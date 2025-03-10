#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils
//import groovy.json.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map params=[:]) {

// //     Logger.init(this, LogLevel.INFO)
//     Logger log = new Logger(this, LogLevel.INFO)

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)

    def agentLabelM3 = getJenkinsAgentLabel(config.jenkinsM3NodeLabel)
    Map testResults

    pipeline {

        agent {
            label agentLabelM3 as String
        }

        tools {
            maven 'M3'
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
//            overrideIndexTriggers(false)
            skipDefaultCheckout()
            timestamps()
            timeout(time: 4, unit: 'HOURS') //Should not take longer than 2 hours to run
        }

        stages {

            stage("Run Jobs") {
                steps {
                    script {
                        log.info("Running tests")
                        testResults = runTestJobs(config)
                    }
                }
            }

            // ref: https://fusion.dettonville.int/confluence/display/CD/Clean+Your+Workspace+and+Discard+Old+Builds
            stage( "Clean WorkSpace" ) {
                steps{
                    cleanWs()
                }
            }//End Clean WorkSpace

            stage('Set Pipeline Status') {
                steps {
                    script {
                        log.info("**** final test results = [${JsonUtils.printToJsonString(testResults)}]")

//                        List resultList = testResults.subMap(config.testJobList).collect {it.value}
                        List resultList = testResults.values()
                        boolean result = (resultList.size()>0) ? resultList.inject { a, b -> a && b } : true
                        if (result) {
                            currentBuild.result = 'SUCCESS'
                        } else {
                            currentBuild.result = 'FAILURE'
                        }
                        log.info("**** currentBuild.result=${currentBuild.result}")
                    }
                }
            }

        }
        post {
            always {
                sendEmail2(currentBuild, env)
            }
        }

    }

} // body

// ref: https://stackoverflow.com/questions/4052840/most-efficient-way-to-make-the-first-character-of-a-string-lower-case
String decapitalize(String string) {
    if (string == null || string.length() == 0) {
        return string;
    }
    return string.substring(0, 1).toLowerCase() + string.substring(1);
}


def getYamlInt(Map config, String key) {
    def value
    if (config.containsKey(key)) {
        try {
            value = config[key].toInteger()
        } catch (Exception err) {
            value = config[key]
        }
    }
    return value
}

