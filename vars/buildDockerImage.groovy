#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import org.codehaus.groovy.runtime.StackTraceUtils

// ref: https://blog.nimbleci.com/2016/08/31/how-to-build-docker-images-automatically-with-jenkins-pipeline/
// ref: https://mike42.me/blog/2019-05-how-to-integrate-gitea-and-jenkins
// ref: https://github.com/jenkinsci/pipeline-examples/pull/83/files
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call() {

    List paramList = []

    Map paramMap = [
        gitRepoUrl: string(defaultValue: "ssh://git@gitea.admin.dettonville.int:2222/infra/docker-jenkins.git", description: "Specify the git repo image URL", name: 'GitRepoUrl'),
        gitRepoBranch: string(defaultValue: "main", description: "Specify the git repo branch", name: 'GitRepoBranch'),
        gitCredentialsId: string(defaultValue: "bitbucket-ssh-jenkins", description: "Specify the git repo credential ID", name: 'GitCredentialsId'),
        registryUrl: string(defaultValue: "https://media.johnson.int:5000", description: "Specify the RegistryUrl", name: "RegistryUrl"),
        registryCredId: string(defaultValue:  "docker-registry-admin", description: "Specify the RegistryCredId", name: "RegistryCredId"),
        buildImageLabel: string(defaultValue:  "docker-jenkins:latest", description: "Specify the BuildImageLabel", name: "BuildImageLabel"),
        buildDir: string(defaultValue: "image/base", description: "Specify the BuildDir", name: "BuildDir"),
        buildPath: string(defaultValue: ".", description: "Specify the BuildPath", name: "BuildPath"),
        buildTags: string(defaultValue: "", description: "Specify the docker image tags in comma delimited format (e.g., 'build-123,latest')", name: "BuildTags"),
        buildArgs: string(defaultValue: "", description: "Specify the BuildArgs in JSON string format", name: "BuildArgs"),
        dockerFile: string(defaultValue: "", description: "Specify the docker file", name: 'DockerFile'),
        changedEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'changed' status", name: 'ChangedEmailList'),
        alwaysEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'always' status", name: 'AlwaysEmailList'),
        failedEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'failed' status", name: 'FailedEmailList'),
        timeout: string(defaultValue: "4", description: "Specify the job timeout", name: 'Timeout'),
        timeoutUnit: string(defaultValue: "HOURS", description: "Specify the job timeout unit (HOURS, MINUTES, etc)", name: 'TimeoutUnit'),
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList)
    ])

    log.info("Loading Default Configs")
    Map config = loadPipelineConfig(params)
    def dockerImage

    pipeline {

        agent {
//             label "docker-in-docker"
            label "docker"
        }
        options {
            skipDefaultCheckout()
            buildDiscarder(logRotator(numToKeepStr: '40'))
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
            // depends on 'throttle-concurrents' plugin
            // ref: https://plugins.jenkins.io/throttle-concurrents/
            throttleJobProperty(
                categories: ['docker_image_builds'],
                throttleEnabled: true,
                throttleOption: 'category'
            )
        }

        stages {
            stage ('Checkout SCM') {
                steps {
                    git credentialsId: config.gitCredentialsId,
                        url: config.gitRepoUrl,
                        branch: config.gitRepoBranch
                }
            }
            stage("Build Docker Image") {
                steps {
                    script {
                        dockerImage = buildDockerImage(config)
                    }
                }
            }
            stage("Publish Docker Image") {
                steps {
                    script {
                        publishDockerImage(dockerImage, config)
                    }
                }
            }
        }
        post {
            always {
                script {
                    List emailAdditionalDistList = []
                    if (config?.deployEmailDistList) {
                        if (config.gitRepoBranch) {
                            if (config.gitRepoBranch in ['main','QA','PROD'] || config.gitRepoBranch.startsWith("release/")) {
                                emailAdditionalDistList = config.deployEmailDistList
                                log.info("post(${config.gitRepoBranch}): sendEmail(${currentBuild.result})")
                                sendEmail(currentBuild, env, emailAdditionalDistList: emailAdditionalDistList)
                            }
                        }
                    } else if (config?.alwaysEmailList) {
                        log.info("config.alwaysEmailList=${config.alwaysEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.alwaysEmailList.split(","))
                    } else {
                        log.info("sendEmail default")
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
}

//@NonCPS
Map loadPipelineConfig(Map params) {

    Map config = [:]

    log.debug("copy immutable params map to mutable config map")

    log.info("params=${JsonUtils.printToJsonString(params)}")

//     config = MapMerge.merge(config, params)
    params.each { key, value ->
        key=Utilities.decapitalize(key)
        log.debug("key=${key} value=${value}")
        if (value!="") {
            config[key] = value
        }
    }

//    config.get('logLevel', "INFO")
    config.get('logLevel', "DEBUG")
    config.get('debugPipeline', false)
    config.get('timeout', "4")
    config.get('timeoutUnit', "HOURS")
    config.get('maxRandomDelaySeconds', "0") as String

    // ref: https://stackoverflow.com/questions/40261710/getting-current-timestamp-in-inline-pipeline-script-using-pipeline-plugin-of-hud
    Date now = new Date()

    String buildDate = now.format("yyyy-MM-dd", TimeZone.getTimeZone('UTC'))
    log.debug("buildDate=${buildDate}")

//     String buildId = "${env.BUILD_NUMBER}"
    String buildId = "build-${env.BUILD_NUMBER}"
    log.debug("buildId=${buildId}")

    config.get("buildId", buildId)
    config.get("buildDate", buildDate)
    config.buildLabel = "${config.buildImageLabel}:${config.buildId}"

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List dockerEnvVarsListDefault = [
        "BUILDX_CONFIG=/home/jenkins/.docker/buildx"
    ]
    config.dockerEnvVarsList = config.get('dockerEnvVarsList', dockerEnvVarsListDefault)

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}

def buildDockerImage(Map config) {

    def dockerImage

    log.debug("config=${JsonUtils.printToJsonString(config)}")

    log.debug("config.buildLabel=${config.buildLabel}")

    List buildArgs = []
    if (config.buildArgs) {
        Object buildArgsMap = readJSON(text: config.buildArgs)
        log.info("buildArgsMap=${JsonUtils.printToJsonString(buildArgsMap)}")

        buildArgsMap.each { key, value ->
            buildArgs.push("--build-arg ${key}=${value}")
        }
        if (!buildArgsMap?.BUILD_ID) {
            buildArgs.push("--build-arg BUILD_ID=${config.buildId}")
        }
        if (!buildArgsMap?.BUILD_DATE) {
            buildArgs.push("--build-arg BUILD_DATE=${config.buildDate}")
        }
    } else {
        buildArgs.push("--build-arg BUILD_ID=${config.buildId}")
        buildArgs.push("--build-arg BUILD_DATE=${config.buildDate}")
    }
    log.debug("buildArgs=${JsonUtils.printToJsonString(buildArgs)}")

    dir (config.buildDir) {
        // ref: https://www.jenkins.io/doc/book/pipeline/docker/
        if (config?.dockerFile) {
            buildArgs.push("-f ${config.dockerFile}")
        }

        buildArgs.push("${config.buildPath}")
        if (buildArgs) {
            String buildArgsString = buildArgs.join(" ")
            log.debug("buildArgsString=${buildArgsString}")
            dockerImage = docker.build(config.buildLabel, buildArgsString)
        } else {
            dockerImage = docker.build(config.buildLabel)
        }
    }
    return dockerImage
}

void publishDockerImage(def dockerImage, Map config) {

    log.debug("config=${JsonUtils.printToJsonString(config)}")

    String gitCommitId = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
//     result = gitCommit.take(6)
//     sh "git rev-parse HEAD > .git/commit-id"
//     String gitCommitId = readFile('.git/commit-id').trim()

    log.debug("gitCommitId=${gitCommitId}")

    dir (config.buildDir) {

        docker.withRegistry(config.registryUrl, config.registryCredId) {
            withEnv(config.dockerEnvVarsList) {
                if (config.buildTags) {
                    List buildTagsList = []
                    // ref: https://stackoverflow.com/a/73789296/2791368
                    buildTagsList = config.buildTags.split(",").collect{ it.trim() }
                    log.info("buildTagsList=${JsonUtils.printToJsonString(buildTagsList)}")
                    buildTagsList.each { String tagValue ->
                        log.info("docker push ${tagValue}")
                        dockerImage.push "${tagValue}"
                    }
                } else {
                    // push to standard set of derived tags (e.g., branch, buildId, buildDate, gitCommitId, latest)
                    dockerImage.push "${config.gitRepoBranch}"
                    if (config?.buildId) {
                        dockerImage.push "${config.buildId}"
                    } else {
                        dockerImage.push "build-${env.BUILD_NUMBER}"
                    }
                    if (config?.buildDate) {
                        dockerImage.push "${config.buildDate}"
                    }
                    if (config?.pushCommitLabel && config.pushCommitLabel.toBoolean()) {
                        dockerImage.push "${gitCommitId}"
                    }
                    if (config.gitRepoBranch in ['master','main']) {
                        dockerImage.push 'latest'
                    }
                }
            }
        }
    }

}
