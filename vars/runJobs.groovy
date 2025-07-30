#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

// ref: https://blog.nimbleci.com/2016/08/31/how-to-build-docker-images-automatically-with-jenkins-pipeline/
// ref: https://mike42.me/blog/2019-05-how-to-integrate-gitea-and-jenkins
// ref: https://github.com/jenkinsci/pipeline-examples/pull/83/files
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)

    pipeline {

        agent any
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
//            overrideIndexTriggers(false)
            skipDefaultCheckout()
            disableConcurrentBuilds()
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }

        stages {
			stage('Load job config file') {
				when {
                    expression { fileExists config.configFile }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
					}
				}
			}
            stage("Run Jobs") {
                steps {
                    script {
                        Map jobResults = runJobStage(config)
                        log.info("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
                        dir(config.buildReportDir) {
                            log.info("dir: ${config.buildReportDir} successfully created!")
                        }
                        log.info("saving ${config.buildReport}")
                        try {
                            // ref: https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#writeyaml-write-a-yaml-from-an-object-or-objects
                            writeYaml file: config.buildReport, data: jobResults
                        } catch (Exception err) {
                            log.error("writeFile(${config.buildReport}): exception occurred [${err}]")
                        }

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: "${config.buildReportDir}/**",
                            fingerprint: true)

                        if (jobResults.failed) {
                            currentBuild.result = 'FAILURE'
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (config?.alwaysEmailList) {
                        log.info("config.alwaysEmailList=${config.alwaysEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.alwaysEmailList.split(","))
                    } else {
                        sendEmail(currentBuild, env)
                    }
                    log.info("Empty current workspace dir")
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }
            success {
                script {
                    if (config?.successEmailList) {
                        log.info("config.successEmailList=${config.successEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.successEmailList.split(","))
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            changed {
                script {
                    if (config?.changedEmailList) {
                        log.info("config.changedEmailList=${config.changedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.changedEmailList.split(","))
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
Map loadPipelineConfig(Map params) {

    Map config = [:]
    config.supportedJobParams = ['changedEmailList','alwaysEmailList','failedEmailList']
    config.gitBranch = env.GIT_BRANCH

    if (configFile != null && fileExists(configFile)) {
        Map configSettings = readYaml file: "${configFile}"
//        config=config + configSettings.pipeline
        config = MapMerge.merge(configSettings.pipeline, config)
    }

    if (params) {
        log.debug("copy immutable params map to mutable config map")
        log.debug("params=${JsonUtils.printToJsonString(params)}")
        config = MapMerge.merge(config, params)
//         params.each { key, value ->
//             log.debug("params[${key}]=${value}")
//             key=decapitalize(key)
//             if (value!="") {
//                 config[key]=value
//             }
//         }
    }

    config.get('jenkinsJobNodeNodeLabel',"any")

    config.get('logLevel', "INFO")
    config.get('timeout', "5")
    config.get('timeoutUnit', "HOURS")
    config.get('debugPipeline', false)
    config.get('continueIfFailed', false)
    config.get('wait', true)
    config.get('failFast', false)
    config.get('propagate', config.failFast)

    config.get('childJobTimeout', "4")
    config.get('childJobTimeoutUnit', "HOURS")

    config.get('runGroupsInParallel', false)
    config.get('runInParallel', false)
    config.get('parallelJobsBatchSize', 0)

    config.get('stage', 'runJobs-stage')

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    log.debug("params=${JsonUtils.printToJsonString(params)}")
    log.info("config=${JsonUtils.printToJsonString(config)}")

    config.get('configFile', ".jenkins/run-jobs-config.yml")

    return config
}

Map loadPipelineConfigFile(Map baseConfig) {

    Map configFileMap = readYaml file: baseConfig.configFile
    log.debug("configFileMap=${JsonUtils.printToJsonString(configFileMap)}")

    Map config = MapMerge.merge(baseConfig, configFileMap)

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
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
    String logPrefix="[${stageConfig.stage}]:"

    Map jobResults = [:]
    jobResults.failed = false

    log.info("${logPrefix} stageConfig=${JsonUtils.printToJsonString(stageConfig)}")

    stage(stageConfig.stage) {
        log.info("${logPrefix} starting stage")

        if (stageConfig?.jobList || stageConfig?.jobs) {
            if (result || stageConfig.continueIfFailed) {
//                 result = runJobList(stageConfig)
//                 jobResults[stageConfig.stage] = result
                jobResults.items.put(stageConfig.stage, runJobList(buildConfig))

                // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
                // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
                boolean failed = (jobResults.items.size()>0) ? jobResults.items.inject(false) { a, k, v -> a || v.failed } : false
                jobResults.failed = failed

                if (jobResults.failed && (config.failFast || !config.continueIfFailed)) {
                    log.debug("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
                    log.debug("${logPrefix} config.failFast=${config.failFast}")
                    log.error("${logPrefix} results failed - not running any more jobs")
                    currentBuild.result = 'FAILURE'
                }

//                 if (!result && !stageConfig.continueIfFailed) {
//                     currentBuild.result = 'FAILURE'
//                 }
                log.info("${logPrefix} finishing stage: result=${result}")
            } else {
                log.info("${logPrefix} skipping stage: prior stage FAILURE results")
            }
        }
    }
//     return result
    return jobResults
}

void runParallelJobs(Map config, Map parallelJobs) {

    if (parallelJobs.size()>0) {

        if (config?.parallelJobsBatchSize && config.parallelJobsBatchSize>0) {
            log.debug("config.parallelJobsBatchSize=${config.parallelJobsBatchSize}")
            Integer batchSize = config.parallelJobsBatchSize
            Map parallelJobsBatch = [:]
            for (String key : parallelJobs.keySet()) {
                parallelJobsBatch.put(key, parallelJobs.get(key))
                if (config.failFast) {
                    parallelJobsBatch.failFast = config.failFast
                }
                if (batchSize-- == 1) {
                    parallel parallelJobsBatch
                    parallelJobsBatch = [:]
                }
            }
            if ((parallelJobsBatch.keySet()).size()> 1) {
                log.debug("Running parallelJobsBatch")
                parallel parallelJobsBatch
            }
        } else {
            if (config.failFast) {
                parallelJobs.failFast = config.failFast
            }
            log.debug("Running parallelJobs")
            parallel parallelJobs
        }
    }

}

Map runJobList(Map config) {

    List jobList = []
    if (config?.jobList) {
        jobList = config.jobList
    } else if (config?.jobs) {
        jobList = config.jobs
    }

    if (jobList.size()==0) {
        log.error("no jobs specified")
        return false
    }

    Map parallelJobs = [:]
    Map jobResults = [:]
    jobResults.items = [:]

    jobList.eachWithIndex { jobConfigsRaw, i ->

        log.info("i=${i} jobConfigsRaw=${JsonUtils.printToJsonString(jobConfigsRaw)}")

        // job configs overlay parent settings
        Map jobConfigs = config.findAll { !["jobList","jobs","stage"].contains(it.key) } + jobConfigsRaw
        jobConfigs.jobId = createJobId(config, i)

        log.info("i=${i} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        if (jobConfigs?.stage) {
            jobResults.items.put(jobConfigs.jobId, runJobStage(jobConfigs))
        } else {
            if (jobConfigs?.jobList || jobConfigs?.jobs) {
                jobResults.items.put(jobConfigs.jobId, runJobList(jobConfigs))
            }
        }

        if (jobConfigs?.jobFolder || jobConfigs?.jobName) {
            log.debug("i=${i} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

            if (jobConfigs?.runInParallel && jobConfigs.runInParallel) {
                parallelJobs["split-${jobConfigs.jobId}"] = {
                    jobResults.items.put(jobConfigs.jobId, runJob(jobConfigs))
                }
            } else {
                jobResults.items.put(jobConfigs.jobId, runJob(jobConfigs))
            }
        }

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
        jobResults.failed = (jobResults.items.size()>0) ? jobResults.items.inject(false) { a, k, v -> a || v.failed } : false
        jobResults.failed = failed

        log.debug("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
        if (jobResults.failed && (config.failFast || !config.continueIfFailed)) {
            log.debug("config.continueIfFailed=${config.continueIfFailed}")
            log.debug("config.failFast=${config.failFast}")
            log.error("results failed - not running any more jobs")
            currentBuild.result = 'FAILURE'
        }

    }

    if (parallelJobs.size()>0) {
        log.info("Running parallelJobs")
        runParallelJobs(config, parallelJobs)
    }

    log.debug("jobResults=${JsonUtils.printToJsonString(jobResults)}")

    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
    boolean failed = (jobResults.items.size()>0) ? jobResults.items.inject(false) { a, k, v -> a || v.failed } : false
    jobResults.failed = failed

    log.debug("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    return jobResults
}
