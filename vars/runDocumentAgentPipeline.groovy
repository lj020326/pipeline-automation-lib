#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call() {

    List paramList = []

    List debugVerbosityList = [
        "",
        "-v",
        "-vv",
        "-vvv",
        "-vvvv"
    ]

    Map paramMap = [
        initializeParamsOnly: booleanParam(defaultValue: false, description: "Set to true to only initialize parameters and skip execution of stages.", name: 'InitializeParamsOnly'),
        changedOnly: booleanParam(defaultValue: true, description: "Run wiki-pipeline commands for changed files only (uncheck to run for the entire repo)", name: 'ChangedOnly'),
        maxRoles: string(defaultValue: '', description: "Specify the maximum number of roles for ai agent to scan (expect long runtime: 2 tasks × N roles)", name: 'MaxRoles'),
        verbosity: choice(choices: debugVerbosityList.join('\n'), description: "Choose Verbosity Level", name: 'Verbosity'),
    ]
    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList)
    ])

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
        environment {
            LLM_API_KEY = credentials('ollama-api-key')
            OPENAI_API_KEY = credentials('ollama-api-key')
            LITELLM_LOCAL_MODEL_COST_MAP = 'True'
            LITELLM_SKIP_MODEL_INFO_QUERY = 'True'
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
//             skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
            stage('Documentation Agent: Execution') {
                when {
                    allOf {
                        expression { return !config.initializeParamsOnly }
                        branch 'main'
                    }
                }
                stages {
                    stage('Check Skip') {
                        steps {
                            // This plugin is in your plugins.txt and works regardless of the Job DSL UI
                            scmSkip(skipPattern: '.*\\[(ci skip|skip ci)\\].*')
                        }
                    }

                    stage('Setup Directory') {
                        steps {
                            script {
                                log.info("Starting code agent pipeline for ${env.JOB_NAME}")
                                config = loadGiteaConfigs(config)
                                sh "mkdir -p ${config.indexDir} ${config.agentStateDir}"
                            }
                        }
                    }

                    stage('Pull Index Assets from Gitea') {
                        steps {
                            script {
                                pullGiteaReleaseAsset(
                                    giteaApiRepoBaseUrl: config.giteaApiRepoBaseUrl,
                                    tagName: config.codeIndexTagName,
                                    credentialId: config.giteaTokenCredentialId,
                                    targetDir: config.indexDir
                                )
                            }
                        }
                    }

                    stage('Run CrewAI Documentation Agent') {
                        steps {
                            script {
                                log.info("Executing CrewAI Documentation Agent health check and workflow...")

                                List baseCommandList = ["crewai-doc-agent"]
                                if (config?.verbosity) {
                                    baseCommandList += [config.verbosity]
                                }

                                String configFlag = ""
                                if (config?.configFile && fileExists(config.configFile)) {
                                    echo "✅ Found ${config.configFile} configuration."
                                    sh "cat ${config.configFile}"
                                    configFlag = "--config ${config.configFile}"
                                } else {
                                    echo "⚠️ ${config.configFile} not found, using default configurations."
                                }

                                // 1. Run preliminary health check first (fail fast if endpoint is unreachable or auth fails)
                                log.info("🔍 Running pre-flight LLM connection health check...")
                                sh "${baseCommandList.join(' ')} healthcheck ${configFlag}".trim()

                                // 2. Run the actual agent workflow if health check passes
                                log.info("🚀 Starting CrewAI documentation agent run...")
                                List runCommandList = baseCommandList + ["run"]
                                if (configFlag) {
                                    runCommandList += [configFlag]
                                }
                                if (config?.changedOnly) {
                                    runCommandList += ["--changed-only"]
                                }
                                sh "${runCommandList.join(' ')}"
                            }
                        }
                    }

                    stage('Commit Generated Documentation') {
                        steps {
                            script {
                                def prBranchName = "wiki-update-build-${env.BUILD_NUMBER}"
                                def baseBranch = env.CHANGE_TARGET ?: 'main'

                                boolean prCreated = createGiteaPullRequest(
                                    prBranchName: prBranchName,
                                    baseBranch: baseBranch,
                                    commitMessage: "chore(wiki): auto-update from LLM pipeline [skip ci] [ci skip] ***NO_CI***",
                                    prTitle: "Automated Wiki Documentation Update (#${env.BUILD_NUMBER}) [skip ci] [ci skip] ***NO_CI***",
                                    prBody: "Automated pipeline run generated updated wiki documentation files for review against ${baseBranch}.",
                                    gitSSHCredentialsId: config.gitSSHCredentialsId,
                                    gitCredentialsId: config.gitCredentialsId,
                                    giteaApiRepoPullsUrl: config.giteaApiRepoPullsUrl,
                                    gitUserName: "Jenkins Wiki Bot",
                                    gitUserEmail: "jenkins@dettonville.com"
                                )

                                config.gitRemoteBuildStatus = "SUCCESSFUL"
                                currentBuild.result = "SUCCESS"
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    config.gitRemoteBuildStatus = "COMPLETED"
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
                        cleanWs notFailBuild: true
//                         cleanWs()
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

    config.gitCommitId = env.GIT_COMMIT

    // defaults (overrideable per-repo via params or .jenkins/wiki-config.yml)
    config.get('jenkinsNodeLabel', 'docker')
    config.get('timeout', 4)
    config.get('timeoutUnit', 'HOURS')
    config.get('skipDefaultCheckout', true)
    config.get('indexDir', '.code_index')
    config.get('agentStateDir', '.agent_state')
    config.get('codeIndexTagName', 'code-index')
    config.get('maxRoles', 5)
//     config.get('runnerImage', 'lj020326/crewai-doc-agent:latest')
    config.get("runnerImage", "media.johnson.int:5000/crewai-doc-agent:latest")

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

    // Force LiteLLM to stay strictly offline regarding model info/costs
    runnerArgsList.push("-e LITELLM_LOCAL_MODEL_COST_MAP=True")
    runnerArgsList.push("-e LITELLM_SKIP_MODEL_INFO_QUERY=True")

    // Add CA bundle environment variables to runner args for SSL trust
    runnerArgsList.push("-e REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt")
    runnerArgsList.push("-e SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt")

    runnerArgsList.push("-e OTEL_SDK_DISABLED=true")
    runnerArgsList.push("-e CREWAI_DISABLE_TELEMETRY=true")

    // Emergency off-switch if LanceDB/embedder still misbehaves:
    // runnerArgsList.push("-e CREWAI_ENABLE_MEMORY=false")
    // LanceDB + local embedder
    runnerArgsList.push("-e CREWAI_ENABLE_MEMORY=true")

    // Avoid api.openai.com
    runnerArgsList.push("-e CREWAI_EMBED_MODEL=nomic-embed-text")

    // Smoke-test first N roles
    if (config?.maxRoles) {
        runnerArgsList.push("-e CREWAI_MAX_ROLES=${config.maxRoles}")
    }
    runnerArgsList.push("-e CREWAI_STORAGE_DIR=${config.agentStateDir}/crewai")

    if (config?.changedOnly) {
        runnerArgsList.push("-e CREWAI_CHANGED_ONLY=true")
    }
    runnerArgsList.push("-e CREWAI_GIT_BASE_REF=origin/${env.BRANCH_NAME}")

    // allows process to have more control over signaling host ssh-agent process
    runnerArgsList.push("-e SSH_AUTH_SOCK")

//     runnerArgsList.push("-v ${env.SSH_AUTH_SOCK}:${env.SSH_AUTH_SOCK}")
    // required to trust internal ca certificates
    runnerArgsList.push("-v /etc/ssl/certs/ca-certificates.crt:/etc/ssl/certs/ca-certificates.crt:ro")
    runnerArgsList.push("-v /var/jenkins_home/git_repo_references:/var/jenkins_home/git_repo_references")

    config.get("runnerArgs", runnerArgsList.join(" "))

    // LLM defaults (exactly as you requested)
//     config.get('openaiApiBase', 'http://gpu02.johnson.int:11434/v1')
//     config.get('llmModel', 'qwen2.5-coder:32b')

    // git
    config.get('gitSSHCredentialsId', 'gitea-ssh-jenkins')
//     config.get('gitSSHCredentialsId', 'git-ssh-jenkins')
    config.get('gitCredentialsId', 'infra-jenkins-git-user')
    config.get('gitRemoteRepoType', 'gitea')

    config.gitRemoteBuildStatus = "INPROGRESS"
    config.get("gitRemoteBuildKey", 'wiki-pipeline')
	config.get("gitRemoteBuildName", 'Wiki Pipeline')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

//     config.get('giteaTokenCredentialId', 'gitea-api-token')
    config.get('giteaTokenCredentialId', 'infra-jenkins-git-user')

    config.get('configFile', '.crewai-config.yml')

    config.get('changedOnly', true)
//     List secretVars=[
//         string(credentialsId: 'ollama-api-key', variable: 'LLM_API_KEY'),
//     ]
//     config.secretVars = secretVars

    log.debug("agent config=${JsonUtils.printToJsonString(config)}")
    return config
}
