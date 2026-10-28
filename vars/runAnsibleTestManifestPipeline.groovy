#!/usr/bin/env groovy

/**
 * Loads and executes ansible-test configurations from YAML manifest files
 * located in the repository.
 *
 * This function looks for default manifest files like:
 * - ./.jenkins/ansible-test-sanity.yml
 * - ./.jenkins/ansible-test-units.yml
 * - ./.jenkins/ansible-test-integration.yml
 *
 * Users can override these default paths via the `config` map.
 * Each YAML file should contain parameters directly consumable by the `runAnsibleTest` function.
 *
 * @param config A map to override default manifest file paths:
 * - sanityConfigFile (String, optional): Path to the sanity test YAML config.
 * - unitsConfigFile (String, optional): Path to the units test YAML config.
 * - integrationConfigFile (String, optional): Path to the integration test YAML config.
 */

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
            ansiColor('xterm')
            buildDiscarder(logRotator(numToKeepStr: '20'))
            disableConcurrentBuilds()
            skipDefaultCheckout(false)
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
            timestamps()
        }
        stages {
            stage('Check Skip') {
                steps {
                    // This plugin is in your plugins.txt and works regardless of the Job DSL UI
                    scmSkip(skipPattern: '.*\\[(ci skip|skip ci)\\].*')
                }
            }
            stage('Extract Collection Metadata') {
				when {
                    expression { fileExists config.galaxyYamlPath }
				}
                steps {
                    script {
                        config = loadAnsibleGalaxyConfigs(config)
                    }
                }
            }
            stage("Run Ansible Test Manifest") {
                steps {
                    script {
                        try {
                            jobResults = runAnsibleTestManifest(config, config.configFile)
                        } catch (FlowInterruptedException e) {
                            currentBuild.result = 'ABORTED'
                            throw e
                        } catch (Exception e) {
                            log.error("runAnsibleTestMatrix failed: ${e.getMessage()}")
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

    // Define the order in which to process the test types
    config.get("galaxyYamlPath", "galaxy.yml")
    config.get("ansibleTestCommand", "units")

    config.get("testDeps", [])
    config.get("preTestCmd", "")
    config.get("testResultsDir", "tests/output/junit")
    List junitXmlsPatternsDefault = [
        "**/${config.testResultsDir}/*.xml"
    ]
    config.get('junitXmlsPatterns', junitXmlsPatternsDefault)

    config.get("gitRemoteRepoType", "git")
    config.get("gitRemoteBuildKey", 'ansible-test')
	config.get("gitRemoteBuildName", 'Ansible Test')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")
//     config.get("gitCredentialsId", "jenkins-ansible-ssh")
    config.get("gitCredentialsId", "infra-jenkins-git-user")

    config.get("galaxyYamlPath", "galaxy.yml")

    config.get("runnerRegistry", "media.johnson.int:5000")
    config.get("runnerImageName", "ansible/ansible-test")

    config.get('jobReport',"test-results.yml")

//     config.get('configFile', ".jenkins/ansible-test-integration.yml")
//     config.get('configFile', ".jenkins/ansible-test-sanity.yml")
    config.get('configFile', ".jenkins/ansible-test-units.yml")

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
