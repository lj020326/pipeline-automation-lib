#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.MapMerge

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils
//import groovy.json.*

import hudson.model.Result

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
// @Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

Map call(Map rawJobConfigs) {
    Map jobConfigs = rawJobConfigs.clone()
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

    boolean result = false
    List paramList=[]

    jobConfigs.jobParameters.each { key, value ->
        if (jobConfigs?.supportedJobParams) {
            if (key in jobConfigs.supportedJobParams) {
                paramList.add([$class: 'StringParameterValue', name: key, value: value])
            }
        } else {
            paramList.add([$class: 'StringParameterValue', name: key, value: value])
        }
    }
    log.debug("${logPrefix} paramList=${JsonUtils.printToJsonString(paramList)}")

    try {
        log.info("${logPrefix} starting job ${jobConfigs.jobFolder}")
        // ref: http://jenkins-ci.361315.n4.nabble.com/How-to-get-build-results-from-a-build-job-in-a-pipeline-td4897887.html
        def jobBuild = build job: jobConfigs.jobFolder, parameters: paramList, wait: jobConfigs.wait, propagate: jobConfigs.propagate

        jobResults.parentJobName = JOB_NAME
        jobResults.parentJobURL = BUILD_URL
        jobResults.parentBuildNumber = BUILD_NUMBER

        if (jobConfigs.wait) {
//             // ref: https://github.com/renfeiw/openjdk-tests/blob/master/buildenv/jenkins/JenkinsfileBase
//             def jobInvocation = jobBuild.getRawBuild()
//             jobResults.buildId = jobInvocation.getNumber()

            jobResults.buildNumber = jobBuild.getNumber()
            jobResults.jobURL = jobBuild.getAbsoluteUrl()

            // ref: https://github.com/jenkinsci/jenkins-multijob-plugin/blob/master/src/main/java/com/tikal/jenkins/plugins/multijob/MultiJobBuild.java
            String jobResult = jobBuild.getResult()
            log.info("${logPrefix} Job build result: ${jobResult}")

            // ref: https://javadoc.jenkins.io/plugin/workflow-support/org/jenkinsci/plugins/workflow/support/steps/build/RunWrapper.html
            // ref: https://github.com/apache/trafficserver-ci/blob/main/jenkins/github/github_polling.pipeline
            jobResults.jobResult = jobResult
            if (jobResult != 'SUCCESS') {
                jobResults.failed = true
                log.error("${logPrefix} test job failed with jobResults ==> : ${JsonUtils.printToJsonString(jobResults)}")
                if (jobConfigs.failFast || !jobConfigs.continueIfFailed) {
                    currentBuild.result = 'FAILURE'
//                     error("${logPrefix} test job failed with jobResults ==> : ${JsonUtils.printToJsonString(jobResults)}")
                }
            }
        }

    } catch (Exception err) {
        jobResults.failed = true
        jobResults.error = err.message
        log.error("job exception occurred [${err}] jobResults ==> : ${JsonUtils.printToJsonString(jobResults)}")
        if (jobConfigs.failFast || !jobConfigs.continueIfFailed) {
            currentBuild.result = 'FAILURE'
//             error("job exception occurred [${err}] jobResults ==> : ${JsonUtils.printToJsonString(jobResults)}")
//             throw err
        }
    }

    log.info("${logPrefix} finished with jobResults=${JsonUtils.printToJsonString(jobResults)}")

    return jobResults
}
