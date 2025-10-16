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

def call(Map params=[:]) {

    // log.enableDebug()
    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.dockerImage
//                 args '-u root' // Optional: Add custom arguments to the docker run command
//                 args "-v /var/run/docker.sock:/var/run/docker.sock --privileged"
                reuseNode true
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
                            gitRemoteBuildKey: config.buildTestName,
                            gitRemoteBuildName: config.buildTestName,
                            gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                            gitRemoteBuildSummary: 'ansible-datacenter',
                            gitCommitId: config.gitCommitId
                        )
                    }
                }
            }
            // ref: https://medium.com/@alexandru.raul/building-an-efficient-ansible-development-pipeline-using-jenkins-8830a0a19de0
            // ref: https://github.com/wasilak/ansible-lint-junit
            stage('ansible-lint test') {
                steps {
                    script {
                        sh "mkdir -p ${config.testResultsDir}"

                        sh("ansible --version")
                        sh("ansible-lint --version")
                        sh("ansible-lint-junit --version")

                        List lintCmdList = []
//                         lintCmdList.push("set -o pipefail &&")
                        lintCmdList.push("ansible-lint")
                        lintCmdList.push("-p")
                        lintCmdList.push("--nocolor")
                        if (config.lintConfigFile) {
                            lintCmdList.push("-c ${config.lintConfigFile}")
                        }
//                         lintCmdList.push("|& tee ${config.testResultsDir}/test-console-results.txt")
//                         lintCmdList.push("2>&1 | tee ${config.testResultsDir}/test-console-results.txt")
                        lintCmdList.push("| tee ${config.testResultsDir}/test-console-results.txt")
                        lintCmdList.push("|| true")

                        String lintCmd = lintCmdList.join(' ')

                        try {
                            sh(lintCmd)

                            sh("ansible-lint-junit ${config.testResultsDir}/test-console-results.txt -o ${config.testResultsDir}/${config.testResultsJunitFile}")

                            config.gitRemoteBuildStatus = "SUCCESSFUL"

                            sh("tree ${config.testResultsDir}")

//                                 sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")
                            sh("head -20 ${config.testResultsDir}/${config.testResultsJunitFile}")
                            echo "..."
                            sh("tail -20 ${config.testResultsDir}/${config.testResultsJunitFile}")

                            String sedCmd = "sed -i 's/<testsuites>/<testsuites name=\"ansible-lint test\">/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                            sedCmd += "&& sed -i 's/<testsuite errors=.* failures=.* \\(.*\\)\\/>/<testcase name=\"no linting errors found\"\\/>/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                            sedCmd += "&& sed -i 's/<testcase name=\"\\(.*\\)-\\([0-9]\\+\\)\">/<testcase name=\"\\1-\\2\" classname=\"\\1\">/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                            sh(sedCmd)

    //                             sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")
                            sh("head -20 ${config.testResultsDir}/${config.testResultsJunitFile}")
                            echo "..."
                            sh("tail -20 ${config.testResultsDir}/${config.testResultsJunitFile}")

                            archiveArtifacts(
                                allowEmptyArchive: true,
                                artifacts: "${config.testResultsDir}/**",
                                fingerprint: true)

                            junit(testResults: "${config.testResultsDir}/${config.testResultsJunitFile}",
                                  skipPublishingChecks: true,
                                  allowEmptyResults: true)
                        } catch (Exception e) {
                            config.gitRemoteBuildStatus = "FAILED"
                            log.error("lint error: " + e.getMessage())
//                             throw e
                        }
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
//     config.get('ansibleVersion', '2.18')
//     config.get('pythonVersion', '3.12')
    config.get('ansibleVersion', '2.19')
    config.get('pythonVersion', '3.13')

    config.get("dockerRegistry", "media.johnson.int:5000")
    config.get("dockerImageName", "ansible/ansible-runner")

    config.dockerImage = getAnsibleDockerImageId(
                            dockerImageName: config.dockerImageName,
                            ansibleVersion: config.ansibleVersion,
                            pythonVersion: config.pythonVersion,
                            dockerRegistry: config.dockerRegistry)

    config.get('logLevel', "INFO")
    config.get('debugPipeline', false)
    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')
    config.get('skipDefaultCheckout', false)
    config.get('testResultsDir', '.test-results')
    config.get('testResultsJunitFile', 'ansible-lint-junit.xml')

    config.gitRemoteBuildStatus = "INPROGRESS"
    config.get("gitRemoteBuildKey", 'Ansible Lint Tests')
	config.get("gitRemoteBuildName", 'Ansible Lint Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.get('emailDist',"lee.james.johnson@gmail.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'lee.james.johnson@gmail.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

//     config.lintConfigFile = config.get('lintConfigFile', ".ansible-lint")

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
