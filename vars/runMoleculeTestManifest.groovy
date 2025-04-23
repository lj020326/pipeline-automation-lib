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
@Field List jobResults = []

def call(Map params=[:]) {

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)
    boolean jobResults = true

    pipeline {

        agent {
            label "docker"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            disableConcurrentBuilds()
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }

        stages {
			stage('Load molecule config file') {
				when {
                    expression { fileExists config.configFile }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
					}
				}
			}
            stage("Run Molecule Manifest") {
                steps {
                    script {
                        jobResults = runMoleculeManifest(config)
                        log.info("jobResults=${jobResults}")
                        if (!jobResults) {
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
                    cleanWs()
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
    String logPrefix = "loadPipelineConfig():"
    Map config = [:]

    if (params) {
        log.info("${logPrefix} copy immutable params map to mutable config map")
        log.info("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
        config = MapMerge.merge(config, params)
    }

    config.logLevel = config.get('logLevel', "INFO")
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

    config.configFile = config.get('configFile', ".jenkins/molecule-test-config.yml")

    return config
}

Map loadPipelineConfigFile(Map config) {
    String logPrefix="loadPipelineConfigFile():"

    Map moleculeRunConfigMap = readYaml file: config.configFile

    Map moleculeConfigs=moleculeRunConfigMap.pipeline
    log.info("${logPrefix} moleculeConfigs=${JsonUtils.printToJsonString(moleculeConfigs)}")

    config = config + moleculeConfigs

    log.info("${logPrefix} Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

boolean runMoleculeManifest(Map config) {
    String logPrefix = "runMoleculeManifest():"

    boolean jobResult
    if (config?.moleculeImageGroups) {
        jobResult = runMoleculeImageGroups(config)
    } else if (config?.moleculeImageList) {
        jobResult = runMoleculeImageList(config)
    } else {
        jobResult = runMoleculeJob(config)
    }

    return jobResult
}

boolean runMoleculeImageGroups(Map config) {
    String logPrefix = "runMoleculeImageGroups():"
    Map parallelGroups = [:]
//     List jobResults = []

    boolean jobResult
    config.moleculeImageGroups.each { groupName, groupConfigRaw ->

        log.info("${logPrefix} groupName=${groupName} groupConfigRaw=${JsonUtils.printToJsonString(groupConfigRaw)}")

        // job configs overlay parent settings
        Map groupConfig = config.findAll { !["moleculeImageGroups","groups"].contains(it.key) } + groupConfigRaw
        groupConfig.groupName = groupName
        String stageName = "build group ${groupName}"
        log.info("${logPrefix} groupName=${groupName} groupConfig=${JsonUtils.printToJsonString(groupConfig)}")

        if (config?.runGroupsInParallel && config.runGroupsInParallel.toBoolean()) {
            parallelGroups["group-${groupConfig.groupName}"] = {
                jobResult = runMoleculeManifest(groupConfig)
            }
        } else {
            stage("${stageName}") {
                jobResult = runMoleculeManifest(groupConfig)
            }
        }
    }

    if (parallelGroups.size()>0) {
        log.info("${logPrefix} parallelGroups=${parallelGroups}")
        parallel parallelGroups
    }

    log.info("${logPrefix} finished: jobResult=${jobResult}")
    return jobResult
}

boolean runMoleculeImageList(Map config) {
    String logPrefix = "buildAndPublishImageList():"
    Map parallelJobs = [:]
    config.moleculeImageList.each { Map buildConfigRaw ->
        log.debug("${logPrefix} buildConfigRaw=${JsonUtils.printToJsonString(buildConfigRaw)}")

        Map buildConfig = config.findAll { !["moleculeImageList"].contains(it.key) } + buildConfigRaw

        log.info("${logPrefix} buildConfig=${JsonUtils.printToJsonString(buildConfig)}")

        if (config?.runInParallel && config.runInParallel.toBoolean()) {
            parallelJobs["split-${buildConfig.moleculeImageLabel}"] = {
                jobResults.add(runMoleculeManifest(buildConfig))
            }
        } else {
            stage("build and publish ${buildConfig.moleculeImageLabel}") {
                jobResults.add(runMoleculeManifest(buildConfig))
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
    boolean jobResult = (jobResults.size()>0) ? jobResults.inject { a, b -> a && b } : true
    log.info("${logPrefix} finished: jobResult=${jobResult}")
    return jobResult
}

boolean runMoleculeJob(Map config) {
    String logPrefix="runMoleculeJob(${config.moleculeImageLabel}):"
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
    // source for 'build-docker-image' job referenced in following 'jobFolder' located in buildDockerImage.groovy
    jobConfigs.jobFolder = "INFRA/repo-test-automation/run-molecule-test"
    jobConfigs.wait = true
    jobConfigs.supportedJobParams = [
        "GitRepoUrl",
        "GitRepoBranch",
        "GitCredentialsId",
        "RegistryUrl",
        "RegistryCredId",
        "MoleculeImageLabel",
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

    log.info("${logPrefix} GIT_URL=${GIT_URL}")
    log.info("${logPrefix} GIT_BRANCH=${GIT_BRANCH}")

    Map jobParameters = [:]
    jobParameters.Timeout = config.childJobTimeout
    jobParameters.TimeoutUnits = config.childJobTimeoutUnit

    jobParameters.GitRepoUrl = GIT_URL
    jobParameters.GitRepoBranch = GIT_BRANCH
    jobParameters.GitCredentialsId = config.gitCredentialsId

    jobParameters.RegistryUrl = config.registryUrl
    jobParameters.RegistryCredId = config.registryCredId

    jobParameters.MoleculeImageLabel = config.moleculeImageLabel
    jobParameters.BuildDir = config.buildDir
    jobParameters.BuildPath = config.buildPath
    if (buildArgs) {
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