//@NonCPS
Map loadPipelineConfig(Map params, String configFile=null) {
    String logPrefix="loadPipelineConfig():"
    Map config = [:]
//    config.testJobList = ['SMOKE','SANITY','REGRESSION']
    config.supportedJobParams=['changedEmailList','alwaysEmailList','failedEmailList']

    if (configFile != null && fileExists(configFile)) {
        Map configSettings = readYaml file: "${configFile}"
        config=config + configSettings.pipeline
    }
    else if (configFile != null) {
        log.info("${logPrefix} pipeline config file ${configFile} not present, using defaults...")
    } else {
        log.info("${logPrefix} pipeline config file not specified, using defaults...")
    }

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("${logPrefix} params[${key}]=${value}")
        key=decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    config.jenkinsM3NodeLabel = config.get('jenkinsM3NodeLabel',"QA-LINUX || PROD-LINUX")
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.continueIfFailed = config.get('continueIfFailed', false)
    config.wait = config.get('wait', true)
    config.failFast = config.get('failFast', false)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    log.debug("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}

String getStageName(String stage) {
    return "${-> println 'Right Now the Stage Name is ' + stage; return stage}"
}

String createJobId(Map config, i) {
    return (config?.jobId) ? "${config.jobId}.${i+1}" : "job-${i+1}"
}

Map runTestJobs(Map config) {
    String logPrefix="runTestJobs():"
    log.info("${logPrefix} started")

    Map testResults = [:]

    config.testJobList.eachWithIndex { it, i ->

        log.info("${logPrefix} i=${i} it=${it}")

        Map stageConfig = config.findAll { !["testJobList"].contains(it.key) } + it
        String testStage = stageConfig.testStage

        stage("Running ${testStage} Test") {
            log.info("${logPrefix} starting stage ${testStage}")

            List resultList = testResults.values()
            boolean result = (resultList.size()>0) ? resultList.inject { a, b -> a && b } : true

            log.debug("${logPrefix} testStage ${testStage}: config.testJobList=${config.testJobList}")
            log.debug("${logPrefix} testStage ${testStage}: testResults=${testResults}")
            log.debug("${logPrefix} testStage ${testStage}: resultList=${resultList}")
            log.debug("${logPrefix} testStage ${testStage}: prior stage results=${result}")
            if (result || config.continueIfFailed) {
                result = runJobs(stageConfig)
                testResults["${testStage}"] = result

                log.info("${logPrefix} finishing stage ${testStage}: result=${result}")
            } else {
                log.info("${logPrefix} skipped running stage ${testStage} due to prior stage FAILURE results")
            }
        }
    }

    log.info("${logPrefix} jobResults=${JsonUtils.printToJsonString(jobResults)}")
    return jobResults

}

boolean runJobs(Map config) {

    String logPrefix="runJobs():"
    log.info("${logPrefix} started")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    if (config.jobs.size()==0) {
        log.error("${logPrefix} no jobs specified")
        return false
    }

    Map parallelJobs = [:]
    List jobResults = []

    config.jobs.eachWithIndex { it, i ->

        log.info("${logPrefix} i=${i} it=${JsonUtils.printToJsonString(it)}")

        // job configs overlay parent settings
        Map jobConfig = config.findAll { !["jobs","stage"].contains(it.key) } + it
        jobConfig.jobId = createJobId(config, i)

        if (jobConfig?.jobs) {
            jobResults.add(runJobs(jobConfig))
        }

        if (jobConfig?.job) {
            log.debug("${logPrefix} i=${i} jobConfig=${JsonUtils.printToJsonString(jobConfig)}")

            if (jobConfig?.runInParallel) {
                parallelJobs["split-${jobConfig.jobId}"] = {
                    jobResults.add(runJob(jobConfig))
                }
            } else {
                jobResults.add(runJob(jobConfig))
            }
        } else {
            jobResults.add(false)
            log.error("${logPrefix} job not specified")
//            return result
        }

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        //boolean result = (false in jobResults) ? false : true
        boolean result = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
//        if (!jobConfig.continueIfFailed && jobResults.size()>0 && !result) {
//            currentBuild.result = 'FAILURE'
//            log.info("${logPrefix} i=${i} continueIfFailed is false and results failed")
//            return result
//        }
    }

    if (parallelJobs.size()>0) {
        log.info("${logPrefix} parallelJobs=${parallelJobs}")
        parallel parallelJobs
    }

    boolean result = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
//    if (!config.continueIfFailed && jobResults.size()>0 && !result) {
//        currentBuild.result = 'FAILURE'
//        log.info("${logPrefix} i=${i} continueIfFailed is false and results failed - not running any more jobs")
//    }

    log.info("${logPrefix} finished: result = ${result}")
    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    //return (false in jobResults) ? false : true
    return result
}

boolean runJob(Map config) {

    String logPrefix="runJob():"

    // This will copy all files packaged in STASH_NAME to agent workspace root directory.
    // To copy to another agent directory, see [https://github.com/jenkinsci/pipeline-examples]
    log.info("${logPrefix} started")

    boolean result = false
    List paramList=[]

    config.each { key, value ->
        if (key in config.supportedJobParams) {
            paramList.add([$class: 'StringParameterValue', name: key, value: value])
        }
    }

    if (config.get('job',null)==null) {
        log.error("${logPrefix} job not specified")
        return result
    }

    try {
        log.info("${logPrefix} starting job ${config.job}")
//        build job: config.job, parameters: paramList, wait: config.wait, propagate: !config.continueIfFailed

        // ref: http://jenkins-ci.361315.n4.nabble.com/How-to-get-build-results-from-a-build-job-in-a-pipeline-td4897887.html
        def jobBuild = build job: config.job, parameters: paramList, wait: config.wait, propagate: false
        def jobResult = jobBuild.getResult()

        log.info("${logPrefix} Build ${config.job} returned result: ${jobResult}")

        if (jobResult != 'SUCCESS') {
            result = false
            if (config.failFast) {
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
        if (config.failFast) {
            currentBuild.result = 'FAILURE'
            throw err
        }
    }

    log.info("${logPrefix} finished with result = ${result}")

    return result
}

