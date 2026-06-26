#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import org.codehaus.groovy.runtime.StackTraceUtils

import groovy.transform.Field
@Field Logger log = new Logger(this)

def call() {

    List paramList = []

    Map paramMap = [
        initializeParamsOnly: booleanParam(defaultValue: false, description: "Set to true to only initialize parameters and skip execution of stages.", name: 'InitializeParamsOnly'),
        gitRepoUrl: string(defaultValue: "ssh://git@gitea.admin.dettonville.int:2222/infra/docker-jenkins.git", description: "Specify the git repo image URL", name: 'GitRepoUrl'),
        gitRepoBranch: string(defaultValue: "main", description: "Specify the git repo branch", name: 'GitRepoBranch'),
        gitCredentialsId: string(defaultValue: "gitea-ssh-jenkins", description: "Specify the git repo credential ID", name: 'GitCredentialsId'),
        registryUrl: string(defaultValue: "https://media.johnson.int:5000", description: "Specify the RegistryUrl", name: "RegistryUrl"),
        registryCredId: string(defaultValue:  "docker-registry-admin", description: "Specify the RegistryCredId", name: "RegistryCredId"),
        builderImage: string(defaultValue:  "", description: "Specify the docker builder image (e.g., 'media.johnson.int:5000/ansible/ansible-runner:stable-2.18-py3.13')", name: "BuilderImage"),
        builderUid: string(defaultValue:  "", description: "Specify the build runner UID (e.g., 'jenkins')", name: "BuilderUid"),
        builderGid: string(defaultValue:  "", description: "Specify the build runner GID (e.g., 'jenkins')", name: "BuilderGid"),
        buildImageName: string(defaultValue:  "", description: "Specify the BuildImageName (e.g., 'docker-jenkins')", name: "BuildImageName"),
        buildDir: string(defaultValue: ".", description: "Specify relative directory where docker build will run (e.g., 'image/base')", name: "BuildDir"),
        buildPath: string(defaultValue: ".", description: "Specify relative directory used as final argument to the docker command", name: "BuildPath"),
        buildTags: string(defaultValue: "", description: "Specify the docker image tags in comma delimited format (e.g., 'build-123,latest')", name: "BuildTags"),
        buildPlatforms: string(defaultValue: "linux/amd64", description: "Target architectures comma-separated (e.g., linux/amd64,linux/arm64)", name: 'BuildPlatforms'),
        buildArgs: string(defaultValue: "", description: "Specify the BuildArgs in JSON string format", name: "BuildArgs"),
        buildTestCommand: string(defaultValue: "", description: "The shell command to run post-build, pre-push", name: 'BuildTestCommand'),
        buildTestAppendIdArg: booleanParam(defaultValue: false, description: 'If true - append env.BUILD_NUMBER to test command', name: 'BuildTestAppendIdArg'),
        buildTestAppendIdOption: string(defaultValue: "", description: "Append option name to shell command followed by env.BUILD_NUMBER", name: 'BuildTestAppendIdOption'),
        runTestCommandInsideContainer: booleanParam(defaultValue: false, description: 'If true - run buildTestCommand inside of target build container', name: 'RunTestCommandInsideContainer'),
        testResultsPath: string(defaultValue: "", description: "Path to test result files to archive", name: 'TestResultsPath'),
        dockerFile: string(defaultValue: "", description: "Specify the docker file", name: 'DockerFile'),
        changedEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'changed' status", name: 'ChangedEmailList'),
        alwaysEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'always' status", name: 'AlwaysEmailList'),
        failedEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'failed' status", name: 'FailedEmailList'),
        timeout: string(defaultValue: "4", description: "Specify the job timeout", name: 'Timeout'),
        timeoutUnit: string(defaultValue: "HOURS", description: "Specify the job timeout unit (HOURS, MINUTES, etc)", name: 'TimeoutUnit'),
    ]

    paramMap.each { String key, def param ->
        if (params.containsKey(key)) {
            log.debug("Using parameter override for ${key}")
        }
        paramList.add(param)
    }

    properties([
        parameters(paramList)
    ])

    log.info("Loading Default Configs")
    Map config = loadPipelineConfig(params)
    def dockerImage

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.builderImage
                args config.builderArgs
                // This force-checks the registry for a newer version
                alwaysPull true
            }
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
            stage ('Checkout Repository Data') {
                when {
                    // This stage will only run if InitializeParamsOnly is false
                    expression { return !config.initializeParamsOnly }
                }
                steps {
                    script {
                        checkoutRepository(config)
                    }
                }
            }

            stage('Docker Multi-Arch Build') {
                when {
                    // This stage will only run if InitializeParamsOnly is false
                    expression { return !config.initializeParamsOnly }
                }
                steps {
                    script {
                        buildDockerImage(config)
                        currentBuild.result = 'SUCCESS'
                    }
                }
            }

            stage('Docker Build Verification Test') {
                when {
                    allOf {
                        expression { config.buildTestCommand }
                        // This stage will only run if InitializeParamsOnly is false
                        expression { return !config.initializeParamsOnly }
                    }
                }
                steps {
                    script {
                        executeDockerImageTests(config)
                    }
                }
            }

            stage('Docker Publish Image') {
                when {
                    allOf {
                        expression { currentBuild.result == 'SUCCESS' }
                        // This stage will only run if InitializeParamsOnly is false
                        expression { return !config.initializeParamsOnly }
                    }
                }
                steps {
                    script {
                        publishDockerImage(config)
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (!config.initializeParamsOnly) {
                        cleanWorkspace()
                        sendEmailNotification(config, "always")
                    }
                }
            }
            changed {
                script {
                    if (!config.initializeParamsOnly) {
                        sendEmailNotification(config, "changed")
                    }
                }
            }
            success {
                script {
                    if (!config.initializeParamsOnly) {
                        sendEmailNotification(config, "success")
                    }
                }
            }
            failure {
                script {
                    if (!config.initializeParamsOnly) {
                        sendEmailNotification(config, "failed")
                    }
                }
            }
            aborted {
                script {
                    if (!config.initializeParamsOnly) {
                        sendEmailNotification(config, "aborted")
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
    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    config.get('debugPipeline', false)
    config.get('timeout', "4")
    config.get('timeoutUnit', "HOURS")
    config.get('maxRandomDelaySeconds', "0")

//     config.get('jenkinsNodeLabel',"docker-in-docker")
    config.get('jenkinsNodeLabel',"docker")

    // Implied extraction of dockerImageRegistry domain from RegistryUrl string parameter context
    String dockerImageRegistryDefault = "media.johnson.int:5000"
    if (config?.registryUrl) {
        dockerImageRegistryDefault = config.registryUrl.replace("https://", "").replace("http://", "")
    }
    config.get("dockerImageRegistry", dockerImageRegistryDefault)

    String builderImage = "media.johnson.int:5000/jenkins-docker-agent:latest"
//    String builderImage = "media.johnson.int:5000/ansible/ansible-runner:stable-2.18-py3.13"
//    String builderImage = "media.johnson.int:5000/ansible/ansible-runner:latest-py3.13"
//    String builderImage = "media.johnson.int:5000/jenkins-docker-cicd-agent:latest"
    config.get("builderImage", builderImage)

    List builderArgsList = []
    builderArgsList.push("-v /var/run/docker.sock:/var/run/docker.sock")
    builderArgsList.push("-v /etc/docker/daemon.json:/etc/docker/daemon.json:ro")
    builderArgsList.push("--privileged")

    // configure to share the host's network stack.
    // This removes the network isolation between the container and the host, allowing the container
    // to access services running on the host via 127.0.0.1 or the host's primary IP address/
    builderArgsList.push("--network host")

//     builderArgsList.push("-u root")
    if (config?.builderUid && config?.builderGid) {
        builderArgsList.push("-u ${config.builderUid}:${config.builderGid}")
    }
    config.get("builderArgs", builderArgsList.join(" "))

    // ref: https://stackoverflow.com/questions/40261710/getting-current-timestamp-in-inline-pipeline-script-using-pipeline-plugin-of-hud
    Date now = new Date()

    String buildDate = now.format("yyyy-MM-dd", TimeZone.getTimeZone('UTC'))
    log.debug("buildDate=${buildDate}")

    config.get("buildDate", buildDate)
    config.get('buildPlatforms', "linux/amd64,linux/arm64")

    String gitBranchLabel = config.gitRepoBranch.replace("^origin/", "")
    config.dockerBranchLabel = gitBranchLabel.replace("/", "-").replace("%2F", "-")
    config.gitRepoBranchRaw = gitBranchLabel
//     config.dockerBranchLabel = gitBranchLabel.replaceAll(/^(.*)-(\d+)-(.*)$/, '$1-$2').replace('/','-').replace('%2F','-')

    // Safe environment map configurations
    config.buildArgsMap = [:]
    if (config.buildArgs) {
        try {
            Map buildArgsMap = readJSON(text: config.buildArgs)
            if (!buildArgsMap?.BUILD_ID) {
                buildArgsMap.get("BUILD_ID", config.buildId)
            }
            if (!buildArgsMap?.BUILD_DATE) {
                buildArgsMap.get("BUILD_DATE" , config.buildDate)
            }
            config.buildArgsMap = buildArgsMap
        } catch(Exception e) {
            log.warn("Failed to parse build arguments JSON mapping context: ${e.getMessage()}")
        }
    }

    // ref: https://issues.jenkins.io/browse/JENKINS-61372
    List dockerEnvVarsListDefault = [
        "BUILDX_CONFIG=/home/jenkins/.docker/buildx",
        "DOCKER_BUILDKIT=1",
        "BUILDX_EXPERIMENTAL=1"
    ]
    config.dockerEnvVarsList = config.get('dockerEnvVarsList', dockerEnvVarsListDefault)

    config.get("buildTestAppendIdArg", false)
    config.get("runTestCommandInsideContainer", false)

    if (config?.buildTestCommand) {
        if (config?.testResultsPath) {
            config.get("testResultsDir", getDirName(config?.testResultsPath))
            log.debug("config.testResultsDir=${config.testResultsDir}")
        } else {
            config.get('testResultsDir', '.test-results')
        }
    }

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}

void checkoutRepository(Map config) {
    git credentialsId: config.gitCredentialsId,
        url: config.gitRepoUrl,
        branch: config.gitRepoBranch
//     checkout([$class: 'GitSCM',
//         branches: [[name: "refs/heads/${config.gitRepoBranchRaw}"]],
//         doGenerateSubmoduleConfigurations: false,
//         extensions: [[$class: 'CleanBeforeCheckout']],
//         submoduleCfg: [],
//         userRemoteConfigs: [[
//             credentialsId: config.gitCredentialsId,
//             url: config.gitRepoUrl
//         ]]
//     ])
}

void buildDockerImage(Map config) {
    withEnv(config.dockerEnvVarsList) {
        log.info("Building individual platforms locally using buildx image exporters...")
        log.debug("config=${JsonUtils.printToJsonString(config)}")

        List buildArgs = []
        config.buildArgsMap.each { key, value ->
            buildArgs.push("--build-arg ${key}=${value}")
        }
        if (config?.dockerFile) {
            buildArgs.push("-f ${config.dockerFile}")
        }

        log.debug("buildArgs=${JsonUtils.printToJsonString(buildArgs)}")
        String buildArgsString = buildArgs.join(" ")

//         log.info("Force-remove any malformed configuration files generated by upstream layers")
//         sh "rm -f /root/.docker/config.json /home/jenkins/.docker/config.json"

        if (fileExists("/root/.docker/config.json")) {
            log.info('=== /root/.docker/config.json ===')
            sh "cat /root/.docker/config.json"
        }
        if (fileExists("/home/jenkins/.docker/config.json")) {
            log.info('=== /home/jenkins/.docker/config.json ===')
            sh "cat /home/jenkins/.docker/config.json"
        }

        // Ensure the system-wide multi-arch builder is used, or fall back to an isolated one if missing
        String builderName = "multiarch-builder"

        log.info("Ensuring buildx multi-arch instance runner is initialized...")
//         sh "docker buildx create --name ${builderName} --use"
        // Check if the local CLI client inside the agent container has the local instance file
        def localInstanceExists = sh(script: "test -f ~/.docker/buildx/instances/${builderName}", returnStatus: true) == 0

        if (localInstanceExists) {
            log.info("Local instance configuration file exists. Switching context...")
            sh "docker buildx use ${builderName}"
        } else {
            log.info("Local configuration file missing for current user. Connecting agent CLI to host backend...")

            // This safely generates the missing metadata tracking file under /home/jenkins/.docker/
            // without resetting or wiping the active host container settings
            sh "docker buildx create --name ${builderName} --use"
        }

        // Ensure the backend container is active and initialized for this build session
        sh "docker buildx inspect --bootstrap"

    //     log.info("Ensure a buildx multi-arch instance runner is initialized")
    //     sh "docker buildx create --name jenkins-builder --use --bootstrap || true"

        List architectures = config.buildPlatforms.split(",").collect { it.trim() }

        dir(config.buildDir) {
            docker.withRegistry(config.registryUrl, config.registryCredId) {
                architectures.each { String platform ->
                    String architectureLabel = platform.replace('/', '-')
                    log.info("Compiling local test layer image tracking for platform target: ${platform}")

                    String buildImageTag = "${architectureLabel}-build-${env.BUILD_NUMBER}"
                    String buildImageId = "${config.buildImageName}:${buildImageTag}"

                    sh """
                        docker buildx build \
                            --platform ${platform} \
                            ${buildArgsString} \
                            -t ${buildImageId} \
                            --load \
                            ${config.buildPath}
                    """
                }
            }
        }
    }
}

def executeDockerImageTests(Map config) {
    log.info("Running verification testing cycles against local native binary formats...")

    // Check host machine arch context to know whether to run the amd64 or arm64 local test tag
    String systemArch = sh(script: "uname -m", returnStdout: true).trim()
    String platformLabel = (systemArch == "x86_64") ? "linux-amd64" : "linux-arm64"

    String targetTestImageTag = "${platformLabel}-build-${env.BUILD_NUMBER}"
    String targetTestImageId = "${config.buildImageName}:${targetTestImageTag}"

    String testBuildImageId = "${config.buildImageName}:${config.buildImageTag}"

    String dynamicTestCommand = config.buildTestCommand

    if (config.buildTestAppendIdArg) {
        if (config.buildTestAppendIdOption) {
            dynamicTestCommand += " ${config.buildTestAppendIdOption}"
        }
        dynamicTestCommand += " ${targetTestImageTag}"
    }
    config.buildTestCommand = dynamicTestCommand
    log.debug("config.buildTestCommand=${config.buildTestCommand}")

    sh "mkdir -p ${config.testResultsDir}"
    log.info("created testResultsDir ${config.testResultsDir}")

    try {
        if (config.runTestCommandInsideContainer) {
            log.info("Invoking custom verification suite: ${dynamicTestCommand} inside of container ${targetTestImageId}")
            withEnv(config.dockerEnvVarsList) {
                sh """
                    docker run --rm ${targetTestImageId} ${dynamicTestCommand}
                """
            }
        } else {
            log.info("Invoking custom verification suite: ${dynamicTestCommand}")
            sh "${dynamicTestCommand}"
        }
        log.info("Test command successful.")
        currentBuild.result = 'SUCCESS'
    } catch (Exception ex) {
        log.error("Test command failed: ${ex.getMessage()}")
        config.gitRemoteBuildStatus = "COMPLETED"
        config.gitRemoteBuildConclusion = "FAILURE"
        currentBuild.result = 'FAILURE'
        error("Image unit test verification failed. Aborting production publishing stage.")
    } finally {
        if (config?.testResultsPath) {
            log.info("Archiving test results from: ${config.testResultsPath}")
            archiveArtifacts(
                artifacts: "${config.testResultsPath}",
                fingerprint: true,
                onlyIfSuccessful: false
            )
            log.info("Archiving junit test results from path: ${config.testResultsDir}/*.xml")
            junit(testResults: "${config.testResultsDir}/*.xml",
                  skipPublishingChecks: true,
                  allowEmptyResults: true
            )
            log.info("Test results archived.")
        }
    }
}

void publishDockerImage(Map config) {
    withEnv(config.dockerEnvVarsList) {
        log.info("Merging architecture caches into multi-platform manifest lists...")

        String gitCommitId = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    //     result = gitCommit.take(6)
    //     sh "git rev-parse HEAD > .git/commit-id"
    //     String gitCommitId = readFile('.git/commit-id').trim()

        log.debug("gitCommitId=${gitCommitId}")

        List buildArgs = []
        config.buildArgsMap.each { key, value ->
            buildArgs.push("--build-arg ${key}=${value}")
        }
        if (config?.dockerFile) {
            buildArgs.push("-f ${config.dockerFile}")
        }
        String buildArgsString = buildArgs.join(" ")

        String fullTargetImage = "${config.dockerImageRegistry}/${config.buildImageName}"

        // Configure full target push labels matching your exact downstream routing rules
        List pushTags = []
        if (config.buildTags) {
            pushTags = config.buildTags.split(",").collect { it.trim() }
        } else {
            pushTags.add(config.dockerBranchLabel)
            pushTags.add(config.buildImageTag ? config.buildImageTag : "build-${env.BUILD_NUMBER}")
            if (config.gitRepoBranchRaw in ['master', 'main']) {
                pushTags.add('latest')
            }
        }

        String tagArgs = pushTags.collect { " -t ${fullTargetImage}:${it}" }.join(" ")

        dir(config.buildDir) {
            docker.withRegistry(config.registryUrl, config.registryCredId) {
                sh """
                    docker buildx build \
                        --platform ${config.buildPlatforms} \
                        ${buildArgsString} \
                        ${tagArgs} \
                        --push \
                        ${config.buildPath}
                """
            }
        }

        // Clean up temporary local architecture-specific images used for test stages
        List architectures = config.buildPlatforms.split(",").collect { it.trim() }
        architectures.each { String platform ->
            String architectureLabel = platform.replace('/', '-')
            sh "docker rmi ${config.buildImageName}:${architectureLabel}-build-${env.BUILD_NUMBER} || true"
        }
    }
}

void cleanWorkspace() {
    log.info("Cleaning execution environment workspace paths...")
    try {
        cleanWs deleteDirs: true, notFailBuild: true
    } catch (Exception ex) {
        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
    }
}

def sendEmailNotification(Map config, String status) {
    String recipientList = ""
    if (status == "always") recipientList = config.alwaysEmailList
    if (status == "changed") recipientList = config.changedEmailList
    if (status in ["aborted", "failed"]) recipientList = config.failedEmailList
    if (status == "success") recipientList = config.successEmailList

    if (!recipientList) {
        log.debug("No recipients defined for email trigger condition: ${status}")
        return
    }
    log.info("recipientList=${recipientList}")

    String emailSubject = "Jenkins Build Notification [${status.toUpperCase()}]: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    String emailBody = "The pipeline execution task finished with status '${status.toUpperCase()}'. Check details at ${env.BUILD_URL}"

    log.info("Sending status notification tracking info to: ${recipientList}")
    emailext subject: emailSubject, body: emailBody, to: recipientList

//     sendEmail(currentBuild, env, emailAdditionalDistList: recipientList.split(","))

}
