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
    log.info("config=${JsonUtils.printToJsonString(config)}")

    pipeline {
        agent {
            label "docker"
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '20'))
            skipDefaultCheckout(false)
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }
        stages {
            stage('Load Collection Galaxy config') {
                steps {
                    script {
                        config = loadCollectionGalaxyConfig(config)
                    }
                }
            }
            stage('Setup Ansible Test Environment') {
                steps {
                    script {
                        setupCollectionTestEnv(config)
                    }
                }
            }
            stage("Run Ansible Test Manifest") {
                steps {
                    script {
                        Map jobResults = [:]
                        try {
                            jobResults = runAnsibleTestConfigs(config)
                        } catch (hudson.AbortException ae) {
                            // handle an AbortException
                            // ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
                            // ref: https://gist.github.com/stephansnyt/3ad161eaa6185849872c3c9fce43ca81
                            if (manager.build.getAction(InterruptedBuildAction.class) ||
                                // this ambiguous condition means a user _probably_ aborted, not sure if this one is really necessary
                                (ae instanceof FlowInterruptedException && ae.causes.size() == 0)) {
                                config.gitRemoteBuildConclusion = "ABORTED"
                                throw ae
                            } else {
                                ansibleLogSummary = "ansible.execPlaybook error: " + ae.getMessage()
                                log.error("ansible.execPlaybook error: " + ae.getMessage())
                            }
                            config.gitRemoteBuildStatus = "FAILED"
                            config.gitRemoteBuildConclusion = "FAILURE"
                            currentBuild.result = 'FAILURE'
                        }

                        log.info("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
                        dir(config.testResultsDir) {
                            log.info("dir: ${config.testResultsDir} successfully created!")
                        }
                        log.info("saving ${config.jobReport}")
                        try {
                            // ref: https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#writeyaml-write-a-yaml-from-an-object-or-objects
                            writeYaml file: config.jobReport, data: jobResults
                        } catch (Exception err) {
                            log.error("writeYaml(${config.jobReport}): exception occurred [${err}]")
                        }

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: config.jobReport,
                            fingerprint: true)

                        if (jobResults.failed) {
                            config.gitRemoteBuildStatus = "FAILED"
                            config.gitRemoteBuildConclusion = "FAILURE"
                            currentBuild.result = 'FAILURE'
                        } else {
                            config.gitRemoteBuildStatus = "SUCCESS"
                            config.gitRemoteBuildConclusion = "SUCCESS"
                            currentBuild.result = 'SUCCESS'
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
                    if (!config.debugPipeline) {
                        log.info("Empty current workspace dir")
                        try {
                            cleanWs()
                        } catch (Exception ex) {
                            log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                        }
                    } else {
                        log.info("Skipping cleanup of current workspace directory since config.debugPipeline == true")
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

Map loadPipelineConfig(Map params) {
    Map config = [:]

    if (params) {
        log.debug("copy immutable params map to mutable config map")
        log.debug("params=${JsonUtils.printToJsonString(params)}")
        config = MapMerge.merge(config, params)
    }

    config.get('logLevel', "INFO")
    config.get('timeout', "5")
    config.get('timeoutUnit', "HOURS")
    config.get('wait', true)

    log.setLevel(config.logLevel)
    log.debug("log.level=${log.level}")

    // Define the default paths for the test configuration YAML files
    // These paths are relative to the Jenkins workspace (repository root).
    def defaultManifestPaths = [
        sanity: './.jenkins/ansible-test-sanity.yml',
        units: './.jenkins/ansible-test-units.yml',
        integration: './.jenkins/ansible-test-integration.yml'
    ]

    // Allow overriding default paths via the 'config' map provided by the user
    def manifestPaths = [
        sanity: config.sanityConfigFile ?: defaultManifestPaths.sanity,
        units: config.unitsConfigFile ?: defaultManifestPaths.units,
        integration: config.integrationConfigFile ?: defaultManifestPaths.integration
    ]

    config.manifestPaths = manifestPaths

    // Define the order in which to process the test types
    def testTypesToProcess = ['sanity', 'units', 'integration']
    config.get("testTypesToProcess", testTypesToProcess)

    config.get("gitRemoteRepoType", "gitea")
    config.get("gitRemoteBuildKey", 'ansible-test')
	config.get("gitRemoteBuildName", 'Ansible Test')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

    config.get("galaxyYamlPath", "galaxy.yml")
    config.get('testResultsDir', 'tests/output/junit')
    config.get('testResultsJunitFile', 'ansible-test-sanity.xml')

    config.get('jobReport',"${config.testResultsDir}/test-results.yml")

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}

def runAnsibleTestConfigs(Map config) {
    for (String testType : config.testTypesToProcess) {
        String manifestPath = config.manifestPaths."${testType}"

        config.get("gitRemoteBuildKey", "ansible-test ${testType}")
        config.get("gitRemoteBuildName", "Ansible Test ${testType}")

        if (fileExists(manifestPath)) {
            log.info("Attempting to load configuration for '${testType}' from: '${manifestPath}'")
            Map testConfig = readYaml(file: manifestPath)
            log.debug("testConfig=${JsonUtils.printToJsonString(testConfig)}")

            testConfig.testingType = testType
            log.info("Successfully loaded config for '${testType}'. Running tests...")

            if (testConfig.strategy?.matrix?.versions) {
                log.info("Matrix strategy detected for '${testType}'. Generating parallel stages.")

                Map matrixDriverConfig = [
                    script: this,
                    matrix: testConfig.strategy,
                    maxRandomDelaySeconds: 10,
                    baseConfig: config + testConfig, // Merged config
                    // Define the executorScript as a closure
                    executorScript: { comboConfig ->
                        // This closure will be executed for each matrix combination.
                        // It needs to return the result of the actual test execution.
                        // The 'dir' step should wrap the execution of 'runAnsibleTests'
                        // to ensure it runs in the correct collection directory.
                        return runAnsibleTest(comboConfig)
                    }
                ]

                // Call the runMatrixStages global variable
                def generated = runMatrixStages(matrixDriverConfig)
                def parallelJobs = generated.stages
                def collectedResults = generated.results

                log.info("Running parallel matrix jobs for '${testType}'.")
                parallel parallelJobs // Execute the parallel jobs with failFast applied by runMatrixStages
                log.info("Completed parallel matrix jobs for '${testType}'.")

                // Optionally, process collectedResults here if needed
                log.info("collectedResults=${JsonUtils.printToJsonString(collectedResults)}")

                // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
                // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
                boolean failed = (collectedResults.size()>0) ? collectedResults.inject(false) { a, k, v -> a || v.failed } : false

                collectedResults.failed = failed
                if (collectedResults.failed && testConfig.strategy['fail-fast']) {
                    log.error("FAST FAIL")
                    currentBuild.result = 'FAILURE'
                }
                return collectedResults
            } else {
                error "Manifest for '${testType}' does not define a 'strategy.matrix'. The pipeline is configured for matrix execution."
            }

        } else {
            log.info("No configuration file found for '${testType}' at: '${manifestPath}'. Skipping this test type.")
        }
    }
    log.info("All Ansible Tests from Manifest Files Processed")
}
