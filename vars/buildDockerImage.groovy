#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
// import com.dettonville.api.pipeline.utils.DockerUtil

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)
@Field List jobResults = []

// ref: https://blog.nimbleci.com/2016/08/31/how-to-build-docker-images-automatically-with-jenkins-pipeline/
// ref: https://mike42.me/blog/2019-05-how-to-integrate-gitea-and-jenkins
// ref: https://github.com/jenkinsci/pipeline-examples/pull/83/files
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

def call(Map params=[:]) {

// //    Logger.init(this, LogLevel.INFO)
//     Logger log = new Logger(this, LogLevel.INFO)

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)
    boolean jobResults = true

    pipeline {

        agent {
//             label "docker-in-docker"
            label "docker"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            disableConcurrentBuilds()
//             skipDefaultCheckout()
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }

        stages {
			stage('Load docker build config file') {
				when {
                    expression { fileExists config.buildConfigFile }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
					}
				}
			}
            stage("Build and Publish Docker Image") {
                steps {
                    script {
                        jobResults = buildAndPublishImages(config)
                        log.info("jobResults=${jobResults}")
                        if (!jobResults) {
                            currentBuild.result = 'FAILURE'
                        }
                    }
                }
            }
        }
        post {
//             always {
//                 script {
//                     sendEmail(currentBuild, env)
//                 }
//             }
            always {
                script {
                    if (config?.alwaysEmailList) {
                        log.info("config.alwaysEmailList=${config.alwaysEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList=[config.alwaysEmailList.split(",")])
                    } else {
                        sendEmail(currentBuild, env)
                    }
                    log.info("Empty current workspace dir")
                    cleanWs()
                }
            }
            success {
                script {
                    if (config?.successEmailList) {
                        log.info("config.successEmailList=${config.successEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList=[config.successEmailList.split(",")])
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList=[config.failedEmailList.split(",")])
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList=[config.failedEmailList.split(",")])
                    }
                }
            }
            changed {
                script {
                    if (config?.changedEmailList) {
                        log.info("config.changedEmailList=${config.changedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList=[config.changedEmailList.split(",")])
                    }
                }
            }
        }
    }
}

