#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params = [:]) {

    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            docker {
                label config.jenkinsNodeLabel
                image config.runnerImage
                args config.runnerArgs
//                 args '--pull=always'
                reuseNode true
                // This force-checks the registry for a newer version
                alwaysPull true
            }
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
            stage('Check Skip') {
                steps {
                    // This plugin is in your plugins.txt and works regardless of the Job DSL UI
                    scmSkip(skipPattern: '.*\\[(ci skip|skip ci)\\].*')
                }
            }

            stage('Wiki: Checkout & Setup') {
                steps {
                    script {
                        log.info("Starting wiki pipeline for ${env.JOB_NAME}")
                        checkout scm
                        config.gitCommitId = env.GIT_COMMIT

                        if (fileExists(config?.configFile)) {
                            config = loadWikiConfigFile(config)
                        }
                    }
                }
            }

            stage('Wiki: Harvest Legacy Markdown') {
                when {
                    // This stage will only run if config.skipHarvest is false
                    expression { return !config.skipHarvest }
                }
                steps {
                    script {
                        List harvestCommandList=["wiki-pipeline harvest"]
                        if (config.changedOnly) {
                            harvestCommandList+=["--changed-only"]
                        }
                        if (config?.harvestVerbosity) {
                            harvestCommandList+=[config.harvestVerbosity]
                        }
                        if (config?.harvestLimit) {
                            harvestCommandList+=["--limit ${config.harvestLimit}"]
                        }
                        sh "${harvestCommandList.join(' ')}"
                    }
                }
            }

            stage('Wiki: Ingest Ansible YAML') {
                when {
                    // This stage will only run if config.skipIngest is false
                    expression { return !config.skipIngest }
                }
                steps {
                    script {
                        List ingestCommandList=["wiki-pipeline ingest"]
                        if (config.changedOnly) {
                            ingestCommandList+=["--changed-only"]
                        }
                        if (config?.ingestVerbosity) {
                            ingestCommandList+=[config.ingestVerbosity]
                        }
                        if (config?.ingestLimit) {
                            ingestCommandList+=["--limit ${config.ingestLimit}"]
                        }

                        sh "${ingestCommandList.join(' ')}"
                    }
                }
            }

            stage('Wiki: Compile raw → wiki/') {
                when {
                    // This stage will only run if config.skipCompile is false
                    expression { return !config.skipCompile }
                }
                steps {
                    script {
                        List compileCommandList=["wiki-pipeline compile"]
                        if (config.changedOnly) {
                            compileCommandList+=["--changed-only"]
                        }
                        if (config?.compileVerbosity) {
                            compileCommandList+=[config.compileVerbosity]
                        }
                        if (config?.compileLimit) {
                            compileCommandList+=["--limit ${config.compileLimit}"]
                        }
                        sh "${compileCommandList.join(' ')}"
                    }
                }
            }

            stage('Wiki: Lint & Auto-fix') {
                when {
                    // This stage will only run if config.skipLint is false
                    expression { return !config.skipLint }
                }
                steps {
                    script {
                        List lintCommandList=["wiki-pipeline lint --fix"]
                        if (config.changedOnly) {
                            lintCommandList+=["--changed-only"]
                        }
                        if (config?.lintVerbosity) {
                            lintCommandList+=[config.lintVerbosity]
                        }
                        if (config?.lintLimit) {
                            lintCommandList+=["--limit ${config.lintLimit}"]
                        }
                        sh "${lintCommandList.join(' ')}"
                    }
                }
            }

            stage('Wiki: Re-index backlinks & categories') {
                steps {
                    script {
                        List indexCommandList=["wiki-pipeline index"]
                        if (config?.indexVerbosity) {
                            indexCommandList+=[config.indexVerbosity]
                        }
                        sh "${indexCommandList.join(' ')}"
                    }
                }
            }

            stage('Wiki: Generate media (slides, charts)') {
                when {
                    // This stage will only run if config.skipMedia is false
                    expression { return !config.skipMedia }
                }
                steps {
                    script {
                        List generateCommandList=["wiki-pipeline generate-media"]
                        if (config?.mediaVerbosity) {
                            generateCommandList+=[config.mediaVerbosity]
                        }
                        sh "${generateCommandList.join(' ')}"
                    }
                }
            }

            stage('Wiki: Commit & Push updates') {
                steps {
                    script {
                        // This block is what "injects" the gitea-ssh-jenkins key into the shell environment
                        sshagent([config.gitCredentialsId]) {
                            sh """
                                # 1. Standard SSH Setup
                                mkdir -p ~/.ssh && chmod 700 ~/.ssh

                                # Scan the Gitea host key and add it to known_hosts to prevent verification failure
                                # We use -p 2222 because your Gitea is on a non-standard port
                                ssh-keyscan -p 2222 gitea.admin.dettonville.int >> ~/.ssh/known_hosts

                                # 2. Configure Identity
                                git config user.name "Jenkins Wiki Bot"
                                git config user.email "jenkins@dettonville.com"

                                # 3. Stage and Commit local changes
                                git add . || true

                                if git diff --cached --quiet; then
                                    echo "No changes to commit"
                                else
                                    git commit -m "chore(wiki): auto-update from LLM pipeline [skip ci] [ci skip] ***NO_CI***"

                                    # 4. Handle race conditions: Fetch and Rebase
                                    # This pulls latest changes and puts your 'chore' commit on top
                                    git fetch origin ${env.BRANCH_NAME}

                                    # Use 'git rebase -X theirs' to automatically resolve conflicts
                                    # in favor of the newly generated Wiki content
                                    git rebase -X theirs origin/${env.BRANCH_NAME}

                                    # 5. Push the rebased history
                                    git push origin HEAD:${env.BRANCH_NAME}
                                fi
                            """
                        }
                    }
                }
            }
        }
        post {
            always {
                script {

                    // ref: https://www.jenkins.io/doc/pipeline/steps/stashNotifier/
                    notifyGitRemoteRepo(
                    	config.gitRemoteRepoType,
                        gitRemoteBuildKey: config.gitRemoteBuildKey,
                        gitRemoteBuildName: config.gitRemoteBuildName,
                        gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                        gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                        gitCommitId: config.gitCommitId
                    )

                    log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result}, 'default')")
                    sendEmail(currentBuild, env)
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace: ", ex.getMessage())
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
                        sendEmail(currentBuild, env,
                            emailAdditionalDistList: config.failedEmailList.split(","),
                            emailBody: ansibleLogSummary
                        )
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env,
                            emailAdditionalDistList: config.failedEmailList.split(","),
                            emailBody: ansibleLogSummary
                        )
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

    params.each { key, value ->
        key = Utilities.decapitalize(key)
        if (value != "") config[key] = value
    }

    config.get('logLevel', "INFO")
    log.setLevel(config.logLevel)

    // defaults (overrideable per-repo via params or .jenkins/wiki-config.yml)
    config.get('jenkinsNodeLabel', 'docker')
    config.get('timeout', 4)
    config.get('timeoutUnit', 'HOURS')
    config.get('skipDefaultCheckout', false)
    config.get("runnerImage", "media.johnson.int:5000/wiki-pipeline:latest")

    List runnerArgsList = []
    if (config?.runnerUid && config?.runnerGid) {
        runnerArgsList.push("-u ${config.runnerUid}:${config.runnerGid}")
    } else {
        runnerArgsList.push("-u root:root")
        runnerArgsList.push("--privileged")
    }
    // configure to share the host's network stack.
    // This removes the network isolation between the container and the host, allowing the container
    // to access services running on the host via 127.0.0.1 or the host's primary IP address/
    runnerArgsList.push("--network host")
    // allows process to have more control over signaling host ssh-agent process
    runnerArgsList.push("-e SSH_AUTH_SOCK")
