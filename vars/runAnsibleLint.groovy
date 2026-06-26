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
    log.info("config=${JsonUtils.printToJsonString(config)}")

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.runnerImage
                args '--pull=always'
//                 args "--pull=always -e PYTHONPATH=/root/.local/lib/python3.13/site-packages -e PATH=/root/.local/bin:/usr/local/bin:/usr/bin:/bin"
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
                        sh "ansible-galaxy collection list"
                        sh "ansible-lint --version"

                        sh "mkdir -p ${config.testResultsDir}"

                        List lintCmdList = []
                        lintCmdList.push("ansible-lint")
                        lintCmdList.push("-p")
                        lintCmdList.push("--nocolor")
                        if (config?.ansibleLintArgs) {
                            lintCmdList.push("${config.ansibleLintArgs}")
                        }
                        if (config?.ansibleLintConfigFile) {
                            lintCmdList.push("-c ${config.ansibleLintConfigFile}")
                        }
                        if (config?.ansibleLintPaths) {
                            lintCmdList.push("-c ${config.ansibleLintPaths.join(' ')}")
                        }

//                         lintCmdList.push("|& tee ${config.testResultsDir}/test-console-results.txt")
//                         lintCmdList.push("2>&1 | tee ${config.testResultsDir}/test-console-results.txt")
                        lintCmdList.push("| tee ${config.testResultsDir}/test-console-results.txt")
//                         lintCmdList.push("|| true")

                        String lintCmd = lintCmdList.join(' ')

                        List testEnvList = []
                        testEnvList += [
                            "ANSIBLE_COLLECTIONS_PATH=~/.ansible/collections:./.ansible/collections:./collections:/usr/share/ansible/collections",
//                            "ANSIBLE_COLLECTIONS_PATH=/root/.ansible/collections:./.ansible/collections:./collections:/usr/share/ansible/collections",
                            "ANSIBLE_LINT_OFFLINE=true"
                        ]
                        log.info("testEnvList=${JsonUtils.printToJsonString(testEnvList)}")

                        withEnv(testEnvList) {
                            sh("ansible --version")
                            sh("ansible-lint --version")
                            sh("ansible-lint-junit --version")

                            sh "ansible-galaxy collection list"

                            try {
                                //sh(lintCmd)
                                sh("bash -c 'set -o pipefail && ${lintCmd}'")
                            } catch (Exception e) {
                                log.info("lint failed")
                                config.gitRemoteBuildStatus = "FAILED"
                                currentBuild.result = 'FAILURE'
                                log.error("lint error: " + e.getMessage())
                                throw e
                            }
                        }
                        log.info("lint succeeded")
                        currentBuild.result = 'SUCCESS'
                        config.gitRemoteBuildStatus = "SUCCESSFUL"
                    }
                }
            }
        }
        post {
            always {
                script {

                    sh("ansible-lint-junit ${config.testResultsDir}/test-console-results.txt -o ${config.testResultsDir}/${config.testResultsJunitFile}")
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
    config.get('ansibleVersion', '2.19')

//     config.get('pythonVersion', '3.12')
    config.get('pythonVersion', '3.13')

    config.get("runnerRegistry", "media.johnson.int:5000")
    config.get("runnerImageName", "ansible/ansible-runner")

//     config.runnerImage = getAnsibleRunnerImageId(
//                             runnerImageName: config.runnerImageName,
//                             runnerRegistry: config.runnerRegistry,
//                             ansibleVersion: config.ansibleVersion,
//                             pythonVersion: config.pythonVersion)

    config.get("runnerImage", "media.johnson.int:5000/jenkins-docker-agent:latest")

    config.get('logLevel', "INFO")
    config.get('debugPipeline', false)
    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')
    config.get('skipDefaultCheckout', false)
    config.get('testResultsDir', '.test-results')
    config.get('testResultsJunitFile', 'ansible-lint-junit.xml')

    config.gitRemoteBuildStatus = "INPROGRESS"
    config.get("gitRemoteRepoType", "gitea")
    config.get("gitRemoteBuildKey", 'Ansible Lint Tests')
	config.get("gitRemoteBuildName", 'Ansible Lint Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

//    config.emailDist = config.emailDist ?: "admin@dettonville.com"
    config.get('emailDist',"admin@dettonville.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

//     config.ansibleLintConfigFile = config.get('ansibleLintConfigFile', ".ansible-lint")

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
