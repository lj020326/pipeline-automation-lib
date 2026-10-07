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
                                String giteaApiReleaseUrl = "${config.giteaApiRepoBaseUrl}/releases/tags/${config.codeIndexTagName}"

                                log.info("Fetching latest release artifacts from Gitea API: ${giteaApiReleaseUrl}")
                                try {
                                    // 1. Query Gitea API for release metadata
                                    def response = httpRequest(
                                        httpMode: 'GET',
                                        url: giteaApiReleaseUrl,
                                        acceptType: 'APPLICATION_JSON',
                                        authentication: config.giteaTokenCredentialId,
                                        validResponseCodes: '200'
                                    )

                                    // 2. Parse response JSON using Jenkins built-in readJSON step
                                    def releaseData = readJSON(text: response.content)
                                    def assets = releaseData.assets ?: []

                                    def targetAsset = assets.find { it.name.endsWith('.tar.gz') || it.name.endsWith('.zip') }
                                    if (targetAsset) {
                                        def downloadUrl = targetAsset.browser_download_url
                                        log.info("Downloading index package from: ${downloadUrl}")

                                        // 3. Download the archive directly to workspace file
                                        httpRequest(
                                            httpMode: 'GET',
                                            url: downloadUrl,
                                            authentication: config.giteaTokenCredentialId,
                                            outputFile: 'index_package.tar.gz',
                                            validResponseCodes: '200'
                                        )

                                        // 4. Extract and clean up archive
                                        sh """
                                            tar -xzf index_package.tar.gz -C ${config.indexDir}/
                                            rm -f index_package.tar.gz
                                        """
                                    } else {
                                        log.info("⚠️ No suitable release archive asset found in latest Gitea release.")
                                    }
                                } catch (Exception apiEx) {
                                    error("Failed to query Gitea API for release artifacts: ${apiEx.getMessage()}")
                                }
                            }
                        }
                    }

                    stage('Run CrewAI Documentation Agent') {
                        steps {
                            script {
                                log.info("Executing CrewAI Documentation Agent container...")
                                
                                List agentCommandList=["crewai-doc-agent run"]
                                if (config?.configYaml) {
                                    agentCommandList+=["--config ${config.configYaml}"]
                                }
//                                 if (config.changedOnly) {
//                                     agentCommandList+=["--changed-only"]
//                                 }
                                if (config?.verbosity) {
                                    agentCommandList+=[config.verbosity]
                                }
                                sh "${agentCommandList.join(' ')}"
                            }
                        }
                    }

                    stage('Commit Generated Documentation') {
                        steps {
                            script {
                                def prBranchName = "wiki-update-build-${env.BUILD_NUMBER}"

                                // Use CHANGE_TARGET when running in a PR context, falling back to 'main'
                                def baseBranch = env.CHANGE_TARGET ?: 'main'

                                // This block injects the gitea-ssh-jenkins key into the shell environment
                                sshagent([config.gitSSHCredentialsId]) {
                                    sh """
                                        # 1. Standard SSH Setup
                                        mkdir -p ~/.ssh && chmod 700 ~/.ssh

                                        # Scan the Gitea host key and add it to known_hosts to prevent verification failure
                                        # We use -p 2222 because your Gitea is on a non-standard port
                                        ssh-keyscan -p 2222 gitea.admin.dettonville.int >> ~/.ssh/known_hosts

                                        # 2. Configure Identity
                                        git config user.name "Jenkins Wiki Bot"
                                        git config user.email "jenkins@dettonville.com"

                                        # 3. Create or reset the isolated PR branch safely
                                        git checkout -B ${prBranchName}

                                        # 4. Stage local changes
                                        git add . || true

                                        if git diff --cached --quiet; then
                                            echo "No changes to commit"
                                        else
                                            git commit -m "chore(wiki): auto-update from LLM pipeline [skip ci] [ci skip] ***NO_CI***"

                                            # 5. Handle race conditions: Fetch current state of upstream base branch and rebase
                                            git fetch origin ${baseBranch}

                                            # Use 'git rebase -X theirs' to automatically resolve conflicts
                                            # in favor of the newly generated Wiki content
                                            git rebase -X theirs origin/${baseBranch}

                                            # 6. Push the rebased PR branch to remote origin
                                            git push origin ${prBranchName} --force
                                        fi
                                    """

                                    // 7. Automatically open a Pull Request via Gitea API if the branch was pushed
                                    String branchExists = sh(script: "git ls-remote --heads origin ${prBranchName}", returnStdout: true).trim()
                                    if (branchExists) {
                                        String payload = JsonUtils.printToJsonString([
                                            head: prBranchName,
                                            base: baseBranch,
                                            title: "Automated Wiki Documentation Update (#${env.BUILD_NUMBER}) [skip ci] [ci skip] ***NO_CI***",
                                            body: "Automated pipeline run generated updated wiki documentation files for review against ${baseBranch}."
                                        ])

                                        try {
                                            // Delegate credential handling to the plugin's native authentication parameter.
                                            // This removes the need for both the 'withCredentials' wrapper block
                                            // and the explicit 'customHeaders' token token assignment.
                                            httpRequest httpMode: 'POST',
                                                        contentType: 'APPLICATION_JSON',
                                                        requestBody: payload,
                                                        url: config.giteaApiRepoPullsUrl,
                                                        authentication: config.gitCredentialsId,
                                                        quiet: true,
                                                        responseHandle: 'NONE'
                                            log.info("🚀 Pull Request created successfully via Gitea API for branch ${prBranchName} targeting ${baseBranch}.")
                                        } catch (Exception apiEx) {
                                            log.error("Gitea direct REST API notification failed: ${apiEx.getMessage()}")
                                        }
                                    } else {
                                        log.info("ℹ️ No PR branch pushed (no changes detected).")
                                    }
                                    config.gitRemoteBuildStatus = "SUCCESSFUL"
                                    currentBuild.result = "SUCCESS"
                                }
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
    config.get('runnerImage', 'lj020326/crewai-doc-agent:latest')

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

    config.get('configYaml', '.crewai-config.yml')

    config.get('changedOnly', true)

    config.get('verbosity', "-v")

//     List secretVars=[
//         string(credentialsId: 'ollama-api-key', variable: 'LLM_API_KEY'),
//     ]
//     config.secretVars = secretVars

    log.debug("agent config=${JsonUtils.printToJsonString(config)}")
    return config
}
