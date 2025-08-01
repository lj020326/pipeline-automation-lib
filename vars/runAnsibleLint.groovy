#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.versioning.ComparableSemanticVersion

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            label config.jenkinsNodeLabel
        }
        tools {
           ansible "ansible-venv"
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
                        config.gitBranch = config.get('gitBranch',gitBranch)
                        config.gitCommitId = env.GIT_COMMIT

                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        // ref: https://www.jenkins.io/doc/pipeline/steps/stashNotifier/
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

                        sh("ansible-galaxy collection list")
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
                        sh(lintCmd)

                        try {
                            sh("ansible-lint-junit ${config.testResultsDir}/test-console-results.txt -o ${config.testResultsDir}/${config.testResultsJunitFile}")

                            config.gitRemoteBuildStatus = "SUCCESSFUL"

                            sh("tree ${config.testResultsDir}")

    //                             sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")
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
                        gitRemoteBuildKey: config.buildTestName,
                        gitRemoteBuildName: config.buildTestName,
                        gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                        gitRemoteBuildSummary: 'ansible-datacenter',
                        gitCommitId: config.gitCommitId
                    )

                    List emailAdditionalDistList = []
                    if (config.gitBranch in ['main','QA','PROD'] || config.gitBranch.startsWith("release/")) {
                        if (config?.deployEmailDistList) {
                            emailAdditionalDistList = config.deployEmailDistList
                            log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                            sendEmail(currentBuild, env, emailAdditionalDistList: emailAdditionalDistList)
                        }
                    } else if (config.gitBranch in ['development']) {
                        if (config?.alwaysEmailDistList) {
                            emailAdditionalDistList = config.alwaysEmailDistList
                            log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                            sendEmail(currentBuild, env, emailAdditionalDistList: emailAdditionalDistList)
                        }
                    } else {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result}, 'default')")
                        sendEmail(currentBuild, env)
                    }

                    log.info("Empty current workspace dir")
                    cleanWs()
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

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"ansible")
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')
    config.skipDefaultCheckout = config.get('skipDefaultCheckout', false)
    config.gitRemoteBuildStatus = "INPROGRESS"

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'lee.james.johnson@gmail.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.testResultsDir = config.get('testResultsDir', 'test-results')
    config.testResultsJunitFile = config.get('testResultsJunitFile', 'ansible-lint-junit.xml')

//     config.lintConfigFile = config.get('lintConfigFile', ".ansible-lint")

    config.get("gitRemoteBuildKey", 'Ansible Lint Tests')
	config.get("gitRemoteBuildName", 'Ansible Lint Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
