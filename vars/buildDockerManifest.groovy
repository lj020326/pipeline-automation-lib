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

        agent {
            label "docker"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            skipDefaultCheckout(false)
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
                        Map jobResults = runDockerBuildManifest(config)
                        log.info("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
                        dir(config.buildReportDir) {
                            log.info("dir: ${config.buildReportDir} successfully created!")
                        }
                        log.info("saving ${config.buildReport}")
                        try {
                            // ref: https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#writeyaml-write-a-yaml-from-an-object-or-objects
                            writeYaml file: config.buildReport, data: jobResults
                        } catch (Exception err) {
                            log.error("writeYaml(${config.buildReport}): exception occurred [${err}]")
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

//@NonCPS
Map loadPipelineConfig(Map params) {

    Map config = [:]

    if (params) {
        log.debug("copy immutable params map to mutable config map")
        log.debug("params=${JsonUtils.printToJsonString(params)}")
        config = MapMerge.merge(config, params)
    }

    config.get('logLevel', "INFO")
//     config.get('logLevel', "DEBUG")
    config.get('timeout', "5")
    config.get('timeoutUnit', "HOURS")
    config.get('debugPipeline', false)
    config.get('wait', true)
    config.get('failFast', false)
    config.get('propagate', config.failFast)
    config.get('maxRandomDelaySeconds', "0")

    config.get('childJobTimeout', "4")
    config.get('childJobTimeoutUnit', "HOURS")

    config.get('runGroupsInParallel', false)
    config.get('runInParallel', false)
    config.get('parallelJobsBatchSize', 0)

//     config.get("gitCredentialsId", "bitbucket-ssh-jenkins")
    config.get("gitCredentialsId", "infra-jenkins-git-user")

//    config.get("registryUrl","https://registry.media.johnson.int:5000")
    config.get("registryUrl","https://media.johnson.int:5000")
//    config.get("registryUrl","https://media.dettonville.int:5000")
    config.get("registryCredId", "docker-registry-admin")

    config.get("buildImageLabel", "${env.JOB_NAME.split('/')[-2]}")
    config.get("buildDir", ".")
    config.get("buildPath", ".")

    config.get('buildReportDir','build')
    config.get('buildReport',"${config.buildReportDir}/build-results.yml")

    // ref: https://stackoverflow.com/questions/40261710/getting-current-timestamp-in-inline-pipeline-script-using-pipeline-plugin-of-hud
    Date now = new Date()

    String buildDate = now.format("yyyy-MM-dd", TimeZone.getTimeZone('UTC'))
    String buildId = "build-${env.BUILD_NUMBER}"

    config.get("buildId", buildId)
    config.get("buildDate", buildDate)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List dockerEnvVarsListDefault = [
        "BUILDX_CONFIG=/home/jenkins/.docker/buildx"
    ]
    config.get('dockerEnvVarsList', dockerEnvVarsListDefault)

    log.info("config=${JsonUtils.printToJsonString(config)}")

    config.get('configFile', ".jenkins/docker-build-config.yml")

    return config
}

Map loadPipelineConfigFile(Map baseConfig) {

    Map configFileMap = readYaml file: baseConfig.configFile
    log.debug("configFileMap=${JsonUtils.printToJsonString(configFileMap)}")

    Map buildConfigs = configFileMap.pipeline
    log.debug("buildConfigs=${JsonUtils.printToJsonString(buildConfigs)}")

//     config = baseConfig + buildConfigs

    Map config = MapMerge.merge(baseConfig, buildConfigs)

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

Map runDockerBuildManifest(Map config) {

    Map jobResults = [:]
    jobResults.failed = false
    if (config?.buildImageGroups) {
        jobResults = buildAndPublishImageGroups(config)
    } else if (config?.buildImageList) {
        jobResults = buildAndPublishImageList(config)
    } else {
        jobResults = runBuildAndPublishImageJob(config)
    }
    log.debug("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    if (jobResults.failed && config.failFast) {
        log.debug("config.failFast=${config.failFast}")
        log.error("results failed - not running any more jobs")
        currentBuild.result = 'FAILURE'
    }

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

    log.info("Running parallelGroups")
    runParallelJobs(config, parallelGroups)

    // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
    // ref: https://blog.mrhaki.com/2009/09/groovy-goodness-using-inject-method.html
    boolean failed = (jobResults.groups.size()>0) ? jobResults.groups.inject(false) { a, k, v -> a || v.failed } : false
    jobResults.failed = failed

    log.debug("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    return jobResults
}

Map buildAndPublishImageList(Map config) {

    Map parallelJobs = [:]
    Map jobResults = [:]
    jobResults.items = [:]
    config.buildImageList.eachWithIndex { Map buildConfigRaw, index ->
        log.debug("buildConfigRaw=${JsonUtils.printToJsonString(buildConfigRaw)}")

        Map buildConfig = config.findAll { !["buildImageList"].contains(it.key) } + buildConfigRaw

        String buildJobId = "${buildConfig.buildImageLabel}-${index}"
        buildConfig.buildJobId = buildJobId

        log.debug("buildConfig=${JsonUtils.printToJsonString(buildConfig)}")

        if (config?.runInParallel && config.runInParallel.toBoolean()) {
            parallelJobs["split-${buildConfig.buildJobId}"] = {
                jobResults.items.put(buildConfig.buildJobId, runDockerBuildManifest(buildConfig))
            }
        } else {
            stage("build and publish ${buildConfig.buildImageLabel}-${index}") {
                jobResults.items.put(buildConfig.buildJobId, runDockerBuildManifest(buildConfig))
            }
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

Map runBuildAndPublishImageJob(Map config) {
    Map jobResults = [:]
    String logPrefix = "[${config.buildImageLabel}]:"

    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    String gitCommitId = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
//     sh "git rev-parse HEAD > .git/commit-id"
//     String gitCommitId = readFile('.git/commit-id').trim()

    log.debug("${logPrefix} gitCommitId=${gitCommitId}")
    jobResults.gitCommitId = gitCommitId

    Map buildArgs = [:]
    if (config.buildArgs) {
        config.buildArgs.each { key, value ->
            buildArgs[key] = value
        }
    }
    if (!buildArgs?.BUILD_ID) {
        buildArgs['BUILD_ID'] = config.buildId
    }
    if (!buildArgs?.BUILD_DATE) {
        buildArgs['BUILD_DATE'] = config.buildDate
    }

    log.debug("${logPrefix} buildArgs=${JsonUtils.printToJsonString(buildArgs)}")

    Map jobConfigs = [:]
    // source for 'build-docker-image' job referenced in following 'jobFolder' located in buildDockerImage.groovy
    jobConfigs.jobFolder = "INFRA/build-docker-image"
    jobConfigs.supportedJobParams = [
        "GitRepoUrl",
        "GitRepoBranch",
        "GitCredentialsId",
        "RegistryUrl",
        "RegistryCredId",
        "BuildImageLabel",
        "BuildDir",
        "BuildPath",
        "BuildTags",
        "BuildArgs",
        "DockerFile",
        "ChangedEmailList",
        "AlwaysEmailList",
        "FailedEmailList",
        "Timeout",
        "TimeoutUnit",
    ]

    log.debug("${logPrefix} GIT_URL=${GIT_URL}")
    log.debug("${logPrefix} GIT_BRANCH=${GIT_BRANCH}")

    Map jobParameters = [:]
    jobParameters.Timeout = config.childJobTimeout
    jobParameters.TimeoutUnit = config.childJobTimeoutUnit

    jobParameters.GitRepoUrl = GIT_URL
    jobParameters.GitRepoBranch = GIT_BRANCH
    jobParameters.GitCredentialsId = config.gitCredentialsId

    jobParameters.RegistryUrl = config.registryUrl
    jobParameters.RegistryCredId = config.registryCredId

    jobParameters.BuildImageLabel = config.buildImageLabel
    jobParameters.BuildDir = config.buildDir
    jobParameters.BuildPath = config.buildPath

    if (config?.buildTags) {
        if (config.buildTags instanceof List) {
            jobParameters.BuildTags = config.buildTags.join(",")
        } else if (config.buildTags instanceof String) {
            jobParameters.BuildTags = config.buildTags
        } else {
            log.error("unsupported buildTags type : " + config.buildTags.getClass())
        }
    }

//     jobParameters.BuildArgs = JsonUtils.printToJsonString(buildArgs)
    jobParameters.BuildArgs = JsonOutput.toJson(buildArgs)

    if (config?.dockerFile) {
        jobParameters.DockerFile = config.dockerFile
    }
    jobConfigs.jobParameters = jobParameters
    jobResults.jobParameters = jobParameters

    jobConfigs.propagate = config.propagate
    jobConfigs.wait = config.wait
    jobConfigs.failFast = config.failFast
    jobConfigs.logLevel = config.logLevel
    jobConfigs.maxRandomDelaySeconds = config.maxRandomDelaySeconds

    log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    Map jobResult = runJob(jobConfigs)

    log.debug("${logPrefix} jobResult=${JsonUtils.printToJsonString(jobResult)}")
    jobResults << jobResult

    if (jobResults.failed && config.failFast) {
        log.debug("${logPrefix} config.failFast=${config.failFast}")
        log.error("${logPrefix} results failed - not running any more jobs")
        currentBuild.result = 'FAILURE'
    }

    log.debug("${logPrefix} finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")
    return jobResults
}
