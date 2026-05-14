#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.versioning.ComparableSemanticVersion

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params = [:]) {

    // log.enableDebug()
    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.runnerImage
//                 args '-u root' // Optional: Add custom arguments to the docker run command
//                 args "-v /var/run/docker.sock:/var/run/docker.sock --privileged"
                reuseNode true
                // This force-checks the registry for a newer version
                alwaysPull true
            }
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30'))
            skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
            stage('Pre-test') {
                steps {
                    script {
                        String gitBranch = java.net.URLDecoder.decode(env.GIT_BRANCH, "UTF-8")
                        log.info("gitBranch=${gitBranch}")
                        config.get('gitBranch',gitBranch)
                        config.gitCommitId = env.GIT_COMMIT
                        log.debug("config.gitBranch=${config.gitBranch}")
                        log.debug("config.gitCommitId=${config.gitCommitId}")

                        notifyGitRemoteRepo(
                        	config.gitRemoteRepoType,
                            gitRemoteBuildKey: config.gitRemoteBuildKey,
                            gitRemoteBuildName: config.gitRemoteBuildName,
                            gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                            gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                            gitCommitId: config.gitCommitId
                        )
                    }
                }
            }
            // ref: https://github.com/shipilovds/yaml-lint-to-junit-xml
            // ref: https://pypi.org/project/yaml-lint-to-junit-xml/
            stage('lint test') {
                steps {
                    script {
                        sh "mkdir -p ${config.testResultsDir}"

                        sh("inclusivity --version")

                        List lintCmdList = []
//                         lintCmdList.push("set -o pipefail &&")
//                        lintCmdList.push("set -o pipefail;")
                        lintCmdList.push("inclusivity")
                        if (config.lintConfigFile) {
                            lintCmdList.push("-c ${config.lintConfigFile}")
                        }
                        lintCmdList.push("--exit-1-on-failure")
                        lintCmdList.push(".")
//                         lintCmdList.push("|& tee ${config.testResultsDir}/inclusivity-results.txt")
                        lintCmdList.push("| tee ${config.testResultsDir}/inclusivity-results.txt")
//                         lintCmdList.push("|| true")

                        String lintCmd = lintCmdList.join(' ')

                        try {
                            sh("bash -c 'set -o pipefail && ${lintCmd}'")
                        } catch (Exception e) {
                                log.info("lint failed")
                                config.gitRemoteBuildStatus = "FAILED"
                                currentBuild.result = 'FAILURE'
                                log.error("lint error: " + e.getMessage())
                                throw e
                        }
                        log.info("lint succeeded")
                        currentBuild.result = 'SUCCESS'
                        config.gitRemoteBuildStatus = "SUCCESSFUL"

                        sh("tree ${config.testResultsDir}")

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: "${config.testResultsDir}/**",
                            fingerprint: true)

                    }
                }
            }
        }
        post {
            always {
                script {

                    // ref: https://www.jenkins.io/doc/pipeline/steps/stashNotifier/
                    notifyGitRemoteRepo(
                    	config.gitRemoteRepoType,
                        gitRemoteBuildKey: config.gitRemoteBuildKey,
                        gitRemoteBuildName: config.gitRemoteBuildName,
                        gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                        gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                        gitCommitId: config.gitCommitId
                    )

                    List emailAdditionalDistList = []
                    if (config?.gitBranch &&
                        (config.gitBranch in ['main','QA','PROD'] || config.gitBranch.startsWith("release/"))) {
                        if (config?.deployEmailDistList) {
                            emailAdditionalDistList = config.deployEmailDistList
                            log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                            sendEmail(currentBuild, env, emailAdditionalDistList: emailAdditionalDistList)
                        }
                    } else if (config?.gitBranch && config.gitBranch in ['development']) {
                        if (config?.alwaysEmailDistList) {
                            emailAdditionalDistList = config.alwaysEmailDistList
                            log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                            sendEmail(currentBuild, env, emailAdditionalDistList: emailAdditionalDistList)
                        }
                    } else {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result}, 'default')")
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
//                         sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                        sendEmail(currentBuild, env,
                            emailAdditionalDistList: config.failedEmailList.split(","),
                            emailBody: ansibleLogSummary
                        )
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
//                         sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                        sendEmail(currentBuild, env,
                            emailAdditionalDistList: config.failedEmailList.split(","),
                            emailBody: ansibleLogSummary
                        )
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

//@NonCPS
Map loadPipelineConfig(Map params) {
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

//     config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"ansible")
    config.get('jenkinsNodeLabel',"docker")

    config.get("runnerImage", "media.johnson.int:5000/jenkins-docker-agent:latest")

    config.get('logLevel', "INFO")
    config.get('debugPipeline', false)
    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')
    config.get('skipDefaultCheckout', false)
    config.get('testResultsDir', '.test-results')

    config.gitRemoteBuildStatus = "INPROGRESS"
    config.get("gitRemoteRepoType", "gitea")
    config.get("gitRemoteBuildKey", 'Inclusivity Lint Tests')
	config.get("gitRemoteBuildName", 'Inclusivity Lint Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

//    config.emailDist = config.emailDist ?: "admin@dettonville.com"
    config.get('emailDist',"admin@dettonville.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'admin@dettonville.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.get('lintConfigFile', ".inclusivity.yml")

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
