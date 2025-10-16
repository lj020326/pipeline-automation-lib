#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.JsonUtils
//import groovy.json.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)

    def agentLabelJobNode = getJenkinsAgentLabel(config.jenkinsJobNodeNodeLabel)
    boolean results

    pipeline {

        agent {
            label agentLabelJobNode as String
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
                        results = runJobList(config)
                    }
                }
            }

            // ref: https://fusion.dettonville.int/confluence/display/CD/Clean+Your+Workspace+and+Discard+Old+Builds
            stage( "Clean WorkSpace" ) {
                steps{
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }//End Clean WorkSpace

            stage('Set Pipeline Status') {
                steps {
                    script {
                        log.info("**** final results = [${results}]")
                        currentBuild.result = results ? 'SUCCESS' : 'FAILURE'
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
    Map config = [:]

    if (configFile != null && fileExists(configFile)) {
        Map configSettings = readYaml file: "${configFile}"
        config=config + configSettings.pipeline
    }
    else if (configFile != null) {
        log.info("pipeline config file ${configFile} not present, using defaults...")
    } else {
        log.info("pipeline config file not specified, using defaults...")
    }

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    config.get('jenkinsJobNodeNodeLabel',"any")
    config.get('logLevel', "INFO")
    config.get('debugPipeline', false)
    config.get('continueIfFailed', false)
    config.get('wait', true)
    config.get('propagate', false)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    log.info("params=${params}")
    log.debug("config=${config}")

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

boolean runJobList(Map config) {
    log.info("started")

    String stageName = config.get('stage',null)
    boolean result
    if (stageName!=null) {
        stage (stageName) {
            log.info("starting stage ${stageName}")
            result = runJobs(config)

            log.info("finishing stage ${stageName}")
            if (!config.continueIfFailed && !result) {
                currentBuild.result = 'FAILURE'
                // ref: https://issues.jenkins-ci.org/browse/JENKINS-36087
                //currentStage.result =  'FAILURE'
                log.info("continueIfFailed is false and results failed - not running any more jobs")
                return result
            }

        }
    } else {
        result = runJobs(config)
    }

    return result

}

boolean runJobs(Map config) {
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    Map parallelJobs = [:]
    List jobResults = []
    config.jobs.eachWithIndex { it, i ->

        log.info("i=${i} it=${JsonUtils.printToJsonString(it)}")

        // job configs overlay parent settings
        Map jobConfig = config.findAll { !["jobs","stage"].contains(it.key) } + it
        jobConfig.jobId = createJobId(config, i)

        if (jobConfig?.jobs) {
            jobResults.add(runJobList(jobConfig))
        }

        if (jobConfig?.jobName) {
            log.debug("i=${i} jobConfig=${JsonUtils.printToJsonString(jobConfig)}")

            if (jobConfig?.runInParallel) {
                parallelJobs["split-${jobConfig.jobId}"] = {
                    jobResults.add(runJob(jobConfig))
                }
            } else {
                jobResults.add(runJob(jobConfig))

            }
        }

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        //boolean result = (false in jobResults) ? false : true
        boolean result = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
        if (!jobConfig.continueIfFailed && jobResults.size()>0 && !result) {
            currentBuild.result = 'FAILURE'
            log.info("i=${i} continueIfFailed is false and results failed - not running any more jobs")
            return result
        }

    }

    if (parallelJobs.size()>0) {
        log.info("parallelJobs=${parallelJobs}")
        parallel parallelJobs
    }

    boolean result = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
    if (!config.continueIfFailed && jobResults.size()>0 && !result) {
        currentBuild.result = 'FAILURE'
        log.info("i=${i} continueIfFailed is false and results failed - not running any more jobs")
    }

    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    //return (false in jobResults) ? false : true
    return result
}

boolean runJob(Map config) {

    // This will copy all files packaged in STASH_NAME to agent workspace root directory.
    // To copy to another agent directory, see [https://github.com/jenkinsci/pipeline-examples]
    log.debug("started")

    boolean result = false
    List paramList=[]

    config.each { key, value ->
        if (key in config.supportedJobParams) {
            paramList.add([$class: 'StringParameterValue', name: key, value: value])
        }
    }

    if (config.get('job',null)==null) {
        log.error("job not specified")
        return result
    }

    try {
        log.info("starting job ${config.jobName}")
        build job: config.jobName, parameters: paramList, wait: config.wait, propagate: !config.continueIfFailed
    } catch (Exception err) {
        log.error("job exception occurred [${err}]")
        if (config.failFast) {
            currentBuild.result = 'FAILURE'
            throw err
        }
        if (!config.continueIfFailed) {
            currentBuild.result = 'FAILURE'
            return result
        }
    }

    result = true

    return result
}

