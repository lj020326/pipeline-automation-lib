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

    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
//             label "docker"
            docker {
                // ref: https://stackoverflow.com/questions/48226183/how-to-mount-jenkins-workspace-in-docker-container-using-jenkins-pipeline#48227560
                image 'checkmarx/kics:latest'
//                        args "--entrypoint='' -v /etc/pki/tls/certs/ca-bundle.crt:/etc/ssl/certs/ca-certificates.crt:ro"
                args "--entrypoint='' -v /etc/ssl/certs/ca-certificates.crt:/etc/ssl/certs/ca-certificates.crt:ro"
                // Run the container on the node specified at the
                // top-level of the Pipeline, in the same workspace,
                // rather than on a new node entirely:
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
                            gitRemoteBuildKey: config.gitRemoteBuildKey,
                            gitRemoteBuildName: config.gitRemoteBuildName,
                            gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                            gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                            gitCommitId: config.gitCommitId
                        )
                    }
                }
            }
            // ref: https://github.com/Checkmarx/kics/blob/master/docs/integrations_jenkins.md
            stage('KICS scan') {
//                 agent {
//                     docker {
//                         // ref: https://stackoverflow.com/questions/48226183/how-to-mount-jenkins-workspace-in-docker-container-using-jenkins-pipeline#48227560
//                         image 'checkmarx/kics:latest'
// //                        args "--entrypoint='' -v /etc/pki/tls/certs/ca-bundle.crt:/etc/ssl/certs/ca-certificates.crt:ro"
//                         args "--entrypoint='' -v /etc/ssl/certs/ca-certificates.crt:/etc/ssl/certs/ca-certificates.crt:ro"
//                         // Run the container on the node specified at the
//                         // top-level of the Pipeline, in the same workspace,
//                         // rather than on a new node entirely:
//                         reuseNode true
//                     }
//                 }
                steps {
                    script {
                        sh "mkdir -p ${config.testResultsDir}"

                        sh("/app/bin/kics version")

                        // ref: https://github.com/Checkmarx/kics/blob/master/docs/commands.md
                        List lintCmdList = []
                        lintCmdList.push("/app/bin/kics scan")
                        lintCmdList.push("--ci")
                        lintCmdList.push("-o ${config.testResultsDir}")
                        lintCmdList.push("--ignore-on-exit results")

                        if (config.testKicsReportFormatsList) {
                            String testReportFormats = config.testKicsReportFormatsList.join(',')
                            lintCmdList.push("--report-formats ${testReportFormats}")
                        } else {
                            lintCmdList.push("--report-formats 'junit,json,html'")
                        }

                        if (config.lintConfigFile) {
                            lintCmdList.push("--config ${config.lintConfigFile}")
                        }

                        if (config.testKicsLintPathsList) {
                            String testLintPaths = config.testKicsLintPathsList.join(',')
                            lintCmdList.push("-p ${testLintPaths}")
                        }

                        if (config.testKicsLintExcludePathsList) {
                            String testLintExcludePaths = config.testKicsLintExcludePathsList.join(',')
                            lintCmdList.push("-e ${testLintExcludePaths}")
                        }

                        String lintCmd = lintCmdList.join(' ')

                        try {
                            sh(lintCmd)
                            config.gitRemoteBuildStatus = "SUCCESSFUL"
                            currentBuild.result = "SUCCESS"
                        } catch (Exception e) {
                            config.gitRemoteBuildStatus = "FAILED"
                            log.error("lint error: " + e.getMessage())
                            currentBuild.result = "FAILED"
//                             throw e
                        }
                        sh("tree ${config.testResultsDir}")

                        String sedCmd = "sed -i 's/<testsuites \\(.*\\) failures=\"0\"><\\/testsuites>/<testsuites \\1><testcase name=\"no linting errors found\"\\/><\\/testsuites>/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                        sh(sedCmd)

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: "${config.testResultsDir}/**",
                            fingerprint: true)

                        junit(testResults: "${config.testResultsDir}/${config.testResultsJunitFile}",
                              skipPublishingChecks: true,
                              allowEmptyResults: true)

                        publishHTML([allowMissing: true,
                                   alwaysLinkToLastBuild: true,
                                   keepAll: true,
                                   reportDir: "${config.testResultsDir}/",
                                   reportFiles: "${config.testResultsDir}/${config.testResultsHtmlFile}",
                                   reportName: 'KICS Results',
                                   reportTitles: ''])
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

    config.get('jenkinsNodeLabel',"ansible")
    config.get('logLevel', "INFO")
    config.get('debugPipeline', false)
    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')
    config.get('skipDefaultCheckout', false)
    config.get('testResultsDir', '.test-results')
    config.get('testResultsJunitFile', 'junit-kics-results.xml')
    config.get('testResultsHtmlFile', 'kics-results.html')

    config.gitRemoteBuildStatus = "INPROGRESS"
    config.get("gitRemoteBuildKey", 'KICS Lint Tests')
	config.get("gitRemoteBuildName", 'KICS Lint Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.get('emailDist',"lee.james.johnson@gmail.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'lee.james.johnson@gmail.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.get('lintConfigFile', '.kics-config.yml')

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
