#!/usr/bin/env groovy
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

//     log.enableDebug()
    Map config = loadAnsibleConfigs(params)
    // run on native OS
    config.get('useDockerAnsibleInstallation', false)
    config.get('ansibleInstallation', "ansible-venv")
    config.get('jenkinsNodeLabel', "ansible")

    String ansibleLogSummary = "No results"

    pipeline {
        agent {
            label config.jenkinsNodeLabel
        }
        tools {
            // ref: https://webapp.chatgpt4google.com/s/MzY5MjYw
            // ref: https://stackoverflow.com/questions/47895668/how-to-select-multiple-jdk-version-in-declarative-pipeline-jenkins#48368506
            ansible "${config.ansibleInstallation}"
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
			stage('Load pipeline config file') {
				when {
                    allOf {
                        expression { config.ansiblePipelineConfigFile }
                        expression { fileExists config.ansiblePipelineConfigFile }
                    }
				}
				steps {
					script {
                        config = loadPipelineConfigFile(config)
                        log.info("Merged config=${JsonUtils.printToJsonString(config)}")
					}
				}
			}
            stage('Prepare Ansible Environment') {
                when {
                    expression { config.ansibleCollectionsRequirements || config.ansibleRolesRequirements }
                }
                steps {
                    script {
                        // ref: https://stackoverflow.com/questions/25785/delete-all-but-the-most-recent-x-files-in-bash
                        // ref: https://stackoverflow.com/questions/22407480/command-to-list-all-files-except-dot-and-dot-dot
//                         sh "cd /tmp/ && ls -Art1 | tail -n +20 | xargs -I {} rm -fr -- {} || true"
                        sh "cd /tmp/ && ls -Art1 | tail -n +${config.tmpDirMaxFileCount} | xargs -I {} rm -fr -- {} || true"

//                         String gitBranch = java.net.URLDecoder.decode(env.BRANCH_NAME, "UTF-8")
                        String gitBranch = java.net.URLDecoder.decode(env.GIT_BRANCH, "UTF-8")
                        config.get('gitBranch',"${gitBranch}")
                        config.gitCommitId = env.GIT_COMMIT
                        log.debug("config.gitBranch=${config.gitBranch}")
                        log.debug("config.gitCommitId=${config.gitCommitId}")

                        installAnsibleGalaxyDeps(config)
                    }
                }
            }

            stage('Run Ansible Playbook') {
                steps {
                    script {
                        runAnsiblePlaybook(config)
                        if (fileExists("ansible.log")) {
                            ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    notifyGitRemoteRepo(
                        config.gitRemoteRepoType,
                        gitRemoteBuildKey: config.gitRemoteBuildKey,
                        gitRemoteBuildName: config.gitRemoteBuildName,
                        gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                        gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                        gitCommitId: config.gitCommitId
                    )
                    List emailAdditionalDistList = []
                    if (config?.alwaysEmailDistList) {
                        emailAdditionalDistList = config.alwaysEmailDistList
                    }
                    if (config.gitBranch in ['origin/main','main']) {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList: emailAdditionalDistList)
                    } else {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result}, 'RequesterRecipientProvider')")
                        sendEmail(currentBuild, env)
                    }
                    if (!config.debugPipeline) {
                        log.info("Empty current workspace dir")
                        try {
                            cleanWs()
                        } catch (Exception ex) {
                            log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                        }
                    } else {
                        log.info("Skipping cleanup of current workspace directory since config.debugPipeline == true")
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

Map loadPipelineConfigFile(Map config) {

    Map ansiblePipelineConfigMap = readYaml file: config.ansiblePipelineConfigFile
    log.debug("ansiblePipelineConfigMap=${JsonUtils.printToJsonString(ansiblePipelineConfigMap)}")

    ansibleRootConfigMap=ansiblePipelineConfigMap.findAll { !['repoList'].contains(it.key) }

    log.debug("config.ansibleRootConfigMap=${ansibleRootConfigMap}")
    config = MapMerge.merge(config, ansibleRootConfigMap)

    if (!config.ansiblePlaybookRepo) {
        return config
    }
    log.debug("config.ansiblePlaybookRepo=${config.ansiblePlaybookRepo}")

    if (!ansiblePipelineConfigMap.repoList) {
        return config
    }

    if (ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]) {
        Map ansibleRepoConfigMap = ansiblePipelineConfigMap.repoList[config.ansiblePlaybookRepo]
        log.debug("ansibleRepoConfigMap=${JsonUtils.printToJsonString(ansibleRepoConfigMap)}")

        ansibleRepoConfigMap=ansibleRepoConfigMap.findAll { !['SANDBOX','DEV','QA','PROD'].contains(it.key) }

        if (ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]["${config.environment}"]) {
            Map ansibleEnvConfigMap = ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]["${config.environment}"]
            log.debug("ansibleEnvConfigMap=${JsonUtils.printToJsonString(ansibleEnvConfigMap)}")

            log.debug("Merge ansibleEnvConfigMap to ansibleRepoConfigMap")
            ansibleRepoConfigMap = MapMerge.merge(ansibleRepoConfigMap, ansibleEnvConfigMap)
        }
        log.debug("ansibleRepoConfigMap=${JsonUtils.printToJsonString(ansibleRepoConfigMap)}")

        log.debug("Merge ansibleRepoConfigMap to config map")
        config = MapMerge.merge(config, ansibleRepoConfigMap)
    }
//     if (config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir
//         && !config.ansibleCollectionsRequirements.startsWith(config.ansiblePlaybookDir))
    if (config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config?.ansiblePlaybookDir) {
        log.debug("Prepend ansiblePlaybookDir to config.ansibleCollectionsRequirements")
        config.ansibleCollectionsRequirements = "${config.ansiblePlaybookDir}/${config.ansibleCollectionsRequirements}"
        log.debug("Modified config.ansibleCollectionsRequirements=${config.ansibleCollectionsRequirements}")
    }

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

Map getAnsibleCommandConfig(Map config) {

    String ansiblePlaybook = "${config.ansiblePlaybook}"
    if (config.ansiblePlaybookDir) {
        ansiblePlaybook = "${config.ansiblePlaybookDir}/${config.ansiblePlaybook}"
    }

    Map ansibleCfg = [
        (ANSIBLE) : [
            (ANSIBLE_PLAYBOOK)        : "${ansiblePlaybook}",
            (ANSIBLE_COLORIZED)       : true,
            (ANSIBLE_DISABLE_HOST_KEY_CHECK): true,
//             (ANSIBLE_EXTRA_PARAMETERS): [],
//             (ANSIBLE_INSTALLATION)    : "ansible-local-bin",
//             (ANSIBLE_INVENTORY)       : "${config.ansibleInventory}",
//             (ANSIBLE_TAGS)            : "${config.ansibleTags}",
//             (ANSIBLE_SKIPPED_TAGS)    : "${config.ansibleSkipTags}",
//             (ANSIBLE_CREDENTIALS_ID)  : "${config.ansibleSshCredId}",
//             (ANSIBLE_SUDO_USER)       : "root",
//             (ANSIBLE_EXTRA_VARS)      : [
//                "ansible_python_interpreter" : "/usr/bin/python3"
//             ]
        ]
    ]

    if (config.containsKey('ansibleInstallation')) {
        ansibleCfg[ANSIBLE][ANSIBLE_INSTALLATION]=config.ansibleInstallation
    }
    if (config.containsKey('ansibleInventory')) {
        ansibleCfg[ANSIBLE][ANSIBLE_INVENTORY]=config.ansibleInventory
    }
    if (config.containsKey('ansibleTags')) {
        ansibleCfg[ANSIBLE][ANSIBLE_TAGS]=config.ansibleTags
    }
    if (config.containsKey('ansibleSkipTags')) {
        ansibleCfg[ANSIBLE][ANSIBLE_SKIPPED_TAGS]=config.ansibleSkipTags
    }
    if (config.containsKey('ansibleSshCredId')) {
        ansibleCfg[ANSIBLE][ANSIBLE_CREDENTIALS_ID]=config.ansibleSshCredId
    }
    if (config.containsKey('ansibleVaultCredId')) {
        ansibleCfg[ANSIBLE][ANSIBLE_VAULT_CREDENTIALS_ID]=config.ansibleVaultCredId
    }
    if (config.containsKey('ansibleLimitHosts')) {
        ansibleCfg[ANSIBLE][ANSIBLE_LIMIT]=config.ansibleLimitHosts
    }
    if (config?.ansibleLogLevel) {
        ansibleCfg[ANSIBLE][ANSIBLE_LOG_LEVEL]=config.ansibleLogLevel
    }

    // ANSIBLE EXTRA VARS
    Map extraVars = [:]
    if (config.containsKey('ansibleExtraVars')) {
        extraVars+=config.ansibleExtraVars
    }
    config.get('isTestPipeline', false)

    if (config.isTestPipeline) {
//         config.testBasePath = "${env.WORKSPACE}/${config.testBaseDir}"
//         config.testBasePath = "${env.WORKSPACE_TMP}/${config.testBaseDir}"
        config.testBasePath = "${env.WORKSPACE}/${config.testBaseDir}"
        config.testComponentDir = "${config.testComponentBaseDir}/${config.gitBranch}"

        extraVars.test_job__test_base_dir = config.testBasePath
        extraVars.test_git_branch = config.gitBranch
        extraVars.test_git_commit_hash = config.gitCommitId

        extraVars.test_job_url = env.BUILD_URL
    }
    if (config.containsKey('ansiblePythonInterpreter')) {
        extraVars+=["ansible_python_interpreter" : config.ansiblePythonInterpreter]
    }
    if (extraVars.size()>0) {
        log.debug("extraVars=${JsonUtils.printToJsonString(extraVars)}")
        ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_VARS]=extraVars
    }

    // ANSIBLE EXTRA PARAMS
    List ansibleExtraParams = []
    if (config.containsKey('ansibleDebugFlag')) {
        ansibleExtraParams+=[config.ansibleDebugFlag]
    }
    if (config.containsKey('ansibleCheckMode')) {
        ansibleExtraParams+=['--check']
    }
    if (config.containsKey('ansibleDiffMode')) {
        ansibleExtraParams+=['--diff']
    }
    if (config.containsKey('ansibleVarFiles')) {
        config.ansibleVarFiles.each { String varFile ->
            String extraVarFileParam = "-e @${varFile}"
            if (config.varFilesRelativeToPlaybookDir?.toBoolean() && config.ansiblePlaybookDir) {
                extraVarFileParam = "-e @${config.ansiblePlaybookDir}/${varFile}"
            }
            ansibleExtraParams+=[extraVarFileParam]
        }
    }
    if (config.containsKey('ansibleExtraParams')) {
        config.ansibleExtraParams.each { String extraParam ->
            ansibleExtraParams+=[extraParam]
        }
    }
    if (ansibleExtraParams.size()>0) {
        ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]=ansibleExtraParams
    }

    return ansibleCfg
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}
