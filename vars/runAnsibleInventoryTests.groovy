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
    String ansibleLogSummary = "No results"
    int numTestsFailed = 0
    int exception_count = 0
    String pyTestVersion = "2024.1.1"
    ComparableSemanticVersion minVersionPyTest = new ComparableSemanticVersion(pyTestVersion)
    boolean pytest_failed = false

    pipeline {
        agent {
            label config.jenkinsNodeLabel
        }
        tools {
            // ref: https://webapp.chatgpt4google.com/s/MzY5MjYw
            // ref: https://stackoverflow.com/questions/47895668/how-to-select-multiple-jdk-version-in-declarative-pipeline-jenkins#48368506
            ansible "${config.ansibleInstallation}"
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
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

                        bitbucketStatusNotify(
                            credentialsId: 'ansible-integration-cred'
                            commitSha1: config.gitCommitHash
                        )
                    }
                }
            }
            stage('Run ansible inventory tests') {
                steps {
                    script {
                        config.testScriptVersion = getTestScriptVersion(this, log, config.testScript)
                        log.info("config.testScriptVersion=${config.testScriptVersion}")

                        ComparableSemanticVersion testScriptVersion = new ComparableSemanticVersion(config.testScriptVersion)
                        log.info("testScriptVersion=${testScriptVersion.toString()}")
                        log.info("minVersionPyTest=${minVersionPyTest.toString()}")

                        if (testScriptVersion >= minVersionPyTest) {
//                             sh(script: "bash ${config.testScript} -r ${config.junitXmlReport} -p", returnStdout: true)
                            pytest_failed = sh(
                                script: "bash ${config.testScript} -r ${config.junitXmlReport} -p  > /dev/null 2>&1",
                                returnStatus: true) != 0
                        }

                        exception_count = sh(script: "bash ${config.testScript}> /dev/null 2>&1", returnStatus: true)
                        log.info("INVENTORY TEST exception_count=${exception_count}")

                        if (exception_count>0) {
                            sh(script: "bash ${config.testScript} 2>&1", returnStdout: true)
                        }

                        numTestsFailed += exception_count
                        Boolean failed = (exception_count>0) ? true : false
                        if (failed) {
                            currentBuild.result = 'FAILURE'
                            log.error("INVENTORY TEST RESULTS: FAILED")
                        } else {
                            currentBuild.result = 'SUCCESS'
                            log.info("INVENTORY TEST RESULTS: SUCCESS")
                        }
                    }
                }
            }
            stage("Test Summary") {
                steps {
                    script {
                        log.info("==> ************************************* ")
                        log.info("==> OVERALL INVENTORY TEST RESULTS")
                        log.info("==> TOTAL totalNumFailed=${numTestsFailed}")
                        if (numTestsFailed==0) {
                          log.info("==> TEST SUCCEEDED!")
                        } else {
                          log.info("==> TEST FAILED!")
                        }
                        currentBuild.result = (numTestsFailed>0) ? "FAILURE" : "SUCCESS"
                    }
                }
            }
        }
        post {
            always {
                script {
                    ComparableSemanticVersion testScriptVersion = new ComparableSemanticVersion(config.testScriptVersion)
                    if (testScriptVersion >= minVersionPyTest) {
    //                     junit testResults: ".test-results/*.xml", skipPublishingChecks: true
                        junit testResults: "${config.junitXmlReportDir}/*.xml", skipPublishingChecks: true
                    }

                    bitbucketStatusNotify(
                        credentialsId: 'ansible-integration-cred'
                        commitSha1: config.gitCommitHash
                    )
                    List emailAdditionalDistList = []
                    if (config.alwaysEmailDistList) {
                        emailAdditionalDistList = config.alwaysEmailDistList
                    }
                    if (config.gitBranch in ['origin/main','main']) {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList=emailAdditionalDistList)
                    } else {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result}, 'RequesterRecipientProvider')")
                        sendEmail(currentBuild, env, [[$class: 'RequesterRecipientProvider']])
                    }
                    log.info("Empty current workspace dir")
//                     cleanWs()
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
    config.junitXmlReportDir = ".test-results"
    config.junitXmlReport = "${config.junitXmlReportDir}/junit-report.xml"

    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    config.alwaysEmailDistList = [
        'lee.james.johnson@gmail.com'
    ]

    // config.alwaysEmailDist = config.alwaysEmailDist ?: "Lee.Johnson.Contractor@alsac.stjude.org"
    config.emailFrom = config.emailFrom ?: "admin+ansible@alsac.stjude.org"

//     config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    config.ansibleInventoryBaseDir = config.get('ansibleInventoryBaseDir', '.')
    config.ansibleInstallation = config.get('ansibleInstallation', 'ansible-venv')
    config.ansibleInventoryCmd = "ansible-inventory"
    config.ansibleCmd = "ansible"

    config.testScript = "inventory/run-inventory-tests.sh"
    config.testScriptVersion = ""

    config.yamlLintCmd = "yamllint"

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

String getTestScriptVersion(dsl, log, script) {
    String logPrefix = "getTestScriptVersion():"
    String version = dsl.sh(script: "${script} -v", returnStdout: true).trim()
    if (version == "1.0") {
        version = "1.0.0"
    }
    log.debug("${logPrefix} version=${version}")
    return version
}