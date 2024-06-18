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
            // ref: https://github.com/Checkmarx/kics/blob/master/docs/integrations_jenkins.md
            stage('KICS scan') {
                agent {
                    docker {
                        // ref: https://stackoverflow.com/questions/48226183/how-to-mount-jenkins-workspace-in-docker-container-using-jenkins-pipeline#48227560
                        image 'checkmarx/kics:latest'
                        args "--entrypoint='' -v /etc/pki/tls/certs/ca-bundle.crt:/etc/ssl/certs/ca-certificates.crt:ro"
                        // Run the container on the node specified at the
                        // top-level of the Pipeline, in the same workspace,
                        // rather than on a new node entirely:
                        reuseNode true
                    }
                }
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
                        sh(lintCmd)

                        sh("tree ${config.testResultsDir}")

                        String sedCmd = "sed -i 's/<testsuites \\(.*\\) failures=\"0\"><\\/testsuites>/<testsuites \\1><testcase name=\"no linting errors found\"\\/><\\/testsuites>/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                        sh(sedCmd)

                        junit(testResults: "${config.testResultsDir}/${config.testResultsJunitFile}",
                                skipPublishingChecks: true,
                                allowEmptyResults: true)

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: "${config.testResultsDir}/**",
                            fingerprint: true)

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
    config.testResultsJunitFile = config.get('testResultsJunitFile', 'junit-kics-results.xml')
    config.testResultsHtmlFile = config.get('testResultsHtmlFile', 'kics-results.html')

    config.lintConfigFile = config.get('lintConfigFile', '.kics-config.yml')

    config.buildTestName = config.get('buildTestName', 'KICS Lint Tests')

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}
