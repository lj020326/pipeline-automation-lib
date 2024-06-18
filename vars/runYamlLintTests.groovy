#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.versioning.ComparableSemanticVersion

def call(Map params=[:]) {

    Logger log = new Logger(this, LogLevel.INFO)
//     log.setLevel(LogLevel.DEBUG)

    Map config = loadPipelineConfig(log, params)

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
                        lintCmdList.push("set -o pipefail &&")
                        lintCmdList.push("yamllint")
                        lintCmdList.push("--no-warnings")
                        lintCmdList.push("-f parsable")
                        if (config.lintConfigFile) {
                            lintCmdList.push("-c ${config.lintConfigFile}")
                        }
                        lintCmdList.push(".")
                        lintCmdList.push("|& tee ${config.testResultsDir}/yamllint-results.txt")
                        lintCmdList.push("|| true")

                        String lintCmd = lintCmdList.join(' ')
                        sh(lintCmd)

                        sh("yaml-lint-to-junit-xml ${config.testResultsDir}/yamllint-results.txt > ${config.testResultsDir}/${config.testResultsJunitFile}")

//                         sh("tree ${config.testResultsDir}")
//
//                         sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")

                        junit(testResults: "${config.testResultsDir}/${config.testResultsJunitFile}",
                              skipPublishingChecks: true,
                              allowEmptyResults: true)

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
                    bitbucketStatusNotify(
                                buildKey: config.buildTestName,
                                buildName: config.buildTestName,
                                repoSlug: 'ansible-datacenter',
                                commitId: config.gitCommitHash
                            )

                    List emailAdditionalDistList = []
                    if (config.alwaysEmailDistList) {
                        emailAdditionalDistList = config.alwaysEmailDistList
                    }
                    if (config.gitBranch in ['main','development'] || config.gitBranch.startsWith("release/")) {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList=emailAdditionalDistList)
                    } else {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result}, 'RequesterRecipientProvider')")
                        sendEmail(currentBuild, env, [[$class: 'RequesterRecipientProvider']])
                    }
                    log.info("Empty current workspace dir")
                    cleanWs()
                }
            }
        }
    }

} // body

//@NonCPS
Map loadPipelineConfig(Logger log, Map params) {
    String logPrefix="loadPipelineConfig():"
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("${logPrefix} params[${key}]=${value}")
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
    config.alwaysEmailDistList = ["ljohnson@dettonville.org"]

    // config.alwaysEmailDist = config.alwaysEmailDist ?: "lee.james.johnson@gmail.com"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.testResultsDir = config.get('testResultsDir', 'test-results')
    config.testResultsJunitFile = config.get('testResultsJunitFile', 'yaml-lint-junit.xml')

//     config.lintConfigFile = config.get('lintConfigFile', ".yamllint.yml")

    config.buildTestName = config.get('buildTestName', 'YAML Lint Tests')

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}