//     runnerArgsList.push("-v ${env.SSH_AUTH_SOCK}:${env.SSH_AUTH_SOCK}")
    // required to trust internal ca certificates
    runnerArgsList.push("-v /etc/ssl/certs/ca-certificates.crt:/etc/ssl/certs/ca-certificates.crt:ro")

    config.get("runnerArgs", runnerArgsList.join(" "))

    // LLM defaults (exactly as you requested)
//     config.get('openaiApiBase', 'http://gpu02.johnson.int:11434/v1')
//     config.get('llmModel', 'qwen2.5-coder:32b')

    // git
    config.get('gitCredentialsId', 'gitea-ssh-jenkins')
//     config.get('gitCredentialsId', 'git-ssh-jenkins')
    config.get('gitRemoteRepoType', 'gitea')

    config.get('configFile', ".wiki-config.yml")

    config.get('changedOnly', true)

    config.get('defaultVerbosity', "-v")
    config.get('harvestVerbosity', config.defaultVerbosity)
    config.get('ingestVerbosity', config.defaultVerbosity)
//    config.get('compileVerbosity', config.defaultVerbosity)
    config.get('compileVerbosity', "-vv")
    config.get('lintVerbosity', config.defaultVerbosity)
    config.get('indexVerbosity', config.defaultVerbosity)
    config.get('mediaVerbosity', config.defaultVerbosity)

//     config.get('defaultLimit', 20)
    config.get('defaultLimit', 5)

    config.get('ingestLimit', config.defaultLimit)
    config.get('compileLimit', config.defaultLimit)
    config.get('lintLimit', config.defaultLimit)

    config.get('skipHarvest', false)
    config.get('skipIngest', false)
    config.get('skipLint', false)
    config.get('skipCompile', false)
    config.get('skipMedia', false)

    log.debug("wiki config=${JsonUtils.printToJsonString(config)}")
    return config
}

Map loadWikiConfigFile(Map baseConfig) {

    Map wikiConfigs = readYaml file: baseConfig.configFile
    log.debug("wikiConfigs=${JsonUtils.printToJsonString(wikiConfigs)}")

//     sh "cat ${baseConfig.configFile}"

//     Map config = baseConfig + wikiConfigs
    Map config = MapMerge.merge(baseConfig, wikiConfigs)

    log.debug("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}
