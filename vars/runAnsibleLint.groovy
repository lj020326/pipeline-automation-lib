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

Map call(Map args=[:]) {

    Map config = args.clone()
    config.get('nocolor', true)
    config.get('ansibleLintConfigFile', '.ansible-lint')
    config.get('testResultsDir', '.test-results')
    config.get('testResultsJunitFile', 'ansible-lint-junit.xml')

//     log.enableDebug()
    log.info("config=${JsonUtils.printToJsonString(config)}")

    sh "ansible-galaxy collection list"
    sh "ansible-lint --version"

    sh "mkdir -p ${config.testResultsDir}"

    List lintCmdList = []
    lintCmdList.push("ansible-lint")
    lintCmdList.push("-v")
    if (config?.nocolor && config.nocolor.toBoolean()) {
        lintCmdList.push("--nocolor")
    }
    if (config?.ansibleLintArgs) {
        lintCmdList.push("${config.ansibleLintArgs}")
    }
    if (config?.ansibleLintConfigFile) {
        lintCmdList.push("-c ${config.ansibleLintConfigFile}")
    } else if (config?.ansibleLintPaths) {
        lintCmdList.push("-c ${config.ansibleLintPaths.join(' ')}")
    }
//     lintCmdList.push("-p")
    lintCmdList.push("--format=pep8")
    lintCmdList.push("| ansible-lint-junit -o ${config.testResultsDir}/ansible-lint-junit.xml")

//     lintCmdList.push("|& tee ${config.testResultsDir}/test-console-results.txt")
//     lintCmdList.push("2>&1 | tee ${config.testResultsDir}/test-console-results.txt")
//     lintCmdList.push("| tee ${config.testResultsDir}/test-console-results.txt")
//     lintCmdList.push("|| true")
    String lintCmd = lintCmdList.join(' ')

    List testEnvList = []
    testEnvList += [
        "ANSIBLE_COLLECTIONS_PATH=~/.ansible/collections:./.ansible/collections:./collections:/usr/share/ansible/collections",
        "ANSIBLE_LINT_OFFLINE=false"
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
        } finally {
//             log.info("Archiving test results from: ${config.testResultsDir}")
//             archiveArtifacts(
//                 artifacts: "${config.testResultsDir}",
//                 fingerprint: true,
//                 onlyIfSuccessful: false
//             )
            sh "tree ${config.testResultsDir}/"
            log.info("Archiving junit test results from path: ${config.testResultsDir}/*.xml")
            junit(testResults: "${config.testResultsDir}/*.xml",
                  skipPublishingChecks: true,
                  allowEmptyResults: true
            )
            log.info("Ansible-lint test results archived.")
        }

    }
    log.info("lint succeeded")
    currentBuild.result = 'SUCCESS'
    config.gitRemoteBuildStatus = "SUCCESSFUL"
    return config
} // body
