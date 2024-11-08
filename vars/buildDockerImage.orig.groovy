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
    Logger log = new Logger(this, LogLevel.INFO)

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
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            disableConcurrentBuilds()
            timestamps()
            timeout(time: 3, unit: 'HOURS')
        }

        stages {
			stage('Load docker build config file') {
				when {
                    expression { fileExists config.buildConfigFile }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(log, config)
					}
				}
			}
            stage("Build and Publish Docker Image") {
                steps {
                    script {
                        config.buildImageList.each { Map buildConfigRaw ->
                            log.info("buildConfigRaw=${JsonUtils.printToJsonString(buildConfigRaw)}")

//                             Map buildConfig = MapMerge.merge(config.findAll { !["buildImageList"].contains(it.key) }, buildConfigRaw)
                            Map buildConfig = config.findAll { !["buildImageList"].contains(it.key) } + buildConfigRaw

                            log.info("buildConfig=${JsonUtils.printToJsonString(buildConfig)}")

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

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List dockerEnvVarsListDefault = [
        "BUILDX_CONFIG=/home/jenkins/.docker/buildx"
    ]
    config.dockerEnvVarsList = config.get('dockerEnvVarsList', dockerEnvVarsListDefault)

    log.debug("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    config.buildConfigFile = config.get('buildConfigFile', ".jenkins/docker-build-config.yml")

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
        if (!config.buildArgs?.BUILD_ID) {
            buildArgs.push("--build-arg BUILD_ID=${config.buildId}")
        }
        if (!config.buildArgs?.BUILD_DATE) {
            buildArgs.push("--build-arg BUILD_DATE=${config.buildDate}")
        }
    } else {
        buildArgs.push("--build-arg BUILD_ID=${config.buildId}")
        buildArgs.push("--build-arg BUILD_DATE=${config.buildDate}")
    }
    log.info("${logPrefix} buildArgs=${JsonUtils.printToJsonString(buildArgs)}")

    dir (config.buildDir) {

        def app
        String stageName = "build ${config.buildImageLabel}"
        if (config.buildPath!=".") stageName += " ${config.buildPath}"

        stage("${stageName}") {
            // ref: https://www.jenkins.io/doc/book/pipeline/docker/
            if (config?.dockerFile) {
                buildArgs.push("-f ${config.dockerFile}")
            }
            buildArgs.push("${config.buildPath}")
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
                withEnv(config.dockerEnvVarsList) {
                    app.push "${env.BRANCH_NAME}"
                    if (config?.buildId) {
                        app.push "${config.buildId}"
                    } else {
                        app.push "build-${env.BUILD_NUMBER}"
                    }
                    if (config?.buildDate) {
                        app.push "${config.buildDate}"
                    }
//                     app.push "${commit_id}"
                    if (env.BRANCH_NAME in ['master','main']) {
                        app.push 'latest'
                    }
                }
            }
        }
    }

}

Map loadPipelineConfigFile(Logger log, Map config) {
    String logPrefix="loadPipelineConfigFile():"

    Map dockerBuildConfigMap = readYaml file: config.buildConfigFile

    Map buildConfigs=dockerBuildConfigMap.pipeline
    log.info("${logPrefix} buildConfigs=${JsonUtils.printToJsonString(buildConfigs)}")

    config = config + buildConfigs

    log.info("${logPrefix} Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

