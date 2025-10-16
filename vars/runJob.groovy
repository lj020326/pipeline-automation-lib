#!/usr/bin/env groovy
// @Grab('org.jenkins-ci.plugins:copyartifact:1.46.2')

import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.JsonUtils
//import groovy.json.*

import hudson.model.Result

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
// @Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

Map call(Map rawJobConfigs) {
    Map jobConfigs = rawJobConfigs.clone()
    jobConfigs.get('copyChildJobArtifacts', false)
    List junitXmlsPatternsDefault = [
        "**/target/surefire-reports/*.xml",
        "**/.test-results/*.xml",
        "**/tests/output/junit/*.xml"
    ]
    jobConfigs.get('junitXmlsPatterns', junitXmlsPatternsDefault)

    jobConfigs.get('logLevel', 'INFO')
    log.setLevel(jobConfigs.logLevel)

    log.debug("rawJobConfigs=${JsonUtils.printToJsonString(rawJobConfigs)}")

    Map jobResults = [:]
    jobResults.failed = false

    if (!jobConfigs?.jobFolder && !jobConfigs?.jobName) {
        log.error("'jobFolder' or 'jobName' must be specified")
        return jobResults
    }
    if (!jobConfigs?.jobParameters) {
        log.error("jobParameters must be specified")
        return jobResults
    }
    jobConfigs.get("testResultsDir", "test-results")
    jobConfigs.get('wait', false)
    jobConfigs.get('propagate', true)

//     String jobFolder = rawJobConfigs.jobFolder
//     jobConfigs.jobFolder = rawJobConfigs.get("jobFolder", jobConfigs.jobName)
    jobConfigs.get("jobFolder", jobConfigs.jobName)
    if (jobConfigs?.jobBaseFolder) {
        jobConfigs.jobFolder = "${jobConfigs.jobBaseFolder}/${jobConfigs.jobFolder}"
    }

    log.info("jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String logPrefix = "[${jobConfigs.jobFolder}]:"

    // This will copy all files packaged in STASH_NAME to agent workspace root directory.
    // To copy to another agent directory, see [https://github.com/jenkinsci/pipeline-examples]
    log.info("${logPrefix} started")

    // --- Start of new random delay logic ---
    // Check for maxRandomDelaySeconds in jobParameters
    Integer maxRandomDelaySeconds = 0
    if (jobConfigs?.maxRandomDelaySeconds) {
        try {
            maxRandomDelaySeconds = jobConfigs.maxRandomDelaySeconds as Integer
        } catch (NumberFormatException e) {
            log.warn("${logPrefix} Invalid value for MaxRandomDelaySeconds: ${jobConfigs.maxRandomDelaySeconds}. Defaulting to 0.")
            maxRandomDelaySeconds = 0
        }
    }

    if (maxRandomDelaySeconds > 0) {
        // Generate a random delay between 0 and maxRandomDelaySeconds
        Random rand = new Random()
        Integer delaySeconds = rand.nextInt(maxRandomDelaySeconds + 1) // +1 to include maxRandomDelaySeconds
        log.info("${logPrefix} Waiting for a random delay of ${delaySeconds} seconds before starting the child job...")
        sleep(time: delaySeconds, unit: 'SECONDS')
    } else {
        log.info("${logPrefix} No random delay configured (MaxRandomDelaySeconds is 0 or not set).")
    }
    // --- End of new random delay logic ---

    boolean result = false
    List paramList=[]

    jobConfigs.jobParameters.each { key, value ->
        if (value) {
            if (jobConfigs?.supportedJobParams) {
                if (key in jobConfigs.supportedJobParams) {
                    paramList.add([$class: 'StringParameterValue', name: key, value: "${value}"])
                }
            } else {
                paramList.add([$class: 'StringParameterValue', name: key, value: "${value}"])
            }
        }
    }
    log.debug("${logPrefix} paramList=${JsonUtils.printToJsonString(paramList)}")

    try {
        log.info("${logPrefix} Triggering job: ${jobConfigs.jobFolder}")
        def buildResult = build job: jobConfigs.jobFolder, parameters: paramList, propagate: jobConfigs.propagate, wait: jobConfigs.wait

        jobResults.buildNumber = buildResult.number
        jobResults.jobURL = buildResult.absoluteUrl
        jobResults.jobResult = buildResult.result.toString()
        log.debug("${logPrefix} buildResult.result => : ${buildResult.result}")
        jobResults.failed = (jobResults.jobResult != "SUCCESS")
        jobResults.error = null

        jobResults.parentJobName = env.JOB_NAME
        jobResults.parentJobURL = env.BUILD_URL
        jobResults.parentBuildNumber = env.BUILD_NUMBER

        log.debug("${logPrefix} jobResults* => : ${JsonUtils.printToJsonString(jobResults)}")

        if (jobConfigs.wait) {
//             // ref: https://github.com/renfeiw/openjdk-tests/blob/master/buildenv/jenkins/JenkinsfileBase
//             def jobInvocation = buildResult.getRawBuild()
//             jobResults.buildId = jobInvocation.getNumber()

//             jobResults.buildNumber = buildResult.getNumber()
//             jobResults.jobURL = buildResult.getAbsoluteUrl()

            // ref: https://github.com/jenkinsci/jenkins-multijob-plugin/blob/master/src/main/java/com/tikal/jenkins/plugins/multijob/MultiJobBuild.java
            //String jobResult = buildResult.getResult()

            // Copy artifacts if the option is enabled
            if (jobConfigs.copyChildJobArtifacts) {
                log.info("${logPrefix} Copying artifacts from job: /${jobConfigs.jobFolder} #${jobResults.buildNumber}")
                try {
                    copyArtifacts(
                        projectName: "/${jobConfigs.jobFolder}",
                        selector: specific("${jobResults.buildNumber}"),
//                         filter: '**/tests/output/junit/*.xml',
                        filter: "**/*.xml",
                        fingerprintArtifacts: true,
                        target: jobConfigs.testResultsDir,
                        optional: true  // Fail softly if no artifacts/perms
                    )
                    log.info("${logPrefix} Artifacts copied successfully.")

                    log.info("${logPrefix} Recording JUnit results")
                    try {
                        junit(
                            testResults: "${jobConfigs.testResultsDir}/**/*.xml",
                            skipPublishingChecks: true,
                            allowEmptyResults: true
                        )
                        log.info("${logPrefix} Recorded JUnit results successfully")
                    } catch (Exception archiveErr) {
                        log.warn("${logPrefix} Failed to record JUnit: ${archiveErr.message}")
                    }

                } catch (Exception copyErr) {
                    log.error("${logPrefix} Failed to copy artifacts: ${copyErr.message}. Check 'Permission to Copy Artifact' in /${jobConfigs.jobFolder} config.")
                    // Don't set failed=true here; child succeeded
                }
            }

        }
    } catch (Exception err) {
        jobResults.failed = true
        jobResults.error = err.message
        log.error("job exception occurred [${err}] jobResults => : ${JsonUtils.printToJsonString(jobResults)}")
        if (jobConfigs.failFast || !jobConfigs.continueIfFailed) {
            currentBuild.result = 'FAILURE'
//             error("job exception occurred [${err}] jobResults => : ${JsonUtils.printToJsonString(jobResults)}")
//             throw err
        }
    }

    log.info("${logPrefix} finished with jobResults=${JsonUtils.printToJsonString(jobResults)}")

    return jobResults
}
