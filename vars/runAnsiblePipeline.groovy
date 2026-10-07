#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-to-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

//     log.enableDebug()
    Map config = loadAnsibleConfigs(params)
    Map results = [:]
    results.gitRemoteBuildStatus = 'INPROGRESS'
    String ansibleLogSummary = "No results"
    log.info("Running ansible inside docker container: ${config.runnerImage}")

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
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
			stage('Load pipeline config file') {
				when {
                    allOf {
                        expression { config.ansiblePipelineConfigFile }
                        expression { fileExists config.ansiblePipelineConfigFile }
                    }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
                        log.info("Merged config=${JsonUtils.printToJsonString(config)}")
					}
				}
			}
            stage('Prepare Ansible Environment') {
                when {
                    allOf {
                        expression { config.ansibleGalaxyInstallOpt }
                        expression { config.ansibleCollectionsRequirements || config.ansibleRolesRequirements }
                    }
                }
                steps {
                    script {
//                         String gitBranch = java.net.URLDecoder.decode(env.BRANCH_NAME, "UTF-8")
                        String gitBranch = java.net.URLDecoder.decode(env.GIT_BRANCH, "UTF-8")
                        log.info("gitBranch=${gitBranch}")
                        config.get('gitBranch',gitBranch)
                        config.gitCommitId = env.GIT_COMMIT
                        log.debug("config.gitBranch=${config.gitBranch}")
                        log.debug("config.gitCommitId=${config.gitCommitId}")

                        installAnsibleGalaxyDeps(config)
                    }
                }
            }
            stage('Run Ansible Playbook') {
                steps {
                    script {
                        results = runAnsiblePlaybook(config)
                        if (fileExists("ansible.log")) {
                            archiveArtifacts(
                                allowEmptyArchive: true,
                                artifacts: "ansible.log",
                                fingerprint: true)

                            ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (!config.initializeParamsOnly) {
                        if (!config.debugPipeline) {
                            cleanWorkspace()
                        }
                        sendEmailNotification(config, "always")
                    }
                }
            }
            changed {
                script {
                    if (!config.initializeParamsOnly) {
                        sendEmailNotification(config, "changed")
                    }
                }
            }
            success {
                script {
                    if (!config.initializeParamsOnly) {
                        sendEmailNotification(config, "success")
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env,
                            emailAdditionalDistList: config.failedEmailList.split(","),
                            emailBody: ansibleLogSummary
                        )
                    }
                }
            }
//             aborted {
//                 script {
//                     if (config?.failedEmailList) {
//                         log.info("config.failedEmailList=${config.failedEmailList}")
//                         sendEmail(currentBuild, env,
//                             emailAdditionalDistList: config.failedEmailList.split(","),
//                             emailBody: ansibleLogSummary
//                         )
//                     }
//                 }
//             }
        }
    }
} // body

Map loadPipelineConfigFile(Map config) {

    Map ansiblePipelineConfigMap = readYaml file: config.ansiblePipelineConfigFile
    log.debug("ansiblePipelineConfigMap=${JsonUtils.printToJsonString(ansiblePipelineConfigMap)}")

    ansibleRootConfigMap=ansiblePipelineConfigMap.findAll { !['repoList'].contains(it.key) }

    log.debug("config.ansibleRootConfigMap=${ansibleRootConfigMap}")
    config = MapMerge.merge(config, ansibleRootConfigMap)

    if (!config.ansiblePlaybookRepo) {
        return config
    }
    log.debug("config.ansiblePlaybookRepo=${config.ansiblePlaybookRepo}")

    if (!ansiblePipelineConfigMap.repoList) {
        return config
    }

    if (ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]) {
        Map ansibleRepoConfigMap = ansiblePipelineConfigMap.repoList[config.ansiblePlaybookRepo]
        log.debug("ansibleRepoConfigMap=${JsonUtils.printToJsonString(ansibleRepoConfigMap)}")

        ansibleRepoConfigMap=ansibleRepoConfigMap.findAll { !['SANDBOX','DEV','QA','PROD'].contains(it.key) }

        if (ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]["${config.environment}"]) {
            Map ansibleEnvConfigMap = ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]["${config.environment}"]
            log.debug("ansibleEnvConfigMap=${JsonUtils.printToJsonString(ansibleEnvConfigMap)}")

            log.debug("Merge ansibleEnvConfigMap to ansibleRepoConfigMap")
            ansibleRepoConfigMap = MapMerge.merge(ansibleRepoConfigMap, ansibleEnvConfigMap)
        }
        log.debug("ansibleRepoConfigMap=${JsonUtils.printToJsonString(ansibleRepoConfigMap)}")

        log.debug("Merge ansibleRepoConfigMap to config map")
        config = MapMerge.merge(config, ansibleRepoConfigMap)
    }
//     if (config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir
//         && !config.ansibleCollectionsRequirements.startsWith(config.ansiblePlaybookDir))
    if (config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config?.ansiblePlaybookDir) {
        log.debug("Prepend ansiblePlaybookDir to config.ansibleCollectionsRequirements")
        config.ansibleCollectionsRequirements = "${config.ansiblePlaybookDir}/${config.ansibleCollectionsRequirements}"
        log.debug("Modified config.ansibleCollectionsRequirements=${config.ansibleCollectionsRequirements}")
    }

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

void cleanWorkspace() {
    log.info("Cleaning execution environment workspace paths...")
    try {
        cleanWs deleteDirs: true, notFailBuild: true
    } catch (Exception ex) {
        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
    }
}
