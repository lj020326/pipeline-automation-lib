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
    config.get("dockerRegistry", "media.johnson.int:5000")
    config.get("dockerImageName", "ansible/ansible-test")
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


// // Core logic for running a single ansible-test
// // This function needs to be callable from outside its original 'call' context.
// // Making it a direct global variable or a separate function within the same file.
// // For simplicity, we'll keep it within this file and ensure 'call' sets up and invokes it.
// Map runAnsibleTest(Map config) {
//     Map jobResult = [:]
//
//     // Crucially, this 'call' expects a single ansibleVersion and pythonVersion
//     // from the matrix iteration in runMatrixStages.groovy
//     if (!config.ansibleVersion || !config.pythonVersion) {
//         error "runAnsibleTest must be called with 'ansibleVersion' and 'pythonVersion' for matrix execution."
//     }
//
//     config.get("dockerImageName", "ansible/ansible-test")
//
//     config.dockerImage = getAnsibleDockerImageId(
//                             dockerImageName: config.dockerImageName,
//                             ansibleVersion: config.ansibleVersion,
//                             pythonVersion: config.pythonVersion,
//                             dockerRegistry: config.dockerRegistry)
//
//     log.info("Running tests inside docker container: ${config.dockerImage}")
//
// //     docker.image(config.dockerImage).inside {
//     docker.image(config.dockerImage).inside("-v /var/run/docker.sock:/var/run/docker.sock --privileged") {
//
//         if (config.testDeps) {
//             config.dependsCollectionDir = "${config.targetCollectionDir}/ansible_collections"
//             sh "mkdir -p ${config.dependsCollectionDir}"
//
//             log.info("installing test dependencies")
//             def depsToInstall = []
//             if (config.testDeps instanceof String) {
//                 depsToInstall = config.testDeps.split('\\r?\\n').findAll { it.trim() != '' }
//                 if (depsToInstall.isEmpty()) {
//                     depsToInstall = [config.testDeps.trim()]
//                 }
//             } else if (config.testDeps instanceof List) {
//                 depsToInstall = config.testDeps.findAll { it.trim() != '' }
//             } else {
//                 error "Unsupported type for 'testDeps'. Must be a String or List<String>."
//             }
//
//             for (dep in depsToInstall) {
// //                 log.info("installing collection: ${dep} to ${config.collectionsBaseDir}")
// //                 sh "find ${config.collectionsBaseDir}/ -maxdepth 3"
//                 log.info("installing collection: ${dep} to ${pwd()}")
//                 sh "find . -maxdepth 3"
//
// //                     sh "set -x; ansible-galaxy collection install ${dep} -p ${config.collectionsBaseDir}; set +x"
// //                     sh "set -x; ansible-galaxy collection install ${dep}; set +x"
//                 sh "set -x; ansible-galaxy collection install ${dep} -p .; set +x"
//
//                 log.info("content in ${config.collectionsBaseDir} after installing ${dep}")
//                 sh "find . -maxdepth 3"
// //                 sh "find ${config.collectionsBaseDir}/ -maxdepth 3"
//             }
//         }
//
//         if (config?.preTestCmd) {
//             log.info("executing pre-test-cmd: '${config.preTestCmd}'")
//             sh "${config.preTestCmd}"
//         }
//
// //         String ansible_collections_path="${WORKSPACE}"
// //         log.info("ansible_collections_path=${ansible_collections_path}")
//
//         log.info("running 'ansible-test ${config.ansibleTestCommand}' For ansible ${config.ansibleVersion} Under python ${config.pythonVersion}")
//         sh("mkdir -p ${config.testResultsDir}")
// //         sh("ansible-test --version")
//
//         List testCmdList = []
// //         testCmdList.push("env ANSIBLE_TEST_CONTENT_ROOT=${config.targetCollectionDir}")
// //         testCmdList.push("env ANSIBLE_TEST_CONTENT_ROOT=${pwd()}")
//         testCmdList.push("ansible-test")
//         testCmdList.push("${config.ansibleTestCommand}")
// //         testCmdList.push("--requirements")
//         if (config?.debugVerbosity) {
//             testCmdList.push("${config.debugVerbosity}")
//         }
//         testCmdList.push("--color no")
//         if (config.ansibleTestCommand=="sanity") {
//             testCmdList.push("--junit")
//         } else if (config.ansibleTestCommand=="units") {
// //             testCmdList.push("--containers '{}'")
//             testCmdList.push("--truncate 0")
//             testCmdList.push("--coverage")
//             testCmdList.push("--docker")
// //             testCmdList.push("--docker default")
//         }
//         testCmdList.push("--python ${config.pythonVersion}")
//         testCmdList.push("2>&1 | tee ${config.testResultsDir}/ansible-test-console.txt")
// //         testCmdList.push("| tee ${config.testResultsDir}/ansible-test-console.txt")
// //         testCmdList.push("|| true") // To ensure the shell command itself doesn't fail the pipeline immediately
//
//         String testCmd = testCmdList.join(' ')
//         try {
//             sh(testCmd)
//             jobResult.buildStatus = "SUCCESSFUL"
//             jobResult.failed = false
//             log.info("ansible-test completed successfully.")
//
// //             archiveArtifacts(
// //                 allowEmptyArchive: true,
// //                 artifacts: "${config.testResultsDir}/ansible-test-console.txt",
// //                 fingerprint: true
// //             )
//
//             sh("find ${config.testResultsDir} -type f")
//             archiveArtifacts(
//                 allowEmptyArchive: true,
//                 artifacts: "${config.testResultsDir}/**",
//                 fingerprint: true
//             )
//             junit(testResults: "${config.testResultsDir}/*.xml",
//                   skipPublishingChecks: true,
//                   allowEmptyResults: true
//             )
//         } catch (Exception e) {
//             archiveArtifacts(
//                 allowEmptyArchive: true,
//                 artifacts: "${config.testResultsDir}/ansible-test-console.txt",
//                 fingerprint: true
//             )
//
//             jobResult.buildStatus = "FAILED"
//             jobResult.failed = true
//             log.error("ansible-test failed: " + e.getMessage())
//             // Do not re-throw here if failFast is handled by the parallel step
//             // or if you want to collect all results before failing the build.
//             // For failFast, the Jenkins 'parallel' step will handle the abortion.
//         }
//     }
//     return jobResult
// }
