#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

def call(Map args=[:]) {

    Map config = loadPipelineConfig(args)
    Map jobResults = [:]
    log.info("config=${JsonUtils.printToJsonString(config)}")

    pipeline {
        agent {
            label "docker"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            skipDefaultCheckout(false)
            disableConcurrentBuilds()
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }
        stages {
			stage('Load pipeline config file') {
				when {
                    expression { fileExists config.configFile }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
					}
				}
			}
            stage("Run Molecule Manifest") {
                steps {
                    script {
                        try {
                            jobResults = runMoleculeConfigs(config)
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            currentBuild.result = 'ABORTED'
                            throw e
                        } catch (Exception e) {
                            log.error("runMoleculeConfigs failed: ${e.getMessage()}")
                            currentBuild.result = 'FAILURE'
                            throw e
                        }

                        log.info("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")

                        if (jobResults.failed) {
                            config.gitRemoteBuildStatus = "COMPLETED"
                            config.gitRemoteBuildConclusion = "FAILURE"
                            currentBuild.result = 'FAILURE'
                        } else {
                            config.gitRemoteBuildStatus = "COMPLETED"
                            config.gitRemoteBuildConclusion = "SUCCESS"
                            currentBuild.result = 'SUCCESS'
                        }
                        // Save jobResults as YAML report and archive
                        if (config.jobReport) {
                            String reportPath = "${config.testResultsDir}/${config.jobReport}"
                            sh "mkdir -p ${config.testResultsDir}"
                            log.info("saving ${config.buildReport}")
                            try {
                                writeYaml file: reportPath, data: jobResults, overwrite: true
                                log.info("Saved jobResults report to ${reportPath}")
                            } catch (Exception err) {
                                log.error("writeYaml(${config.buildReport}): exception occurred [${err}]")
                            }
                            archiveArtifacts(
                                allowEmptyArchive: true,
                                artifacts: "${reportPath}",
                                fingerprint: true)
                            log.info("Archived jobResults report: ${reportPath}")
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    notifyGitRemoteRepo(
                        config.gitRemoteRepoType,
                        gitRemoteBuildKey: config.gitRemoteBuildKey,
                        gitRemoteBuildName: config.gitRemoteBuildName,
                        gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                        gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                        gitRemoteBuildConclusion: config.gitRemoteBuildConclusion,
                        gitCommitId: config.gitCommitId
                    )
                    if (config?.alwaysEmailList) {
                        log.info("config.alwaysEmailList=${config.alwaysEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.alwaysEmailList.split(","))
                    } else {
                        sendEmail(currentBuild, env)
                    }
                    log.info("Empty current workspace dir")
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }
            success {
                script {
                    if (config?.successEmailList) {
                        log.info("config.successEmailList=${config.successEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.successEmailList.split(","))
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            changed {
                script {
                    if (config?.changedEmailList) {
                        log.info("config.changedEmailList=${config.changedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.changedEmailList.split(","))
                    }
                }
            }
        }
    }
} // body

Map loadPipelineConfig(Map params = [:]) {
    log.debug("copy immutable params map to mutable config map")
    log.debug("params=${JsonUtils.printToJsonString(params)}")
    Map config = params.clone()

    config.get('logLevel', "INFO")
    config.get('timeout', "5")
    config.get('timeoutUnit', "HOURS")
    config.get('debugPipeline', false)
    config.get('wait', true)
    config.get('failFast', false)
    config.get('propagate', config.failFast)
    config.get('maxRandomDelaySeconds', "10")
    config.get('childJobTimeout', "4")
    config.get('childJobTimeoutUnit', "HOURS")
    config.get("copyChildJobArtifacts", true)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    config.get("moleculeCommand", "test")
    config.get("moleculeImage", "ubuntu2404-systemd-python")
    config.get("moleculeImageRegistry", "media.johnson.int:5000")
    config.get("moleculeScenario", "bootstrap_linux_package")
    config.get("moleculeDebugFlag", false)

    config.get("preTestCmd", "")
    config.get("testResultsDir", ".test-reports")
    List junitXmlsPatternsDefault = [
        "**/${config.testResultsDir}/*.xml"
    ]
    config.get('junitXmlsPatterns', junitXmlsPatternsDefault)

    config.get("gitRemoteRepoType", "gitea")
    config.get("gitRemoteBuildKey", 'molecule-test')
	config.get("gitRemoteBuildName", 'Molecule Test')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")
//     config.get("gitCredentialsId", "jenkins-ansible-ssh")
    config.get("gitCredentialsId", "infra-jenkins-git-user")

    config.get("runnerRegistry", "media.johnson.int:5000")
    config.get("runnerImageName", "ansible/ansible-runner")

    config.get('jobReport',"test-results.yml")

    config.get('configFile', ".jenkins/molecule-tests.yml")

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}

Map loadPipelineConfigFile(Map baseConfig) {

    Map pipelineConfigMap = readYaml file: baseConfig.configFile
    log.debug("pipelineConfigMap=${JsonUtils.printToJsonString(pipelineConfigMap)}")

//     Map config = baseConfig + pipelineConfigMap
    Map config = MapMerge.merge(baseConfig, pipelineConfigMap)

    log.setLevel(config.logLevel)

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

Map runMoleculeConfigs(Map config) {
    Map collectedResults = [:]
    // To collect results from parallel jobs
    collectedResults.results = [:]

    log.info("Successfully loaded config for '${config.moleculeCommand}'. Running tests...")

    if (config.strategy?.matrix) {
        log.info("Matrix strategy detected for '${config.moleculeCommand}'. Generating parallel stages.")

        Map strategyConfig = [
            script: this,
            strategy: config.strategy,
            stageNamePrefix: "Molecule Test",
            maxRandomDelaySeconds: config.maxRandomDelaySeconds,
            baseConfig: config,
            // Define the executorScript as a closure
            executorScript: { comboConfig ->
                // This closure will be executed for each matrix combination.
                // It needs to return the result of the actual test execution.
                // The 'dir' step should wrap the execution of 'runAnsibleTests'
                // to ensure it runs in the correct collection directory.
                return runMoleculeJob(comboConfig)
            }
        ]

        Map generated = runStrategyMatrix(strategyConfig)
        Map parallelJobs = generated.stages
        collectedResults.results = generated.results

        log.info("Running parallel matrix jobs for '${config.moleculeCommand}'.")
        parallel parallelJobs // Execute the parallel jobs with failFast applied by runMatrixStages
        log.info("Completed parallel matrix jobs for '${config.moleculeCommand}'.")

        // Optionally, process collectedResults here if needed
        log.debug("collectedResults=${JsonUtils.printToJsonString(collectedResults)}")
        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
        boolean failed = (collectedResults.results.size()>0) ? collectedResults.results.inject(false) { a, k, v -> a || v.failed } : false

        log.info("failed=${failed}")

        collectedResults.failed = failed
        if (collectedResults.failed && config.strategy['fail-fast']) {
            log.error("FAST FAIL")
            currentBuild.result = 'FAILURE'
        }
    } else {
        error "Manifest for '${config.moleculeCommand}' does not define a 'strategy.matrix'. The pipeline is configured for matrix execution."
    }

    log.info("Molecule finished")

    log.info("collectedResults=${JsonUtils.printToJsonString(collectedResults)}")
    return collectedResults
}

Map runMoleculeJob(Map config) {
    log.debug("GIT_URL=${env.GIT_URL}")
    log.debug("GIT_BRANCH=${env.GIT_BRANCH}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    Map jobConfigs = [
        jobFolder: "INFRA/repo-test-automation/run-molecule",
        jobParameters: [
            Timeout: config.childJobTimeout ?: "4",
            TimeoutUnit: config.childJobTimeoutUnit ?: "HOURS",
            GitRepoUrl: config.gitRepoUrl ?: env.GIT_URL,
            GitRepoBranch: config.gitRepoBranch ?: env.GIT_BRANCH,
            GitCredentialsId: config.gitCredentialsId,
            RunnerRegistry: config.runnerRegistry,
            RunnerImageName: config.runnerImageName,
            AnsibleVersion: config.ansibleVersion,
            PythonVersion: config.pythonVersion,
            MoleculeImage: config.moleculeImage,
            MoleculeCommand: config.moleculeCommand,
            MoleculeScenario: config.moleculeScenario,
            PreTestCmd: config.preTestCmd,
            TestResultsDir: config.testResultsDir,
        ],
        propagate: config.propagate,
        wait: config.wait,
        failFast: config.failFast,
        logLevel: config.logLevel,
        maxRandomDelaySeconds: config.maxRandomDelaySeconds,
        copyChildJobArtifacts: config.copyChildJobArtifacts,
        junitXmlsPatterns: config.junitXmlsPatterns
    ]

    log.debug("jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    Map jobResult = runJob(jobConfigs)

    log.debug("jobResult=${JsonUtils.printToJsonString(jobResult)}")

    return jobResult
}
