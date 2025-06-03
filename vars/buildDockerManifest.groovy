#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
// import com.dettonville.api.pipeline.utils.DockerUtil

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
// @Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

// ref: https://blog.nimbleci.com/2016/08/31/how-to-build-docker-images-automatically-with-jenkins-pipeline/
// ref: https://mike42.me/blog/2019-05-how-to-integrate-gitea-and-jenkins
// ref: https://github.com/jenkinsci/pipeline-examples/pull/83/files
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

def call(Map params=[:]) {

    Map config=loadPipelineConfig(params)
//     boolean jobResults = true
    Map jobResults = [:]

    pipeline {

        agent {
//             label "docker-in-docker"
            label "docker"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            disableConcurrentBuilds()
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }

        stages {
			stage('Load docker build config file') {
				when {
                    expression { fileExists config.configFile }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
					}
				}
			}
            stage("Run Docker Build Manifest") {
                steps {
                    script {
                        jobResults = runDockerBuildManifest(config)
                        log.info("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
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
                        sendEmail(currentBuild, env, emailAdditionalDistList: [config.alwaysEmailList.split(",")])
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
                        sendEmail(currentBuild, env, emailAdditionalDistList: [config.successEmailList.split(",")])
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: [config.failedEmailList.split(",")])
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: [config.failedEmailList.split(",")])
                    }
                }
            }
            changed {
                script {
                    if (config?.changedEmailList) {
                        log.info("config.changedEmailList=${config.changedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: [config.changedEmailList.split(",")])
                    }
                }
            }
        }
    }
}

//@NonCPS
Map loadPipelineConfig(Map params) {

    Map config = [:]

    if (params) {
        log.debug("copy immutable params map to mutable config map")
        log.debug("params=${JsonUtils.printToJsonString(params)}")
        config = MapMerge.merge(config, params)
    }

    config.logLevel = config.get('logLevel', "INFO")
//    config.logLevel = config.get('logLevel', "DEBUG")
    config.timeout = config.get('timeout', "5")
    config.timeoutUnit = config.get('timeoutUnit', "HOURS")
    config.debugPipeline = config.get('debugPipeline', false)
    config.continueIfFailed = config.get('continueIfFailed', false)
    config.failFast = config.get('failFast', false)
    config.propagate = config.get('propagate', config.failFast)

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
    log.debug("buildDate=${buildDate}")

//     String buildId = "${env.BUILD_NUMBER}"
    String buildId = "build-${env.BUILD_NUMBER}"
    log.debug("buildId=${buildId}")

    config.get("buildId", buildId)
    config.get("buildDate", buildDate)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    config.runGroupsInParallel = config.get('runGroupsInParallel', false)
    config.runInParallel = config.get('runInParallel', false)
    config.parallelJobsBatchSize = config.get('parallelJobsBatchSize', 0)

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List dockerEnvVarsListDefault = [
        "BUILDX_CONFIG=/home/jenkins/.docker/buildx"
    ]
    config.dockerEnvVarsList = config.get('dockerEnvVarsList', dockerEnvVarsListDefault)

    log.info("config=${JsonUtils.printToJsonString(config)}")

    config.configFile = config.get('configFile', ".jenkins/docker-build-config.yml")

    return config
}

