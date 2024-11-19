#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.MapMerge

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
    Map config = loadPipelineConfig(params)

//    def agentLabelJobNode = getJenkinsAgentLabel(config.jenkinsJobNodeNodeLabel)
    Map jobResults

    pipeline {

        agent any
//        agent {
//            label agentLabelJobNode as String
//        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
//            overrideIndexTriggers(false)
            skipDefaultCheckout()
            disableConcurrentBuilds()
            timestamps()
            timeout(time: 4, unit: 'HOURS') //Should not take longer than 2 hours to run
        }

        stages {

			stage('Load job config file') {
				when {
                    allOf {
                        expression { config.jobConfigFile }
                        expression { fileExists config.jobConfigFile }
                    }
				}
				steps {
					script {
                        config = loadJobConfigFile(config)
                        log.info("Merged config=${JsonUtils.printToJsonString(config)}")
					}
				}
			}

            stage("Run Jobs") {
                steps {
                    script {
                        log.info("Running jobs")
                        jobResults = runJobStage(config)
                    }
                }
            }

            stage( "Clean WorkSpace" ) {
                steps{
                    cleanWs()
                }
            }

            stage('Set Pipeline Status') {
                steps {
                    script {
                        log.info("**** final test results = [${JsonUtils.printToJsonString(jobResults)}]")

                        List resultList = jobResults.values()
                        boolean result = (resultList.size()>0) ? resultList.inject { a, b -> a && b } : true

                        log.info("**** final result = [${result}]")
                        currentBuild.result = result ? 'SUCCESS' : 'FAILURE'
                        log.info("**** currentBuild.result=${currentBuild.result}")
                    }
                }
            }

        }
        post {
            always {
                script {
                    List emailAdditionalDistList = []
                    if (config.gitBranch in ['main','QA','PROD'] || config.gitBranch.startsWith("release/")) {
                        if (config?.deployEmailDistList) {
                            emailAdditionalDistList = config.deployEmailDistList
                            log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result})")
                            sendEmail(currentBuild, env, emailAdditionalDistList=emailAdditionalDistList)
                        }
                    } else if (config.gitBranch in ['development']) {
                        if (config?.alwaysEmailDistList) {
                            emailAdditionalDistList = config.alwaysEmailDistList
                            log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result})")
                            sendEmail(currentBuild, env, emailAdditionalDistList=emailAdditionalDistList)
                        }
                    } else {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result}, 'RequesterRecipientProvider')")
                        sendEmail(currentBuild, env)
                    }
                }
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
    config.supportedJobParams=['changedEmailList','alwaysEmailList','failedEmailList']
    config.gitBranch = env.GIT_BRANCH

    if (configFile != null && fileExists(configFile)) {
        Map configSettings = readYaml file: "${configFile}"
//        config=config + configSettings.pipeline
        config = MapMerge.merge(configSettings.pipeline, config)
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

    config.jenkinsJobNodeNodeLabel = config.get('jenkinsJobNodeNodeLabel',"any")
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.continueIfFailed = config.get('continueIfFailed', false)
    config.wait = config.get('wait', true)
    config.failFast = config.get('failFast', false)

    config.stage = config.get('stage', 'runJobs-stage')

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.info("${logPrefix} log.level=${log.level}")

    log.debug("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

Map loadJobConfigFile(Map baseConfig) {
    String logPrefix="loadJobConfigFile():"

    Map jobConfigFileMap = readYaml file: config.ansiblePipelineConfigFile
    log.debug("${logPrefix} jobConfigFileMap=${JsonUtils.printToJsonString(jobConfigFileMap)}")

    Map config = MapMerge.merge(baseConfig, jobConfigFileMap)

    log.info("${logPrefix} Merged config=${JsonUtils.printToJsonString(config)}")
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

Map runJobStage(Map stageConfig) {
    String logPrefix="runJobStage(${stageConfig.stage}):"

    Map jobResults = [:]
    boolean result = false

    log.info("${logPrefix} stageConfig=${JsonUtils.printToJsonString(stageConfig)}")

    stage(stageConfig.stage) {
        log.info("${logPrefix} starting stage")

        List resultList = jobResults.values()
        boolean result = (resultList.size()>0) ? resultList.inject { a, b -> a && b } : true

        log.debug("${logPrefix} jobResults=${jobResults}")
        log.debug("${logPrefix} resultList=${resultList}")
        log.debug("${logPrefix} prior stage results=${result}")
        if (stageConfig?.jobList || stageConfig?.jobs) {
            if (result || stageConfig.continueIfFailed) {
                result = runJobList(stageConfig)
                jobResults[stageConfig.stage] = result

                if (!result && !stageConfig.continueIfFailed) {
                    currentBuild.result = 'FAILURE'
                }
                log.info("${logPrefix} finishing stage: result=${result}")
            } else {
                log.info("${logPrefix} skipping stage: prior stage FAILURE results")
            }
        }
    }
//     return result
    return jobResults
}

boolean runJobList(Map baseJobConfigs) {

    String logPrefix="runJobList():"
    log.info("${logPrefix} started")
    log.debug("${logPrefix} baseJobConfigs=${JsonUtils.printToJsonString(baseJobConfigs)}")

    List jobList = []
    if (baseJobConfigs?.jobList) {
        jobList = baseJobConfigs.jobList
    } else if (baseJobConfigs?.jobs) {
        jobList = baseJobConfigs.jobs
    }

    if (jobList.size()==0) {
        log.error("${logPrefix} no jobs specified")
        return false
    }

    Map parallelJobs = [:]
    List jobResults = []

    jobList.eachWithIndex { jobConfigsRaw, i ->

        log.info("${logPrefix} i=${i} jobConfigsRaw=${JsonUtils.printToJsonString(jobConfigsRaw)}")

        // job configs overlay parent settings
        Map jobConfigs = baseJobConfigs.findAll { !["jobList","jobs","stage"].contains(it.key) } + jobConfigsRaw
        jobConfigs.jobId = createJobId(baseJobConfigs, i)

        log.info("${logPrefix} i=${i} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        if (jobConfigs?.stage) {
            runJobStage(jobConfigs)
        } else {
            if (jobConfigs?.jobList || jobConfigs?.jobs) {
                jobResults.add(runJobList(jobConfigs))
            }
        }

        if (jobConfigs?.jobFolder || jobConfigs?.job) {
            log.debug("${logPrefix} i=${i} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

            if (jobConfigs?.runInParallel) {
                parallelJobs["split-${jobConfigs.jobId}"] = {
                    jobResults.add(runJob(jobConfigs))
                }
            } else {
                jobResults.add(runJob(jobConfigs))
            }
        } else {
            jobResults.add(false)
            log.error("${logPrefix} job not specified")
//            return result
        }

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        //boolean result = (false in jobResults) ? false : true
        boolean result = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
        if (!jobConfigs.continueIfFailed && jobResults.size()>0 && !result) {
            currentBuild.result = 'FAILURE'
            log.info("${logPrefix} i=${i} continueIfFailed is false and results failed - not running any more jobs")
//            return result
        }

    }

    if (parallelJobs.size()>0) {
        log.info("${logPrefix} parallelJobs=${parallelJobs}")
        parallel parallelJobs
    }

    boolean result = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
    if (!baseJobConfigs.continueIfFailed && jobResults.size()>0 && !result) {
        currentBuild.result = 'FAILURE'
        log.info("${logPrefix} continueIfFailed is false and results failed - not running any more jobs")
    }

    log.info("${logPrefix} finished: result = ${result}")
    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    //return (false in jobResults) ? false : true
    return result
//     return jobResults
}

boolean runJob(Map jobConfigs) {

    String logPrefix="runJob():"
    log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    if (!jobConfigs?.jobFolder && !jobConfigs?.job) {
        log.error("${logPrefix} 'jobFolder' or 'job' must be specified")
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

    jobConfigs.each { key, value ->
        if (key in jobConfigs.supportedJobParams) {
            paramList.add([$class: 'StringParameterValue', name: key, value: value])
        }
    }

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

