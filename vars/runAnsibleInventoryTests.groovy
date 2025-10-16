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
    String ansibleLogSummary = "No results"
    int numTestsFailed = 0
    int exception_count = 0
    String pyTestVersion = "2024.1.1"
    ComparableSemanticVersion minVersionPyTest = new ComparableSemanticVersion(pyTestVersion)
    ComparableSemanticVersion testScriptVersion
    boolean pytest_failed = false
    int pytest_return_code = 0

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
            stage('ansible inventory tests') {
                steps {
                    script {
                        sh "mkdir -p ${config.junitXmlReportDir}"

                        config.testScriptVersion = getTestScriptVersion(this, log, config.testScript)
                        log.info("config.testScriptVersion=${config.testScriptVersion}")

                        testScriptVersion = new ComparableSemanticVersion(config.testScriptVersion)
                        log.info("testScriptVersion=${testScriptVersion.toString()}")
                        log.info("minVersionPyTest=${minVersionPyTest.toString()}")

//                         sh(script: "bash ${config.testScript} -r ${config.junitXmlReport} -p", returnStdout: true)
                        pytest_return_code = sh(
                            script: "bash ${config.testScript} -r ${config.junitXmlReport} -p > /dev/null 2>&1",
                            returnStatus: true)

                        pytest_failed = (pytest_return_code>0)

                        log.info("pytest_return_code=${pytest_return_code}")

                        sh "tree ${config.junitXmlReportDir}"
                        junit(testResults: "${config.junitXmlReportDir}/*.xml",
                            skipPublishingChecks: true,
                            allowEmptyResults: true)

                        List testCmdList = []
//                         testCmdList.push("set -eo pipefail;")
                        testCmdList.push("bash")
                        testCmdList.push("${config.testScript}")
                        String testCmd = testCmdList.join(' ')

//                         testCmdList.push("|& tee ${config.junitXmlReportDir}/inventory-test-results.txt")
                        testCmdList.push("2>&1 | tee ${config.junitXmlReportDir}/inventory-test-results.txt")
                        testCmdList.push("|| true")
                        testCmd = testCmdList.join(' ')

                        log.info("testCmd => ${testCmd}")
                        sh(testCmd)

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: "${config.junitXmlReportDir}/**",
                            fingerprint: true)

//                         if (exception_count>0) {
// //                             sh(script: "bash ${config.testScript} 2>&1", returnStdout: true)
//                             testCmdList.push("|& tee ${config.junitXmlReportDir}/inventory-test-results.txt")
//                             testCmdList.push("|| true")
//                             testCmd = testCmdList.join(' ')
//                             sh(testCmd)
//
//                             archiveArtifacts(
//                                 allowEmptyArchive: true,
//                                 artifacts: "${config.junitXmlReportDir}/inventory-test-results.txt",
//                                 fingerprint: true)
//                         }

//                         numTestsFailed += exception_count
//                         Boolean failed = (exception_count>0) ? true : false
//                         if (failed) {
//                             currentBuild.result = 'FAILURE'
//                             log.error("INVENTORY TEST RESULTS: FAILED")
//                         } else {
//                             currentBuild.result = 'SUCCESS'
//                             log.info("INVENTORY TEST RESULTS: SUCCESS")
//                         }

                    }
                }
            }
            stage("Test Summary") {
                steps {
                    script {
                        log.info("==> ************************************* ")
                        log.info("==> OVERALL INVENTORY TEST RESULTS")
                        log.info("==> TOTAL totalNumFailed=${pytest_return_code}")
                        log.info("==> pytest_failed=${pytest_failed}")

                        if (pytest_failed) {
                          log.error("==> TEST FAILED!")
                        } else {
                          log.info("==> TEST SUCCEEDED!")
                        }

                        currentBuild.result = (pytest_failed) ? "FAILURE" : "SUCCESS"
                        config.gitRemoteBuildStatus = (pytest_failed) ? "FAILED" : "SUCCESSFUL"
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
                    if (config?.alwaysEmailDistList) {
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.alwaysEmailDistList)
                    }
//                     if (config?.gitBranch) &&
//                         (config.gitBranch in ['main','QA','PROD'] || config.gitBranch.startsWith("release/"))
                    if (config?.deployEmailDistList) {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.deployEmailDistList)
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

    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')
    config.skipDefaultCheckout = config.get('skipDefaultCheckout', false)
    config.gitRemoteBuildStatus = "INPROGRESS"
    config.junitXmlReportDir = ".test-results"
    config.junitXmlReport = "${config.junitXmlReportDir}/junit-report.xml"

    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    // config.alwaysEmailDist = config.alwaysEmailDist ?: "lee.james.johnson@gmail.com"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

//     config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    config.ansibleInventoryBaseDir = config.get('ansibleInventoryBaseDir', '.')
    config.ansibleInstallation = config.get('ansibleInstallation', 'ansible-venv')
    config.ansibleInventoryCmd = "ansible-inventory"
    config.ansibleCmd = "ansible"

    config.testScript = "inventory/run-inventory-tests.sh"
    config.testScriptVersion = ""

    config.yamlLintCmd = "yamllint"

    config.get("gitRemoteBuildKey", 'Ansible Inventory Tests')
	config.get("gitRemoteBuildName", 'Ansible Inventory Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}

String getTestScriptVersion(dsl, log, script) {
    String version = dsl.sh(script: "${script} -v", returnStdout: true).trim()
    if (version == "1.0") {
        version = "1.0.0"
    }
    log.debug("version=${version}")
    return version
}
