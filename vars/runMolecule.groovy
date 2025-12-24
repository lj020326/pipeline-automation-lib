#!/usr/bin/env groovy

/**
 * Runs 'molecule' with specified command, *a single* Ansible core, and *a single* Python version
 *
 * @param config A map containing configuration options:
 * - ansibleVersion (String, REQUIRED): A single Ansible core version to test against.
 * - pythonVersion (String, REQUIRED): A single Python version to test under.
 * - moleculeCommand (String, optional): The molecule command to run (e.g., 'test', 'converge', 'check', 'verify').
 * - moleculeImageRegistry (String, optional): The the molecule image registry (e.g., 'registry.example.int:5000').
 * - moleculeImage (String, optional): The molecule image to run (e.g., 'ubuntu2404-systemd-python', 'centos9-systemd-python').
 * - moleculeScenario (String, optional): The molecule scenario to run (e.g., 'bootstrap_docker', 'bootstrap_java', 'bootstrap_linux', 'bootstrap_linux_package')", name: 'MoleculeScenario').
 * - moleculeDebugFlag (Boolean, optional): Set to enable molecule debug.
 * Defaults to 'test'.
 * - preTestCmd (String, optional): A shell command to execute before 'molecule' runs.
 */

import com.dettonville.pipeline.utils.JsonUtils

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

// Core logic for running a single molecule command
// This function needs to be callable from outside its original 'call' context.
// Making it a direct global variable or a separate function within the same file.
// For simplicity, we'll keep it within this file and ensure 'call' sets up and invokes it.
Map call(Map params = [:]) {
    Map config = params.clone()
    log.info("config=${JsonUtils.printToJsonString(config)}")

    Map jobResult = [:]
    jobResult = runMoleculeCommand(config)
    return jobResult
}

Map runMoleculeCommand(Map config) {
    Map jobResult = [:]

    log.info("pwd => ${pwd()}")

    if (config?.preTestCmd) {
        log.info("executing pre-test-cmd: '${config.preTestCmd}'")
        sh "${config.preTestCmd}"
    }

    log.info("running 'molecule ${config.moleculeCommand}' For ansible ${config.ansibleVersion} Under python ${config.pythonVersion}")
    sh("mkdir -p ${config.testResultsDir}")
    //         sh("molecule --version")

    List commandList = []
    commandList.push("molecule")
    if (config?.ansibleDebugFlag && config.ansibleDebugFlag.toBoolean()) {
        commandList.push("--debug")
    }
    commandList.push(config.moleculeCommand)
    if (config?.moleculeScenario) {
        commandList.push("-s ${config.moleculeScenario}")
    }
    String command = commandList.join(' ')

    List commandEnvList = []
    if (config?.moleculeImageRegistry) {
        commandEnvList.push("MOLECULE_IMAGE_REGISTRY=${config.moleculeImageRegistry}")
    }
    if (config?.moleculeImage) {
        commandEnvList.push("MOLECULE_IMAGE_LABEL=${config.moleculeImage}")
    }
    log.info("commandEnvList=${JsonUtils.printToJsonString(commandEnvList)}")

    try {
        log.info("current directory => ${pwd()}")
        // Wrap in withEnv to set content root and other vars
        withEnv(commandEnvList) {
            sh(command)
        }
        jobResult.buildStatus = "SUCCESSFUL"
        jobResult.failed = false
        log.info("molecule completed successfully.")
    } catch (Exception e) {
        // General catch-all for any other unexpected failures
        log.error("molecule failed: ${e.getMessage()}")
        jobResult.buildStatus = "FAILED"
        jobResult.failed = true
        currentBuild.result = 'FAILURE'
        // Re-raise to fail the stage
        throw e
    } finally {
        // Archiving after dir closes (from workspace root)
        sh("find ${config.testResultsDir} -type f")
        log.info("Archiving test results from: ${config.testResultsDir}")
        archiveArtifacts(
            artifacts: "${config.testResultsDir}/**",  // NEW: Use relative path
            fingerprint: true,
            onlyIfSuccessful: false,
            allowEmptyArchive: true
        )

        // Use relative path
        String junitPattern = "${config.testResultsDir}/*.xml"
        log.info("Recording junit test results from path: ${junitPattern}")
        junit(
            testResults: junitPattern,
            skipPublishingChecks: true,
            allowEmptyResults: true
        )

        log.info("Test results archived.")
    }
    return jobResult
}
