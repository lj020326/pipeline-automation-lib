#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

def call(Map params=[:]) {

    Logger log = new Logger(this, LogLevel.INFO)
//     log.setLevel(LogLevel.DEBUG)

    Map config = loadPipelineConfig(log, params)
    String ansibleLogSummary = "No results"
    int numTestsFailed=0

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
            stage('Pre-test: Confirm config.ansibleInventoryList is set') {
                when {
                    expression { !config.ansibleInventoryList }
                }
                steps {
                    script {
                        String exceptionMessage = "runAnsibleInventoryTests(): must define config.ansibleInventoryList"
                        log.error("${exceptionMessage}")
                        throw exceptionMessage
                    }
                }
            }
            stage('Pre-test - bitbucketStatusNotify') {
                steps {
                    script {
                        bitbucketStatusNotify(
                            credentialsId: 'ansible-integration-cred'
                        )
                    }
                }
            }
            stage('TEST[01] - Run ansible inventory test') {
                steps {
                    script {
                        numInventoryTestsFailed = runAnsibleInventoryValidation(this, log, config)
                        numTestsFailed += numInventoryTestsFailed
                        if (numInventoryTestsFailed>0) {
                            currentBuild.result = 'FAILURE'
                        } else {
                            currentBuild.result = 'SUCCESS'
                        }
                    }
                }
            }
            stage('TEST[02] - Run yamllint test on inventory') {
                steps {
                    script {
                        sh "${config.yamlLintCmd} --version"
                        dir(config.ansibleInventoryBaseDir) {
                            // run the ansible graph command and grep the stdout/err for the existance of either "WARNING" "ERROR"
                            String yamlLintCmd = "${config.yamlLintCmd} ."

                            int retstat = sh(script: "${yamlLintCmd}", returnStatus: true)
                            log.info("test[02]: retstat=${retstat}")

                            Boolean failed = (retstat>0) ? true : false
                            if (failed) {
                                currentBuild.result = 'FAILURE'
                            } else {
                                currentBuild.result = 'SUCCESS'
                            }

                            log.info("test[02]: failed=${failed}")
                            numTestsFailed += (failed) ? 1 : 0
                        }
                    }
                }
            }
            stage('TEST[03] - Run xenv-compare.sh') {
                steps {
                    script {
                        dir(config.ansibleInventoryBaseDir) {
                            // run the ansible graph command and grep the stdout/err for the existance of either "WARNING" "ERROR"
                            int retstat = sh(script: "bash xenv-compare.sh > /dev/null 2>&1", returnStatus: true)
                            log.info("test[03]: retstat=${retstat}")

                            Boolean failed = (retstat>0) ? true : false
                            if (failed) {
                                sh(script: "bash xenv-compare.sh 2>&1", returnStdout: true)
                                currentBuild.result = 'FAILURE'
                            } else {
                                currentBuild.result = 'SUCCESS'
                            }
                            log.info("test[03]: failed=${failed}")
                            numTestsFailed += (failed) ? 1 : 0
                        }
                    }
                }
            }
            stage("Test Summary") {
                steps {
                    script {
                        currentBuild.result = (numTestsFailed>0) ? "FAILURE" : "SUCCESS"
                    }
                }
            }
            stage('Post-test - bitbucketStatusNotify') {
                steps {
                    script {
                        bitbucketStatusNotify(
                            credentialsId: 'ansible-integration-cred'
                        )
                    }
                }
            }
        }
        post {
            always {
                script {
                    List emailAdditionalDistList = []
                    if (config.alwaysEmailDistList) {
                        emailAdditionalDistList = config.alwaysEmailDistList
                    }
                    if (config.gitBranch in ['origin/main','main']) {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList=alwaysEmailDistList)
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
//     config.gitBranch = env.BRANCH_NAME
    config.gitBranch = env.GIT_BRANCH

//    config.emailDist = config.emailDist ?: "Lee.Johnson.Contractor@alsac.stjude.org"
    config.emailDist = config.get('emailDist',"Lee.Johnson.Contractor@alsac.stjude.org")
    config.alwaysEmailDistList = [
        'matthew.hyclak.contractor@alsac.stjude.org',
        'drew.conner.contractor@alsac.stjude.org',
        'lee.johnson.contractor@alsac.stjude.org'
    ]

    // config.alwaysEmailDist = config.alwaysEmailDist ?: "Lee.Johnson.Contractor@alsac.stjude.org"
    config.emailFrom = config.emailFrom ?: "admin+ansible@alsac.stjude.org"

//     config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    config.ansibleInventoryBaseDir = config.get('ansibleInventoryBaseDir', '.')
    config.ansibleInstallation = config.get('ansibleInstallation', 'ansible-venv')
    config.ansibleInventoryCmd = "ansible-inventory"
    config.ansibleCmd = "ansible"

    config.yamlLintCmd = "yamllint"

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${config}")

    return config
}

int runAnsibleInventoryValidation(def dsl, Logger log, Map config) {
    String logPrefix="runAnsibleInventoryValidation():"
    int numTestsFailed=0

    sh "${config.ansibleInventoryCmd} --version"
    log.info("${logPrefix} config.ansibleInventoryList=${config.ansibleInventoryList}")

    config.ansibleInventoryList.each { String ansibleInventory ->
        dsl.dir(config.ansibleInventoryBaseDir) {

            List ansibleInventoryArgList = []
            ansibleInventoryArgList.push("-i ${ansibleInventory}")
            ansibleInventoryArgList.push("--graph")

            String ansibleInventoryArgs = ansibleInventoryArgList.join(" ")

            // run the ansible graph command and grep the stdout/err for the existence of either "WARNING" "ERROR"
            String ansibleInventoryTestCmd = "${config.ansibleInventoryCmd} ${ansibleInventoryArgs} 2>&1"
            String ansibleInventoryTestCountCmd = "${ansibleInventoryTestCmd} | grep -c -i -e warning -e error || true"
//             int exception_count = sh(script: "${ansibleInventoryTestCountCmd}", returnStdout: true)
            Integer exception_count = sh(script: ansibleInventoryTestCountCmd, returnStdout: true).toInteger()
            log.info("${logPrefix} test[01]: exception_count=${exception_count}")

            Boolean failed = (exception_count>0) ? true : false
            if (failed) {
                dsl.sh "${ansibleInventoryTestCmd} | grep -i -e warning -e error || true"
                dsl.currentBuild.result = 'FAILURE'
            } else {
                dsl.currentBuild.result = 'SUCCESS'
            }

            log.info("${logPrefix} test[01]: failed=${failed}")
            numTestsFailed += (failed) ? 1 : 0
        }
    }
    return numTestsFailed
}