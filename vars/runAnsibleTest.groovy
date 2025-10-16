#!/usr/bin/env groovy

/**
 * Runs 'ansible-test' with specified testing type, *a single* Ansible core, and *a single* Python version
 *
 * This function provides similar capabilities to the 'ansible-community/ansible-test-gh-action'.
 * https://github.com/ansible-community/ansible-test-gh-action/blob/main/action.yml
 *
 * @param config A map containing configuration options:
 * - ansibleVersion (String, REQUIRED): A single Ansible core version to test against.
 * - pythonVersion (String, REQUIRED): A single Python version to test under.
 * - ansibleTestCommand (String, optional): The type of ansible-test to run (e.g., 'sanity', 'units', 'integration').
 * Defaults to 'sanity'.
 * - testDeps (String or List<String>, optional): A collection name or list of collection names.
 * - preTestCmd (String, optional): A shell command to execute before 'ansible-test' runs.
 */

import com.dettonville.pipeline.utils.JsonUtils

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

// Core logic for running a single ansible-test
// This function needs to be callable from outside its original 'call' context.
// Making it a direct global variable or a separate function within the same file.
// For simplicity, we'll keep it within this file and ensure 'call' sets up and invokes it.
Map call(Map params = [:]) {
    Map config = loadAnsibleTestConfig(params)
    log.info("config=${JsonUtils.printToJsonString(config)}")

    if (config.testDeps) {
        setupTestDependencies(config)
    }

    Map jobResult = [:]
    log.info("config.targetCollectionDir => ${config.targetCollectionDir}")
    dir(config.targetCollectionDir) {
        jobResult = runAnsibleTestConfig(config)
    }
    // Archiving after dir closes (from workspace root)
//     String fullResultsDir = "${config.targetCollectionDir}/${config.testResultsDir}"
//     log.info("fullResultsDir => ${fullResultsDir}")
    String relativeResultsDir = "ansible_collections/${config.collectionNamespace}/${config.collectionName}/${config.testResultsDir}"  // NEW: Relative to workspace
    log.info("relativeResultsDir => ${relativeResultsDir}")
    sh("find ${relativeResultsDir} -type f")

    log.info("Archiving test results from : ${relativeResultsDir}")
    archiveArtifacts(
        artifacts: "${relativeResultsDir}/**",  // NEW: Use relative path
        fingerprint: true,
        onlyIfSuccessful: false,
        allowEmptyArchive: true
    )

    // Use relative path
    String junitPattern = "${relativeResultsDir}/*-${config.ansibleTestCommand}*.xml"
    log.info("Recording junit test results from path: ${junitPattern}")
    junit(
        testResults: junitPattern,
        skipPublishingChecks: true,
        allowEmptyResults: true
    )

    log.info("Test results archived.")
    return jobResult
}

Map loadAnsibleTestConfig(Map params) {
    Map config = params.clone()

    if (!config?.collectionNamespace || !config?.collectionName) {
        error "FATAL: collectionNamespace and collectionName must be provided by caller."
    }

    config.ansibleEnvVarsList = [
        "ANSIBLE_COLLECTIONS_PATH=~/.ansible/collections:/usr/share/ansible/collections:${config.collectionsBaseDir}"
    ]

    log.debug("config.collectionsBaseDir=${config.collectionsBaseDir}")
    return config
}

void setupTestDependencies(Map config) {
//     config.dependsCollectionDir = "${config.targetCollectionDir}/ansible_collections"
    config.dependsCollectionDir = "${config.collectionsBaseDir}"
    sh "mkdir -p ${config.dependsCollectionDir}"

    log.info("installing test dependencies")
    List depsToInstall = []
    if (config.testDeps instanceof String) {
        depsToInstall = config.testDeps.split(',').findAll { it.trim() != '' }
//         depsToInstall = config.testDeps.split('\\r?\\n').findAll { it.trim() != '' }
        if (depsToInstall.isEmpty()) {
            depsToInstall = [config.testDeps.trim()]
        }
    } else if (config.testDeps instanceof List) {
        depsToInstall = config.testDeps.findAll { it.trim() != '' }
    } else {
        error "Unsupported type for 'testDeps'. Must be a String or List<String>."
    }

    for (dep in depsToInstall) {
        log.info("installing collection: ${dep} to ${config.collectionsBaseDir}")
        sh "find . -maxdepth 3"

        sh "set -x; ansible-galaxy collection install ${dep} -p ${config.collectionsBaseDir}; set +x"

        log.info("content in ${config.collectionsBaseDir} after installing ${dep}")
        sh "find ${config.collectionsBaseDir} -maxdepth 3"
    }
}

Map runAnsibleTestConfig(Map config) {
    Map jobResult = [:]

    if (config?.preTestCmd) {
        log.info("executing pre-test-cmd: '${config.preTestCmd}'")
        sh "${config.preTestCmd}"
    }

    log.info("running 'ansible-test ${config.ansibleTestCommand}' For ansible ${config.ansibleVersion} Under python ${config.pythonVersion}")
    sh("mkdir -p ${config.testResultsDir}")
    //         sh("ansible-test --version")

    List testCmdList = []
    //         testCmdList.push("env ANSIBLE_TEST_CONTENT_ROOT=${config.targetCollectionDir}")
    //         testCmdList.push("env ANSIBLE_TEST_CONTENT_ROOT=${pwd()}")
    testCmdList.push("ansible-test")
    testCmdList.push("${config.ansibleTestCommand}")
    if (config?.debugVerbosity) {
        testCmdList.push("${config.debugVerbosity}")
    }
    if (config?.ansibleDebugFlag) {
        testCmdList.push("${config.ansibleDebugFlag}")
    }
    testCmdList.push("--color no")
    if (config.ansibleTestCommand == "sanity") {
        testCmdList.push("--junit")
    } else if (config.ansibleTestCommand == "units") {
        testCmdList.push("--requirements")
        testCmdList.push("--truncate 0")
        testCmdList.push("--coverage")
//         testCmdList.push("--containers '{}'")
//         testCmdList.push("--docker")
//         testCmdList.push("--docker default")
    }
    testCmdList.push("--python ${config.pythonVersion}")
//     testCmdList.push("2>&1 | tee ${config.testResultsDir}/ansible-test-console.txt")
//     testCmdList.push("| tee ${config.testResultsDir}/ansible-test-console.txt")
//     testCmdList.push("|| true") // To ensure the shell command itself doesn't fail the pipeline immediately

    String testCmd = testCmdList.join(' ')

    List testEnvList = config.ansibleEnvVarsList
    testEnvList += [
        "ANSIBLE_TEST_CONTENT_ROOT=${pwd()}"
    ]
    log.info("testEnvList=${JsonUtils.printToJsonString(testEnvList)}")

    try {
        log.info("current directory => ${pwd()}")
        // Wrap in withEnv to set content root and other vars
        withEnv(testEnvList) {
            sh(testCmd)
        }
        jobResult.buildStatus = "SUCCESSFUL"
        jobResult.failed = false
        log.info("ansible-test completed successfully.")
    } catch (Exception e) {
        // General catch-all for any other unexpected failures
        log.error("ansible-test failed: ${e.getMessage()}")
        jobResult.buildStatus = "FAILED"
        jobResult.failed = true
        currentBuild.result = 'FAILURE'
        // Re-raise to fail the stage
        throw e
    }
    return jobResult
}
