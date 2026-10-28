#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.pipeline.utils.JsonUtils

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)
    Map jobResults = [:]

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.runnerImage
                args config.runnerArgs
                reuseNode true
                // This force-checks the registry for a newer version
                alwaysPull true
            }
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
            stage('Publish Galaxy Collection: Execution') {
				when {
                    expression { fileExists config.galaxyYamlPath }
				}
                stages {
                    stage('Check Skip') {
                        steps {
                            // This plugin is in your plugins.txt and works regardless of the Job DSL UI
                            scmSkip(skipPattern: '.*\\[(ci skip|skip ci)\\].*')
                        }
                    }

                    stage('Extract Collection Metadata') {
                        steps {
                            script {
                                config = loadAnsibleGalaxyConfigs(config)
                            }
                        }
                    }

                    stage('Ansible Lint') {
                        steps {
                            script {
                                runAnsibleLint()
                            }
                        }
                    }

                    stage('Sanity Testing') {
                        when {
                            allOf {
                                expression { config?.configFileSanity }
                                expression { fileExists config.configFileSanity }
                            }
                        }
                        steps {
                            script {
                                jobResults['sanity'] = runAnsibleTestConfig(config, config.configFileSanity)
                            }
                        }
                    }

                    stage('Unit Testing') {
                        when {
                            allOf {
                                expression { config?.configFileUnits }
                                expression { fileExists config.configFileUnits }
                            }
                        }
                        steps {
                            script {
                                jobResults['units'] = runAnsibleTestConfig(config, config.configFileUnits)
                            }
                        }
                    }

                    stage('Integration Testing') {
                        when {
                            allOf {
                                branch 'main'
                                expression { config?.configFileIntegration }
                                expression { fileExists config.configFileIntegration }
                            }
                        }
                        steps {
                            script {
                                jobResults['integration'] = runAnsibleTestConfig(config, config.configFileIntegration)
                            }
                        }
                    }

                    stage('Publish to Pulp') {
                        when { branch 'main' }
                        steps {
                            script {
                                runWithConditionalEnvAndCredentials(
                                    config.pulpEnvVarsList,
                                    config.galaxySecretVarsList
                                ) {
                                    sh '''
                                        ansible-galaxy collection build

                                        # Find the built tarball and publish to your local Pulp registry
                                        TARBALL=$(ls *-*.tar.gz)

                                        ansible-galaxy collection publish "$TARBALL" --server pulp
                                    '''
                                }
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    boolean failed = (jobResults.size()>0) ? jobResults.inject(false) { a, k, v -> a || v.failed } : false
                    log.info("failed=${failed}")
                    jobResults.failed = failed
                    // Save jobResults as YAML report and archive
                    if (config?.jobReport) {
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

                    if (jobResults.failed) {
                        config.gitRemoteBuildStatus = "COMPLETED"
                        config.gitRemoteBuildConclusion = "FAILURE"
                        currentBuild.result = 'FAILURE'
                    } else {
                        config.gitRemoteBuildStatus = "COMPLETED"
                        config.gitRemoteBuildConclusion = "SUCCESS"
                        currentBuild.result = 'SUCCESS'
                    }

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
//     Map config = MapMerge.merge(config, params)
    Map config = params.clone()

    config.get('logLevel', "INFO")
//     config.get('logLevel', "DEBUG")
    config.get('timeout', "5")
    config.get('timeoutUnit', "HOURS")
    config.get('debugPipeline', false)
    config.get('wait', true)
    config.get('failFast', false)
    config.get('propagate', config.failFast)
    config.get('maxRandomDelaySeconds', "10")
    config.get('childJobTimeout', "4")
    config.get('childJobTimeoutUnit', "HOURS")
    config.get("copyChildJobArtifacts", false)
    config.get('testResultsDir', '.test-results')
    config.get('junitTestResultsDir', 'tests/output/junit')
    config.get('testResultsJunitFile', 'ansible-lint-junit.xml')
    config.get('jobReport',"test-results.yml")

    config.get('jenkinsNodeLabel', "docker")
    config.get('runnerRegistry', "media.johnson.int:5000")

//     config.get('runnerImageName', "ansible/ansible-runner")
//     config.get('ansibleVersion', '2.21')
//     config.get('pythonVersion', '3.13')
//     config.runnerImage = getAnsibleRunnerImageId(
//                             runnerImageName: config.runnerImageName,
//                             runnerRegistry: config.runnerRegistry,
//                             ansibleVersion: config.ansibleVersion,
//                             pythonVersion: config.pythonVersion)

    config.get("runnerImage", "media.johnson.int:5000/jenkins-docker-agent:latest")

    List runnerArgsList = []
    runnerArgsList.push("-v /etc/ssl/certs/ca-certificates.crt:/etc/ssl/certs/ca-certificates.crt:ro")
    runnerArgsList.push("-v /var/jenkins_home/git_repo_references:/var/jenkins_home/git_repo_references")
    config.get("runnerArgs", runnerArgsList.join(" "))

//     config.get("gitCredentialsId", "gitea-ssh-jenkins")
    config.get("gitCredentialsId", "infra-jenkins-git-user")

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    config.get("galaxyYamlPath", "galaxy.yml")
    config.get('configFileSanity', ".jenkins/ansible-test-sanity.yml")
    config.get('configFileUnits', ".jenkins/ansible-test-units.yml")

    String pulpUrlDefault = "https://pulp.media.johnson.int/pulp_ansible/galaxy/internal/"
//     String pulpUrlDefault = "https://pulp.admin.johnson.int/pulp_ansible/galaxy/internal/"
    config.get('pulpUrl', pulpUrlDefault)

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List pulpEnvVarsListDefault = [
        "ANSIBLE_GALAXY_SERVER_LIST=pulp",
        "ANSIBLE_GALAXY_SERVER_PULP_URL=${config.pulpUrl}",
    ]
    config.pulpEnvVarsList = config.get('pulpEnvVarsList', pulpEnvVarsListDefault)

    galaxySecretVarsListDefault = [
        usernamePassword(credentialsId: 'pulp-admin-cred', passwordVariable: 'ANSIBLE_GALAXY_SERVER_PULP_TOKEN')
    ]
    config.get('galaxySecretVarsList', galaxySecretVarsListDefault)

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}

Map runAnsibleTestConfig(Map params=[:], String configFile) {
    Map config = params.clone()
    Map jobResults = [:]
    try {
        jobResults = runAnsibleTestManifest(config, configFile)
    } catch (FlowInterruptedException e) {
        currentBuild.result = 'ABORTED'
        throw e
    } catch (Exception e) {
        log.error("runAnsibleTestConfig failed: ${e.getMessage()}")
        currentBuild.result = 'FAILURE'
        throw e
    }
    return jobResults
}