//@NonCPS
Map loadPipelineConfig(Map params) {
    String logPrefix = "loadPipelineConfig():"
    Map config = [:]

    if (params) {
        log.info("${logPrefix} copy immutable params map to mutable config map")
        log.info("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
        config = MapMerge.merge(config, params)
    }

//    config.logLevel = config.get('logLevel', "INFO")
    config.logLevel = config.get('logLevel', "DEBUG")
    config.timeout = config.get('timeout', "5")
    config.timeoutUnit = config.get('timeoutUnit', "HOURS")
    config.debugPipeline = config.get('debugPipeline', false)
    config.continueIfFailed = config.get('continueIfFailed', false)
    config.failFast = config.get('failFast', false)

    config.childJobTimeout = config.get('childJobTimeout', "4")
    config.childJobTimeoutUnit = config.get('childJobTimeoutUnit', "HOURS")

//     config.get("gitCredentialsId", "bitbucket-ssh-jenkins")
    config.get("gitCredentialsId", "infra-jenkins-git-user")

//    config.get("registryUrl","https://registry.media.johnson.int:5000")
    config.get("registryUrl","https://media.johnson.int:5000")
//    config.get("registryUrl","https://media.dettonville.int:5000")
    config.get("registryCredId", "docker-registry-admin")

    config.get("buildImageLabel", "${env.JOB_NAME.split('/')[-2]}")
    config.get("buildDir", ".")
    config.get("buildPath", ".")

//    config.get("buildImageList", [[ buildDir: ".", buildImageLabel: "${env.JOB_BASE_NAME}"]])
//     config.get("buildImageList", [[ buildDir: config.buildDir, buildImageLabel: config.buildImageLabel]])

    // ref: https://stackoverflow.com/questions/40261710/getting-current-timestamp-in-inline-pipeline-script-using-pipeline-plugin-of-hud
    Date now = new Date()

    String buildDate = now.format("yyyy-MM-dd", TimeZone.getTimeZone('UTC'))
    log.info("buildDate=${buildDate}")

//     String buildId = "${env.BUILD_NUMBER}"
    String buildId = "build-${env.BUILD_NUMBER}"
    log.info("buildId=${buildId}")

    config.get("buildId", buildId)
    config.get("buildDate", buildDate)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.info("${logPrefix} log.level=${log.level}")

    config.runGroupsInParallel = config.get('runGroupsInParallel', false)
    config.runInParallel = config.get('runInParallel', false)
    config.parallelJobsBatchSize = config.get('parallelJobsBatchSize', 0)

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List dockerEnvVarsListDefault = [
        "BUILDX_CONFIG=/home/jenkins/.docker/buildx"
    ]
    config.dockerEnvVarsList = config.get('dockerEnvVarsList', dockerEnvVarsListDefault)

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    config.buildConfigFile = config.get('buildConfigFile', ".jenkins/docker-build-config.yml")

    return config
}

Map loadPipelineConfigFile(Map config) {
    String logPrefix="loadPipelineConfigFile():"

    Map dockerBuildConfigMap = readYaml file: config.buildConfigFile

    Map buildConfigs=dockerBuildConfigMap.pipeline
    log.info("${logPrefix} buildConfigs=${JsonUtils.printToJsonString(buildConfigs)}")

    config = config + buildConfigs

    log.info("${logPrefix} Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

boolean buildAndPublishImages(Map config) {
    String logPrefix = "buildAndPublishImages():"

    boolean jobResult
    if (config?.buildImageGroups) {
        jobResult = buildAndPublishImageGroups(config)
    } else if (config?.buildImageList) {
        jobResult = buildAndPublishImageList(config)
    } else {
//         List buildImageListDefault = [[ buildDir: config.buildDir, buildImageLabel: config.buildImageLabel]]
//         config.get("buildImageList", buildImageListDefault)
//         buildAndPublishImageList(config)
        jobResult = runBuildAndPublishImageJob(config)
    }
//     if (!jobResult && (config.failFast || !config.continueIfFailed)) {
//         currentBuild.result = 'FAILURE'
//         log.info("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
//         log.info("${logPrefix} config.failFast=${config.failFast}")
//         log.info("${logPrefix} results failed - not running any more jobs")
//     }

    return jobResult
}

boolean buildAndPublishImageGroups(Map config) {
    String logPrefix = "buildAndPublishImageGroups():"
    Map parallelGroups = [:]
//     List jobResults = []

    boolean jobResult
    config.buildImageGroups.each { groupName, groupConfigRaw ->

        log.info("${logPrefix} groupName=${groupName} groupConfigRaw=${JsonUtils.printToJsonString(groupConfigRaw)}")

        // job configs overlay parent settings
        Map groupConfig = config.findAll { !["buildImageGroups","groups"].contains(it.key) } + groupConfigRaw
        groupConfig.groupName = groupName
        String stageName = "build group ${groupName}"
        log.info("${logPrefix} groupName=${groupName} groupConfig=${JsonUtils.printToJsonString(groupConfig)}")

        if (config?.runGroupsInParallel && config.runGroupsInParallel.toBoolean()) {
            parallelGroups["split-${groupConfig.groupName}"] = {
                jobResult = buildAndPublishImages(groupConfig)
            }
        } else {
            stage("${stageName}") {
                jobResult = buildAndPublishImages(groupConfig)
            }
        }
    }

    if (parallelGroups.size()>0) {
        log.info("${logPrefix} parallelGroups=${parallelGroups}")
        parallel parallelGroups
    }

//     // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
// //     boolean jobResult = (false in jobResults) ? false : true
// //     boolean jobResult = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
//     if (!jobResult && (config.failFast || !config.continueIfFailed)) {
//         currentBuild.result = 'FAILURE'
//         log.info("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
//         log.info("${logPrefix} config.failFast=${config.failFast}")
//         log.info("${logPrefix} results failed - not running any more jobs")
//     }

    log.info("${logPrefix} finished: jobResult=${jobResult}")
    return jobResult
}

boolean buildAndPublishImageList(Map config) {
    String logPrefix = "buildAndPublishImageList():"
    Map parallelJobs = [:]
//     List jobResults = []
    config.buildImageList.each { Map buildConfigRaw ->
        log.debug("${logPrefix} buildConfigRaw=${JsonUtils.printToJsonString(buildConfigRaw)}")

        Map buildConfig = config.findAll { !["buildImageList"].contains(it.key) } + buildConfigRaw

        log.info("${logPrefix} buildConfig=${JsonUtils.printToJsonString(buildConfig)}")

        if (config?.runInParallel && config.runInParallel.toBoolean()) {
            parallelJobs["split-${buildConfig.buildImageLabel}"] = {
                jobResults.add(buildAndPublishImages(buildConfig))
//                 boolean jobResult = runBuildAndPublishImageJob(buildConfig)
//                 jobResults.add(jobResult)
//                 if (!jobResult && config.failFast) {
//                     currentBuild.result = 'FAILURE'
//                     log.info("${logPrefix} results failed and config.failFast is set - stopping immediately")
//                     return jobResult
//                 }
            }
        } else {
            stage("build and publish ${buildConfig.buildImageLabel}") {
                jobResults.add(buildAndPublishImages(buildConfig))
//                 boolean jobResult = runBuildAndPublishImageJob(buildConfig)
//                 jobResults.add(jobResult)
//                 if (!jobResult && config.failFast) {
//                     currentBuild.result = 'FAILURE'
//                     log.info("${logPrefix} results failed and config.failFast is set - stopping immediately")
//                     return jobResult
//                 }
            }
        }
    }

    if (parallelJobs.size()>0) {
        log.info("${logPrefix} parallelJobs=${parallelJobs}")
        if (config?.parallelJobsBatchSize && config.parallelJobsBatchSize>0) {
            log.info("${logPrefix} config.parallelJobsBatchSize=${config.parallelJobsBatchSize}")
            Integer batchSize = config.parallelJobsBatchSize
            Map parallelJobsBatch = [:]
            for (String key : parallelJobs.keySet()) {
                parallelJobsBatch.put(key, parallelJobs.get(key))
                if (batchSize-- == 1) {
                    parallel parallelJobsBatch
                    parallelJobsBatch = [:]
                }
            }
            if ((parallelJobsBatch.keySet()).size()> 1) {
                parallel parallelJobsBatch
            }
        } else {
            log.info("${logPrefix} parallelJobs=${parallelJobs}")
            parallel parallelJobs
        }
    }

    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    //boolean jobResult = (false in jobResults) ? false : true
    boolean jobResult = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
//     if (!jobResult && (config.failFast || !config.continueIfFailed)) {
//         currentBuild.result = 'FAILURE'
//         log.info("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
//         log.info("${logPrefix} config.failFast=${config.failFast}")
//         log.info("${logPrefix} results failed - not running any more jobs")
//     }
    log.info("${logPrefix} finished: jobResult=${jobResult}")
    return jobResult
}

boolean runBuildAndPublishImageJob(Map config) {
    String logPrefix="runBuildAndPublishImageJob(${config.buildImageLabel}):"
    log.info("${logPrefix} started")

    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    boolean priorJobResults = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
    if (!priorJobResults && (config.failFast || !config.continueIfFailed)) {
        currentBuild.result = 'FAILURE'
        log.info("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
        log.info("${logPrefix} config.failFast=${config.failFast}")
        log.info("${logPrefix} current results are FAILED - not running any more jobs")
        return priorJobResults
    }

    String gitCommitId = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
//     sh "git rev-parse HEAD > .git/commit-id"
//     String gitCommitId = readFile('.git/commit-id').trim()

    log.info("${logPrefix} gitCommitId=${gitCommitId}")

//    DockerUtil dockerUtil = new DockerUtil(this)
    Map buildArgs = [:]
    if (config.buildArgs) {
        config.buildArgs.each { key, value ->
            buildArgs[key] = value
        }
        if (!config.buildArgs?.BUILD_ID) {
            buildArgs['BUILD_ID'] = config.buildId
        }
        if (!config.buildArgs?.BUILD_DATE) {
            buildArgs['BUILD_DATE'] = config.buildDate
        }
    } else {
        buildArgs['BUILD_ID'] = config.buildId
        buildArgs['BUILD_DATE'] = config.buildDate
    }
    log.info("${logPrefix} buildArgs=${JsonUtils.printToJsonString(buildArgs)}")

    Map jobConfigs = [:]
    jobConfigs.jobFolder = "INFRA/build-docker-image"
    jobConfigs.wait = true
    jobConfigs.supportedJobParams = [
        "GitRepoUrl",
        "GitRepoBranch",
        "GitCredentialsId",
        "RegistryUrl",
        "RegistryCredId",
        "BuildImageLabel",
        "BuildDir",
        "BuildPath",
        "BuildArgs",
        "DockerFile",
        "ChangedEmailList",
        "AlwaysEmailList",
        "FailedEmailList",
        "Timeout",
        "TimeoutUnits"
    ]

//     // ref: https://stackoverflow.com/questions/45937337/jenkins-pipeline-get-repository-url-variable-under-pipeline-script-from-scm
//     log.info("${logPrefix} scm.userRemoteConfigs=${JsonUtils.printToJsonString(scm.userRemoteConfigs)}")
//     log.info("${logPrefix} scm.userRemoteConfigs[0].url=${scm.userRemoteConfigs[0].url}")
//     jobConfigs.gitRepoUrl = scm.userRemoteConfigs[0].url
//     // ref: https://stackoverflow.com/questions/38254968/how-do-i-get-the-scm-url-inside-a-jenkins-pipeline-or-multibranch-pipeline
// //     jobConfigs.gitRepoUrl = scm.getUserRemoteConfigs()[0].getUrl()
//
//     // ref: https://stackoverflow.com/questions/42383273/get-git-branch-name-in-jenkins-pipeline-jenkinsfile
//     log.info("${logPrefix} scm.branches=${JsonUtils.printToJsonString(scm.branches)}")
//     log.info("${logPrefix} scm.branches[0].name=${scm.branches[0].name}")
//     jobConfigs.gitRepoBranch = scm.branches[0].name
// //     jobConfigs.gitRepoBranch = scm.branches.first().getExpandedName(env.getEnvironment())

    log.info("${logPrefix} GIT_URL=${GIT_URL}")
    log.info("${logPrefix} GIT_BRANCH=${GIT_BRANCH}")

    jobParameters = [:]
    jobParameters.Timeout = config.childJobTimeout
    jobParameters.TimeoutUnits = config.childJobTimeoutUnit

    jobParameters.GitRepoUrl = GIT_URL
    jobParameters.GitRepoBranch = GIT_BRANCH
    jobParameters.GitCredentialsId = config.gitCredentialsId

    jobParameters.RegistryUrl = config.registryUrl
    jobParameters.RegistryCredId = config.registryCredId

    jobParameters.BuildImageLabel = config.buildImageLabel
    jobParameters.BuildDir = config.buildDir
    jobParameters.BuildPath = config.buildPath
    if (buildArgs) {
//         jobParameters.BuildArgs = JsonUtils.printToJsonString(buildArgs)
        jobParameters.BuildArgs = JsonOutput.toJson(buildArgs)
    }
    if (config?.dockerFile) {
        jobParameters.DockerFile = config.dockerFile
    }
    jobConfigs.jobParameters = jobParameters

    log.info("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
    boolean jobResult = runJob(jobConfigs)

    if (!jobResult && (config.failFast || !config.continueIfFailed)) {
        currentBuild.result = 'FAILURE'
        log.info("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
        log.info("${logPrefix} config.failFast=${config.failFast}")
        log.info("${logPrefix} results failed - not running any more jobs")
    }

    log.info("${logPrefix} runJob(): jobResult=${jobResult}")
    return jobResult
}
