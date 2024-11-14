#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.MapMerge

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils
//import groovy.json.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

boolean call(Map jobConfigs) {

    String logPrefix="runJob():"
    log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    if (!jobConfigs?.jobFolder && !jobConfigs?.job) {
        log.error("${logPrefix} 'jobConfigs.jobFolder' or 'jobConfigs.job' must be specified")
        return false
    }
    if (!jobConfigs?.wait) {
        log.error("${logPrefix} jobConfigs.wait must be specified")
        return false
    }
    if (!jobConfigs?.jobParameters) {
        log.error("${logPrefix} jobConfigs.jobParameters must be specified")
        return false
    }

//     String jobFolder = jobConfigs.jobFolder
    String jobFolder = jobConfigs.get("jobFolder", jobConfigs.job)
    if (jobConfigs?.jobBaseFolder) {
        jobFolder = "${jobConfigs.jobBaseFolder}/${jobConfigs.jobFolder}"
    }
    logPrefix="runJob(${jobFolder}):"

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
    log.info("${logPrefix} paramList=${JsonUtils.printToJsonString(paramList)}")

    try {
        log.info("${logPrefix} starting job ${jobConfigs.jobFolder}")
//        build job: jobConfigs.job, parameters: paramList, wait: jobConfigs.wait, propagate: !jobConfigs.continueIfFailed

        // ref: http://jenkins-ci.361315.n4.nabble.com/How-to-get-build-results-from-a-build-job-in-a-pipeline-td4897887.html
        def jobBuild = build job: jobFolder, parameters: paramList, wait: jobConfigs.wait, propagate: false
        def jobResult = jobBuild.getResult()

        log.info("${logPrefix} Build returned result: ${jobResult}")

        if (jobResult != 'SUCCESS') {
            result = false
            if (jobConfigs.failFast) {
                currentBuild.result = 'FAILURE'
                log.error("${logPrefix}: test job failed with result: ${jobResult}")
                error("${logPrefix}: test job failed with result: ${jobResult}")
            }
        } else {
            result = true
        }
    } catch (Exception err) {
        log.error("${logPrefix} job exception occurred [${err}]")
        result = false
        if (jobConfigs.failFast) {
            currentBuild.result = 'FAILURE'
            throw err
        }
        if (!jobConfigs.continueIfFailed) {
            currentBuild.result = 'FAILURE'
            return result
        }
    }

    log.info("${logPrefix} finished with result = ${result}")

    return result
}
