#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.versioning.ComparableSemanticVersion

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            label "ansible"
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
                        config.gitBranch = config.get('gitBranch',"${gitBranch}")
                        config.gitCommitHash = env.GIT_COMMIT

                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        // ref: https://www.jenkins.io/doc/pipeline/steps/stashNotifier/
                        bitbucketStatusNotify(
                                buildKey: config.buildTestName,
                                buildName: config.buildTestName,
                                buildState: 'INPROGRESS',
                                repoSlug: 'ansible-datacenter',
                                commitId: config.gitCommitHash
                            )
                    }
                }
            }
            // ref: https://github.com/shipilovds/yaml-lint-to-junit-xml
            // ref: https://pypi.org/project/yaml-lint-to-junit-xml/
            stage('yamllint test') {
                steps {
                    script {
                        sh "mkdir -p ${config.testResultsDir}"

                        sh("yamllint --version")

                        List lintCmdList = []
//                         lintCmdList.push("set -o pipefail &&")
//                        lintCmdList.push("set -o pipefail;")
                        lintCmdList.push("yamllint")
                        lintCmdList.push("--no-warnings")
                        lintCmdList.push("-f parsable")
                        if (config.lintConfigFile) {
                            lintCmdList.push("-c ${config.lintConfigFile}")
                        }
                        lintCmdList.push(".")
//                         lintCmdList.push("|& tee ${config.testResultsDir}/yamllint-results.txt")
                        lintCmdList.push("| tee ${config.testResultsDir}/yamllint-results.txt")
                        lintCmdList.push("|| true")

                        String lintCmd = lintCmdList.join(' ')

                        try {
                            sh(lintCmd)

                            config.bitbucketResult = "SUCCESSFUL"

                            sh("yaml-lint-to-junit-xml ${config.testResultsDir}/yamllint-results.txt > ${config.testResultsDir}/${config.testResultsJunitFile}")

    //                         sh("tree ${config.testResultsDir}")
    //
    //                         sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")

                            archiveArtifacts(
                                allowEmptyArchive: true,
                                artifacts: "${config.testResultsDir}/**",
                                fingerprint: true)

                            junit(testResults: "${config.testResultsDir}/${config.testResultsJunitFile}",
                                  skipPublishingChecks: true,
                                  allowEmptyResults: true)
                        } catch (Exception e) {
                            config.bitbucketResult = "FAILED"
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
                    bitbucketStatusNotify(
                        buildKey: config.buildTestName,
                        buildName: config.buildTestName,
                        buildState: config.bitbucketResult,
                        repoSlug: 'ansible-datacenter',
                        commitId: config.gitCommitHash
                    )

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

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'lee.james.johnson@gmail.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.testResultsDir = config.get('testResultsDir', 'test-results')
    config.testResultsJunitFile = config.get('testResultsJunitFile', 'yaml-lint-junit.xml')

//     config.lintConfigFile = config.get('lintConfigFile', ".yamllint.yml")

    config.buildTestName = config.get('buildTestName', 'YAML Lint Tests')

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
