#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
// import com.dettonville.api.pipeline.utils.DockerUtil

// ref: https://blog.nimbleci.com/2016/08/31/how-to-build-docker-images-automatically-with-jenkins-pipeline/
// ref: https://mike42.me/blog/2019-05-how-to-integrate-gitea-and-jenkins
// ref: https://github.com/jenkinsci/pipeline-examples/pull/83/files
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

def call(Map params=[:]) {

//    Logger.init(this, LogLevel.INFO)
    Logger.init(this)
    Logger log = new Logger(this)

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(log, params)

    properties([
        disableConcurrentBuilds()
    ])

    pipeline {

        agent {
//             label "docker-in-docker"
            label "docker"
        }

        stages {
            stage("Build and Publish Docker Image") {
                steps {
                    script {
                        config.buildImageList.each { String buildInfo ->
//                             config.get("buildInfo", buildInfo)
//                             config = MapMerge.merge(config, buildInfo)
//                             buildAndPublishImage(log, config)
                            Map buildConfig = MapMerge.merge(config.findAll { !["buildImageList"].contains(it.key) }, buildInfo)
                            buildAndPublishImage(log, buildConfig)
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    sendEmail(currentBuild, env)
                }
            }
        }
    }
}

//@NonCPS
Map loadPipelineConfig(Logger log, Map params) {
    String logPrefix = "loadPipelineConfig():"
    Map config = [:]

    log.info("${logPrefix} copy immutable params map to mutable config map")
    config = MapMerge.merge(config, params)

//    config.get("registryUrl","https://registry.media.johnson.int:5000")
    config.get("registryUrl","https://media.johnson.int:5000")
//    config.get("registryUrl","https://media.dettonville.int:5000")
    config.get("registryCredId", "docker-registry-admin")
    config.get("buildImageLabel", "${env.JOB_NAME.split('/')[-2]}")
    config.get("buildDir", ".")
    config.get("buildPath", ".")
//    config.get("buildImageList", [[ buildDir: ".", buildImageLabel: "${env.JOB_BASE_NAME}"]])
    config.get("buildImageList", [[ buildDir: config.buildDir, buildImageLabel: config.buildImageLabel]])

//    config.logLevel = config.get('logLevel', "INFO")
    config.logLevel = config.get('logLevel', "DEBUG")
    config.debugPipeline = config.get('debugPipeline', false)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.info("${logPrefix} log.level=${log.level}")

    log.debug("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

void buildAndPublishImage(Logger log, Map config) {
    String logPrefix="buildAndPublishImage():"
    log.info("${logPrefix} started")

    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    sh "git rev-parse HEAD > .git/commit-id"
    String commit_id = readFile('.git/commit-id').trim()

    log.info("${logPrefix} commit_id=${commit_id}")

//    DockerUtil dockerUtil = new DockerUtil(this)
    List buildArgs = []
    if (config.buildArgs) {
        config.buildArgs.each { key, value ->
            buildArgs.push("--build-arg ${key}=${value}")
        }
    }
    log.info("${logPrefix} buildArgs=${JsonUtils.printToJsonString(buildArgs)}")

    dir (config.buildDir) {

        def app
        String stageName = "build ${config.buildImageLabel}"
        if (config.buildPath!=".") stageName += " ${config.buildPath}"

        stage("${stageName}") {
            // ref: https://www.jenkins.io/doc/book/pipeline/docker/
            if (config?.dockerFile) {
                buildArgs.push("-f ${config.dockerFile} ${config.buildPath}")
            }
            else {
                buildArgs.push("${config.buildPath}")
            }
            if (buildArgs) {
                String buildArgsString = buildArgs.join(" ")
                log.info("${logPrefix} buildArgsString=${buildArgsString}")
                app = docker.build(config.buildImageLabel, buildArgsString)
            } else {
                app = docker.build(config.buildImageLabel)
            }
        }

        stage("publish ${config.buildImageLabel}") {

            docker.withRegistry(config.registryUrl, config.registryCredId) {
                app.push "${env.BRANCH_NAME}"
                app.push "build-${env.BUILD_ID}"
                app.push "${commit_id}"
                if (env.BRANCH_NAME == 'master') {
                    app.push 'latest'
                }
            }
        }
    }

}