Map loadPipelineConfigFile(Map config) {

    Map dockerBuildConfigMap = readYaml file: config.configFile

    Map buildConfigs=dockerBuildConfigMap.pipeline
    log.debug("buildConfigs=${JsonUtils.printToJsonString(buildConfigs)}")

    config = config + buildConfigs

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

Map runDockerBuildManifest(Map config) {

    Map jobResults
    if (config?.buildImageGroups) {
        jobResults = buildAndPublishImageGroups(config)
    } else if (config?.buildImageList) {
        jobResults = buildAndPublishImageList(config)
    } else {
        jobResults = runBuildAndPublishImageJob(config)
    }
    log.debug("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    if (jobResults.failed && (config.failFast || !config.continueIfFailed)) {
        currentBuild.result = 'FAILURE'
        log.debug("config.continueIfFailed=${config.continueIfFailed}")
        log.debug("config.failFast=${config.failFast}")
        log.error("results failed - not running any more jobs")
    }

    return jobResults
}

Map buildAndPublishImageGroups(Map config) {

    Map parallelGroups = [:]

    Map jobResults = [:]
    jobResults.groups = [:]
    config.buildImageGroups.each { groupName, groupConfigRaw ->

        log.debug("groupName=${groupName} groupConfigRaw=${JsonUtils.printToJsonString(groupConfigRaw)}")

        // job configs overlay parent settings
        Map groupConfig = config.findAll { !["buildImageGroups","groups"].contains(it.key) } + groupConfigRaw
        groupConfig.groupName = groupName
        String stageName = "build group ${groupName}"
        log.debug("groupName=${groupName} groupConfig=${JsonUtils.printToJsonString(groupConfig)}")

        if (config?.runGroupsInParallel && config.runGroupsInParallel.toBoolean()) {
            parallelGroups["group-${groupConfig.groupName}"] = {
                jobResults.groups.put(groupConfig.groupName, runDockerBuildManifest(groupConfig))
            }
        } else {
            stage("${stageName}") {
                jobResults.groups.put(groupConfig.groupName, runDockerBuildManifest(groupConfig))
            }
        }
    }

    if (parallelGroups.size()>0) {
        log.debug("parallelGroups=${parallelGroups}")
        parallel parallelGroups
    }

    log.debug("finished: jobResult=${jobResult}")
    return jobResult
}

// boolean buildAndPublishImageList(Map config) {
Map buildAndPublishImageList(Map config) {

    Map parallelJobs = [:]
    Map jobResults = [:]
    jobResults.items = [:]
    config.buildImageList.each { Map buildConfigRaw ->
        log.debug("buildConfigRaw=${JsonUtils.printToJsonString(buildConfigRaw)}")

        Map buildConfig = config.findAll { !["buildImageList"].contains(it.key) } + buildConfigRaw

        log.debug("buildConfig=${JsonUtils.printToJsonString(buildConfig)}")

        if (config?.runInParallel && config.runInParallel.toBoolean()) {
            parallelJobs["split-${buildConfig.buildImageLabel}"] = {
                jobResults.items.put(buildConfig.buildImageLabel, runDockerBuildManifest(buildConfig))
            }
        } else {
            stage("build and publish ${buildConfig.buildImageLabel}") {
                jobResults.items.put(buildConfig.buildImageLabel, runDockerBuildManifest(buildConfig))
            }
        }
    }

    if (parallelJobs.size()>0) {
        log.debug("parallelJobs=${parallelJobs}")
        if (config?.parallelJobsBatchSize && config.parallelJobsBatchSize>0) {
            log.debug("config.parallelJobsBatchSize=${config.parallelJobsBatchSize}")
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
            log.debug("parallelJobs=${parallelJobs}")
            parallel parallelJobs
        }
    }

    log.debug("jobResults=${JsonUtils.printToJsonString(jobResults)}")

    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
    boolean failed = (jobResults.items.size()>0) ? jobResults.items.inject(true) { a, k, v -> a && v.failed } : false
    jobResults.failed = failed

    log.debug("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    return jobResults
}

// boolean runBuildAndPublishImageJob(Map config) {
Map runBuildAndPublishImageJob(Map config) {
    Map jobResults = [:]
    String logPrefix = "[${config.buildImageLabel}]:"

    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    String gitCommitId = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
//     sh "git rev-parse HEAD > .git/commit-id"
//     String gitCommitId = readFile('.git/commit-id').trim()

    log.debug("${logPrefix} gitCommitId=${gitCommitId}")
    jobResults.gitCommitId = gitCommitId

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
    // source for 'build-docker-image' job referenced in following 'jobFolder' located in buildDockerImage.groovy
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
//     log.debug("${logPrefix} scm.userRemoteConfigs=${JsonUtils.printToJsonString(scm.userRemoteConfigs)}")
//     log.debug("${logPrefix} scm.userRemoteConfigs[0].url=${scm.userRemoteConfigs[0].url}")
//     jobConfigs.gitRepoUrl = scm.userRemoteConfigs[0].url
//     // ref: https://stackoverflow.com/questions/38254968/how-do-i-get-the-scm-url-inside-a-jenkins-pipeline-or-multibranch-pipeline
// //     jobConfigs.gitRepoUrl = scm.getUserRemoteConfigs()[0].getUrl()
//
//     // ref: https://stackoverflow.com/questions/42383273/get-git-branch-name-in-jenkins-pipeline-jenkinsfile
//     log.debug("${logPrefix} scm.branches=${JsonUtils.printToJsonString(scm.branches)}")
//     log.debug("${logPrefix} scm.branches[0].name=${scm.branches[0].name}")
//     jobConfigs.gitRepoBranch = scm.branches[0].name
// //     jobConfigs.gitRepoBranch = scm.branches.first().getExpandedName(env.getEnvironment())

    log.debug("${logPrefix} GIT_URL=${GIT_URL}")
    log.debug("${logPrefix} GIT_BRANCH=${GIT_BRANCH}")

    Map jobParameters = [:]
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

    jobResults.jobParameters = jobParameters

    log.info("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
    Map jobResult = runJob(jobConfigs)
    log.info("${logPrefix} jobResult=${JsonUtils.printToJsonString(jobResult)}")
    jobResults << jobResult

    if (jobResults.failed && (config.failFast || !config.continueIfFailed)) {
        currentBuild.result = 'FAILURE'
        log.debug("${logPrefix} config.continueIfFailed=${config.continueIfFailed}")
        log.debug("${logPrefix} config.failFast=${config.failFast}")
        log.error("${logPrefix} results failed - not running any more jobs")
    }

    log.debug("${logPrefix} finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    return jobResults
}
