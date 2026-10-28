#!/usr/bin/env groovy

/**
 * Loads and executes ansible-test configurations from YAML manifest files
 * located in the repository.
 *
 * This function looks for default manifest files like:
 * - ./.jenkins/ansible-test-sanity.yml
 * - ./.jenkins/ansible-test-units.yml
 * - ./.jenkins/ansible-test-integration.yml
 *
 * Users can override these default paths via the `config` map.
 * Each YAML file should contain parameters directly consumable by the `runAnsibleTest` function.
 *
 * @param config A map to override default manifest file paths:
 * - sanityConfigFile (String, optional): Path to the sanity test YAML config.
 * - unitsConfigFile (String, optional): Path to the units test YAML config.
 * - integrationConfigFile (String, optional): Path to the integration test YAML config.
 */

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

Map call(Map args=[:]) {

    Map config = args.clone()
    Map collectedResults = [:]
    // To collect results from parallel jobs
    collectedResults.results = [:]

    config.get("ansibleVersion", "2.21")
    config.get("pythonVersion", "3.13")
    config.get("runnerImageName", "ansible/ansible-test")
    config.get("runnerRegistry", "media.johnson.int:5000")

    config.get("ansibleTestCommand", "units")
    config.get("testDeps", [])
    config.get("preTestCmd", "")
    config.get("testResultsDir", ".test-results")
    config.get("junitTestResultsDir", "tests/output/junit")

    log.info("Successfully loaded config for '${config.ansibleTestCommand}'. Running tests...")

    if (config.strategy?.matrix) {
        log.info("Matrix strategy detected for '${config.ansibleTestCommand}'. Generating parallel stages.")

        Map strategyConfig = [
            script: this,
            strategy: config.strategy,
            stageNamePrefix: "Ansible Test",
            maxRandomDelaySeconds: config.maxRandomDelaySeconds,
            baseConfig: config,
            // Define the executorScript as a closure
            executorScript: { comboConfig ->
                // This closure will be executed for each matrix combination.
                // It needs to return the result of the actual test execution.
                // The 'dir' step should wrap the execution of 'runAnsibleTests'
                // to ensure it runs in the correct collection directory.
                return runAnsibleTestJob(comboConfig)
            }
        ]

        Map generated = runStrategyMatrix(strategyConfig)
        Map parallelJobs = generated.stages
        collectedResults.results = generated.results

        log.info("Running parallel matrix jobs for '${config.ansibleTestCommand}'.")
        parallel parallelJobs // Execute the parallel jobs with failFast applied by runMatrixStages
        log.info("Completed parallel matrix jobs for '${config.ansibleTestCommand}'.")

        // Optionally, process collectedResults here if needed
        log.debug("collectedResults=${JsonUtils.printToJsonString(collectedResults)}")
        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
        boolean failed = (collectedResults.results.size()>0) ? collectedResults.results.inject(false) { a, k, v -> a || v.failed } : false

        log.info("failed=${failed}")

        collectedResults.failed = failed
        if (collectedResults.failed && config.strategy['fail-fast']) {
            log.error("FAST FAIL")
            currentBuild.result = 'FAILURE'
        }
    } else {
        error "Manifest for '${config.ansibleTestCommand}' does not define a 'strategy.matrix'. The pipeline is configured for matrix execution."
    }

    log.info("Ansible Test finished")

    log.info("collectedResults=${JsonUtils.printToJsonString(collectedResults)}")
    return collectedResults
}

Map runAnsibleTestJob(Map config) {
    log.debug("GIT_URL=${env.GIT_URL}")
    log.debug("GIT_BRANCH=${env.GIT_BRANCH}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    Map jobConfigs = [
        jobFolder: "INFRA/repo-automation/run-ansible-test",
        jobParameters: [
            CollectionNamespace: config.collectionNamespace,
            CollectionName: config.collectionName,
            Timeout: config.childJobTimeout ?: "4",
            TimeoutUnit: config.childJobTimeoutUnit ?: "HOURS",
            GitRepoUrl: config.gitRepoUrl ?: env.GIT_URL,
            GitRepoBranch: config.gitRepoBranch ?: env.GIT_BRANCH,
            GitCredentialsId: config.gitCredentialsId,
            RunnerRegistry: config.runnerRegistry,
            RunnerImageName: config.runnerImageName,
            AnsibleVersion: config.ansibleVersion,
            PythonVersion: config.pythonVersion,
            AnsibleTestCommand: config.ansibleTestCommand,
            TestDeps: config.testDeps.join(','),
            PreTestCmd: config.preTestCmd,
            TestResultsDir: config.testResultsDir,
            JunitTestResultsDir: config.junitTestResultsDir,
        ],
        propagate: config.propagate,
        wait: config.wait,
        failFast: config.failFast,
        logLevel: config.logLevel,
        maxRandomDelaySeconds: config.maxRandomDelaySeconds,
        copyChildJobArtifacts: config.copyChildJobArtifacts,
        junitXmlsPatterns: config.junitXmlsPatterns
    ]

    log.debug("jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    Map jobResult = runJob(jobConfigs)

    log.debug("jobResult=${JsonUtils.printToJsonString(jobResult)}")

    return jobResult
}
