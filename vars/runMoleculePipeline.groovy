#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-to-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)


def call() {

    List paramList = []
    List moleculeCommandTypes = [
        'test',
        'check',
        'converge',
        'create',
        'dependency',
        'drivers',
        'idempotence',
        'init',
        'list',
        'matrix',
        'prepare',
        'reset',
        'side',
        'syntax',
        'verify',
    ]
    List logLevelsList = [
        'INFO',
        'DEBUG',
        'WARN',
        'ERROR'
    ]
    Map paramMap = [
        initializeParamsOnly: booleanParam(defaultValue: false, description: "Set to true to only initialize parameters and skip execution of stages.", name: 'InitializeParamsOnly'),
        moleculeCommand : choice(choices: moleculeCommandTypes.join('\n'), description: "Choose molecule command", name: 'MoleculeCommand'),
        moleculeImageRegistry: string(defaultValue: "media.johnson.int:5000", description: "Specify the molecule image registry (e.g., 'registry.example.int:5000')", name: 'MoleculeImageRegistry'),
        moleculeImage: string(defaultValue: "ubuntu2404-systemd-python", description: "Specify the molecule image (e.g., 'ubuntu2404-systemd-python', 'centos9-systemd-python')", name: 'MoleculeImage'),
        moleculeScenario: string(defaultValue: "bootstrap_linux_package", description: "Specify the molecule scenario (e.g., 'bootstrap_docker', 'bootstrap_java', 'bootstrap_linux', 'bootstrap_linux_package')", name: 'MoleculeScenario'),
        moleculeDebugFlag: booleanParam(defaultValue: false, description: "Set to enable molecule debug.", name: 'MoleculeDebugFlag'),
        gitRepoUrl: string(defaultValue: "ssh://git@gitea.admin.dettonville.int:2222/infra/ansible-datacenter.git", description: "Specify the git repo image URL", name: 'GitRepoUrl'),
        gitRepoBranch: string(defaultValue: "main", description: "Specify the git repo branch", name: 'GitRepoBranch'),
        gitCredentialsId: string(defaultValue: "jenkins-ansible-ssh", description: "Specify the git repo credential ID", name: 'GitCredentialsId'),
        runnerImageName: string(defaultValue: "ansible/ansible-runner", name: "RunnerImageName"),
        runnerRegistry: string(defaultValue: "media.johnson.int:5000", name: "RunnerRegistry"),
        registryCredentialsId: string(defaultValue:  "docker-registry-admin", description: "Specify the Registry Credential Id", name: "RegistryCredentialsId"),
        ansibleVersion: string(defaultValue: "2.19", name: "AnsibleVersion"),
        pythonVersion: string(defaultValue: "3.12", name: "PythonVersion"),
        preTestCmd: string(defaultValue: "", name: "PreTestCmd"),
        testResultsDir: string(defaultValue: '.test-reports', name: "TestResultsDir"),
        changedEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'changed' status", name: 'ChangedEmailList'),
        alwaysEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'always' status", name: 'AlwaysEmailList'),
        failedEmailList: string(defaultValue: "", description: "Specify the email recipients for job 'failed' status", name: 'FailedEmailList'),
        timeout: string(defaultValue: "4", description: "Specify the job timeout", name: 'Timeout'),
        timeoutUnit: string(defaultValue: "HOURS", description: "Specify the job timeout unit (HOURS, MINUTES, etc)", name: 'TimeoutUnit'),
        logLevel : choice(choices: logLevelsList.join('\n'), description: "Choose log level", name: 'LogLevel')
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList)
    ])

    log.info("Loading Default Configs")
    Map config = loadPipelineConfig(params)

    log.info("Running molecule inside docker container: ${config.runnerImage}")

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.runnerImage
                args config.runnerArgs
                registryUrl config.runnerRegistryUrl
                registryCredentialsId config.registryCredentialsId
                reuseNode true
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
                categories: ['ansible_test'],
                throttleEnabled: true,
                throttleOption: 'category'
            )
            // Explicitly allow the parent job (INFRA/repo-test-automation/ansible-utils/ansible-test-units/main)
            // or use the wildcard 'INFRA/repo-test-automation/**' as discussed earlier.
            // We'll use the absolute path for maximum security/specificity here:
//             copyArtifactPermission('*')
//             copyArtifactPermission('/**')
            copyArtifactPermission('/INFRA/repo-test-automation/*')
        }

        stages {
            stage ('Checkout SCM') {
                when {
                    // This stage will only run if InitializeParamsOnly is false
                    expression { return !config.initializeParamsOnly }
                }
                steps {
                    git credentialsId: config.gitCredentialsId,
                        url: config.gitRepoUrl,
                        branch: config.gitRepoBranch
                }
            }
            stage("Run Molecule Test") {
                when {
                    // This stage will only run if InitializeParamsOnly is false
                    expression { return !config.initializeParamsOnly }
                }
                steps {
                    script {
                        Map result = [:]
                        result = runMolecule(config)
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
}

//@NonCPS
Map loadPipelineConfig(Map params) {

    Map config = [:]

    log.info("copy immutable params map to mutable config map")

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
    config.get('maxRandomDelaySeconds', "0") as String

    config.get('jenkinsNodeLabel',"docker")

    // Apply default values if not provided by the matrix driver
    config.get("runnerRegistry", "media.johnson.int:5000")
    config.get("runnerRegistryUrl", "https://${config.runnerRegistry}")
    config.get("runnerImageName", "ansible/ansible-test")
    config.get("registryCredentialsId", "docker-registry-admin")

    List runnerArgsList = []
    runnerArgsList.push("-v /var/run/docker.sock:/var/run/docker.sock")
    runnerArgsList.push("--privileged")

    // configure to share the host's network stack.
    // This removes the network isolation between the container and the host, allowing the container
    // to access services running on the host via 127.0.0.1 or the host's primary IP address/
    runnerArgsList.push("--network host")

//     runnerArgsList.push("-u root")
    config.get("runnerArgs", runnerArgsList.join(" "))

    config.get("ansibleVersion", "2.19")
    config.get("pythonVersion", "3.13")

    config.runnerImage = getAnsibleDockerImageId(
                            dockerImageName: config.runnerImageName,
                            ansibleVersion: config.ansibleVersion,
                            pythonVersion: config.pythonVersion,
                            dockerRegistry: config.runnerRegistry)

    config.get("moleculeCommand", "test")
    config.get("moleculeImage", "ubuntu2404-systemd-python")
    config.get("moleculeImageRegistry", "media.johnson.int:5000")
    config.get("moleculeScenario", "bootstrap_linux_package")
    config.get("moleculeDebugFlag", false)

    config.get("preTestCmd", "")
    config.get('testResultsDir', '.test-reports')

    // Map boolean params to config for stashing decisions
    config.get("stashJobArtifacts", false)
    config.get("stashJobJunitResults", false)

    log.debug("params=${JsonUtils.printToJsonString(params)}")
    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}
