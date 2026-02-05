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

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

// The main entry point for the global variable function
Map call(Map params = [:]) {
    // This 'call' method will now execute runSingleAnsibleTest for a single combination.
    // It is effectively one "cell" in the matrix.

    Map config = params.clone()
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    // Apply default values if not provided by the matrix driver
    config.get("runnerRegistry", "media.johnson.int:5000")
    config.get("runnerImageName", "ansible/ansible-test")
    config.get("ansibleVersion", "2.19")
    config.get("pythonVersion", "3.13")
    config.get("ansibleTestCommand", "sanity")
    config.get("testDeps", [])
    config.get("preTestCmd", "")
    config.get('testResultsDir', 'tests/output/junit')

    log.info("Running tests inside docker container: ${config.dockerImage}")

    Map result = [:]

//     docker.image(config.dockerImage).inside {
    docker.image(config.dockerImage).inside("-v /var/run/docker.sock:/var/run/docker.sock --privileged") {
        // Execute the single test run
        result = runAnsibleTest(config)
    }

    // You might want to return the result, so the calling function (runMatrixStages) can collect it.
    return result
}
