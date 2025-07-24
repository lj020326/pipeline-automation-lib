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
                        config.gitCommitHash = env.GIT_COMMIT

                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        // Gitea status notification using Checks API
                        publishChecks(
                            name: config.buildTestName,
                            title: config.buildTestName,
                            status: config.buildStatus,  // 'IN_PROGRESS' or 'COMPLETED'
                            conclusion: config.repoConclusionResult,  // 'SUCCESS', 'FAILURE', 'NEUTRAL'
                            summary: "Build ${config.buildTestName} started"
                        )

//                         // ref: https://www.jenkins.io/doc/pipeline/steps/stashNotifier/
//                         bitbucketStatusNotify(
//                             buildKey: config.buildTestName,
//                             buildName: config.buildTestName,
//                             buildState: config.buildStatus,
//                             repoSlug: 'ansible-datacenter',
//                             commitId: config.gitCommitHash
//                         )
                    }
                }
            }
            // ref: https://medium.com/@alexandru.raul/building-an-efficient-ansible-development-pipeline-using-jenkins-8830a0a19de0
            stage('ansible-test sanity') {
                steps {
                    script {
                        sh "mkdir -p ${config.testResultsDir}"

                        sh("ansible-galaxy collection list")
                        sh("ansible --version")
                        sh("ansible-test --version")

                        List testCmdList = []
//                         testCmdList.push("set -o pipefail &&")
                        testCmdList.push("ansible-test sanity")
                        if (config.pythonVersion) {
                            testCmdList.push("--python ${config.pythonVersion}")
                        }
//                         testCmdList.push("|& tee ${config.testResultsDir}/test-console-results.txt")
//                         testCmdList.push("2>&1 | tee ${config.testResultsDir}/test-console-results.txt")
                        testCmdList.push("| tee ${config.testResultsDir}/test-console-results.txt")
//                         testCmdList.push("|| true")

                        try {
                            String lintCmd = testCmdList.join(' ')
                            sh(lintCmd)
                            config.repoConclusionResult = "SUCCESS"

                        } catch (Exception e) {
                            config.repoConclusionResult = "FAILURE"
                            log.error("test error: " + e.getMessage())
//                             throw e
                        }

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

                    // Gitea status notification using Checks API
                    publishChecks(
                        name: config.buildTestName,
                        title: config.buildTestName,
                        status: 'COMPLETED', // 'IN_PROGRESS' or 'COMPLETED'
                        conclusion: config.repoConclusionResult, // 'SUCCESS', 'FAILURE', 'NEUTRAL'
                        summary: "Build ${config.buildTestName} started"
                    )

//                     // ref: https://www.jenkins.io/doc/pipeline/steps/stashNotifier/
//                     bitbucketStatusNotify(
//                         buildKey: config.buildTestName,
//                         buildName: config.buildTestName,
//                         buildState: config.buildStatus,
//                         repoSlug: 'ansible-datacenter',
//                         commitId: config.gitCommitHash
//                     )

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
    config.buildStatus = "IN_PROGRESS"
    config.repoConclusionResult = "NEUTRAL"

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'lee.james.johnson@gmail.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.testResultsDir = config.get('testResultsDir', 'test-results')
    config.testResultsJunitFile = config.get('testResultsJunitFile', 'ansible-sanity-junit.xml')

    config.buildTestName = config.get('buildTestName', 'Ansible Test Sanity')

    config.pythonVersion = config.get('pythonVersion', '3.12')

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
