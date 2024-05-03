#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(Map params=[:]) {

    Logger log = new Logger(this, LogLevel.INFO)
//     log.setLevel(LogLevel.DEBUG)

    Map config = loadPipelineConfig(log, params)
    String ansibleLogSummary = "No results"

    pipeline {
        agent {
            label config.jenkinsNodeLabel
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
                        config = loadPipelineConfigFile(log, config)
                        log.info("Merged config=${JsonUtils.printToJsonString(config)}")
					}
				}
			}
            stage('Run initializations') {
                when {
                    expression { config.ansibleCollectionsRequirements || config.ansibleRolesRequirements }
                }
                tools {
                    // ref: https://webapp.chatgpt4google.com/s/MzY5MjYw
                    // ref: https://stackoverflow.com/questions/47895668/how-to-select-multiple-jdk-version-in-declarative-pipeline-jenkins#48368506
                    ansible "${config.ansibleInstallation}"
                }
                steps {
                    script {
                        // ref: https://stackoverflow.com/questions/25785/delete-all-but-the-most-recent-x-files-in-bash
                        // ref: https://stackoverflow.com/questions/22407480/command-to-list-all-files-except-dot-and-dot-dot
//                         sh "cd /tmp/ && ls -Art1 | tail -n +20 | xargs -I {} rm -fr -- {} || true"
                        sh "cd /tmp/ && ls -Art1 | tail -n +${config.tmpDirMaxFileCount} | xargs -I {} rm -fr -- {} || true"

//                         String gitBranch = java.net.URLDecoder.decode(env.BRANCH_NAME, "UTF-8")
                        String gitBranch = java.net.URLDecoder.decode(env.GIT_BRANCH, "UTF-8")
                        config.gitBranch = config.get('gitBranch',"${gitBranch}")
                        config.gitCommitHash = env.GIT_COMMIT
                        log.info("config.gitBranch=${config.gitBranch}")
                        log.info("config.gitCommitHash=${config.gitCommitHash}")

                        // install galaxy roles
                        List ansibleGalaxyArgList = []
                        if (config.ansibleGalaxyIgnoreCerts) {
                            ansibleGalaxyArgList.push("--ignore-certs")
                        }
                        if (config.ansibleGalaxyForceOpt) {
                            ansibleGalaxyArgList.push("--force")
                        }
                        // ref: https://docs.ansible.com/ansible/latest/collections_guide/collections_installing.html
                        if (config.ansibleGalaxyUpgradeOpt) {
                            ansibleGalaxyArgList.push("--upgrade")
                        }
                        String ansibleGalaxyArgs = ansibleGalaxyArgList.join(" ")

                        withEnv(config.ansibleGalaxyEnvVarsList) {
                            withCredentials(config.galaxySecretVarsListDefault) {
                                // ref: https://stackoverflow.com/questions/60756020/print-environment-variables-sorted-by-name-including-variables-with-newlines
//                                 sh "env -0 | sort -z | tr \'\0\' \'\n\'"
//                                 sh "export -p | sed 's/declare -x //' | sed 's/export //'"
                                sh "${config.ansibleGalaxyCmd} collection list"
                                sh "${config.ansibleGalaxyCmd} --version"
                                log.debug("config.ansibleCollectionsRequirements=${config.ansibleCollectionsRequirements}")
//                                 sh "tree -d"
//                                 sh "tree -d collections/"
                                if (config.ansibleCollectionsRequirements && fileExists(config.ansibleCollectionsRequirements)) {
                                    sh "${config.ansibleGalaxyCmd} collection install ${ansibleGalaxyArgs} -r ${config.ansibleCollectionsRequirements} || true"
                                }
                                if (config?.ansibleRolesRequirements && fileExists(config.ansibleRolesRequirements)) {
                                    sh "${config.ansibleGalaxyCmd} install ${ansibleGalaxyArgs} -r ${config.ansibleRolesRequirements} || true"
                                }
                            }
                        }
                    }
                }
            }

            stage('Run Ansible Playbook') {
                tools {
                    // ref: https://webapp.chatgpt4google.com/s/MzY5MjYw
                    // ref: https://stackoverflow.com/questions/47895668/how-to-select-multiple-jdk-version-in-declarative-pipeline-jenkins#48368506
                    ansible "${config.ansibleInstallation}"
                }
                steps {
                    script {

                        if ( config.ansibleInventoryDir && fileExists("${config.ansibleInventoryDir}/group_vars") ) {
                            sh "tree ${config.ansibleInventoryDir}/group_vars"
                        }

                        // load vaultIdList into secret vars
                        if (config.ansibleVaultIdList) {

                            config.ansibleVaultIdList.eachWithIndex { Map vaultConfig, idx ->
                                String jenkinsCredentialId = vaultConfig.jenkinsCredentialId
                                String ansibleVaultId = vaultConfig.ansibleVaultId
                                String envVarName = "VAULT_ID_${idx}"

                                config.ansibleSecretVarsList += [file(credentialsId: jenkinsCredentialId, variable: envVarName)]
                                // ref: https://stackoverflow.com/questions/46691502/launch-ansible-playbook-containing-vault-file-reference-from-jenkinsfile
                                ansibleExtraParams=['--vault-id ${' + ansibleVaultId + '}@${' + envVarName + '}']
                                config.ansibleExtraParams=ansibleExtraParams
                            }
                        }

                        if (config.isTestPipeline) {
                            // ref: https://github.com/jenkinsci/bitbucket-build-status-notifier-plugin
                            bitbucketStatusNotify(
                                buildKey: 'test',
                                buildName: 'Test',
                                buildState: 'INPROGRESS',
                                repoSlug: 'ansible-datacenter',
                                commitId: config.gitCommitHash
                            )
                            dir(config.testBaseDir) { deleteDir() }
                        }

                        withEnv(config.ansibleEnvVarsList) {
                            withCredentials(config.ansibleSecretVarsList) {

                                sh "export -p | sed 's/declare -x //' | sed 's/export //'"
                                Map ansibleConfig = getAnsibleCommandConfig(log, config)

                                config = MapMerge.merge(ansibleConfig, config)
                                log.info("config.ansible=${JsonUtils.printToJsonString(config.ansible)}")

                                sh "${config.ansibleCmd} --version"

                                // ref: https://stackoverflow.com/questions/44022775/jenkins-ignore-failure-in-pipeline-build-step#47789656
                                try {
                                    catchError{
                                        ansible.execPlaybook(config)
                                    }
                                    currentBuild.result = 'SUCCESS'
                                    config.bitbucketResult = "SUCCESSFUL"
                                } catch (hudson.AbortException ae) {
                                    config.bitbucketResult = "FAILED"
                                    currentBuild.result = 'FAILURE'
                                    // handle an AbortException
                                    // ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
                                    // ref: https://gist.github.com/stephansnyt/3ad161eaa6185849872c3c9fce43ca81
                                    if (manager.build.getAction(InterruptedBuildAction.class) ||
                                        // this ambiguous condition means a user _probably_ aborted, not sure if this one is really necessary
                                        (ae instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException && ae.causes.size() == 0)) {
                                        throw ae
                                    } else {
                                        ansibleLogSummary = "ansible.execPlaybook error: " + ae.getMessage()
                                        log.error("ansible.execPlaybook error: " + ae.getMessage())
                                    }
                                } finally {
                                    if (fileExists("ansible.log")) {
                                        ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                                    }

                                    if (config.isTestPipeline) {
                                        if (fileExists(config.testComponentDir)) {
    //                                         sh "tree ${config.testBasePath}/"
    //                                          archiveArtifacts allowEmptyArchive: true, artifacts: "${config.testBaseDir}/**"
    //                                         sh "tree ${config.testComponentDir}/"
                                            archiveArtifacts allowEmptyArchive: true, artifacts: "${config.testComponentDir}/**"
                                            publishHTML([allowMissing         : true,
                                                         alwaysLinkToLastBuild: true,
                                                         keepAll              : false,
                                                         reportDir            : "${config.testComponentDir}/",
                                                         reportFiles          : "${config.testComponentDir}/test-results.md",
                                                         reportName           : "Test Results for ${config.gitBranch}"])
                                        }
                                    }
                                    log.info("**** currentBuild.result=${currentBuild.result}")
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
                    if (config.isTestPipeline) {
                        bitbucketStatusNotify(
                            buildKey: 'test',
                            buildName: 'Test',
                            buildState: config.bitbucketResult,
                            repoSlug: 'ansible-datacenter',
                            commitId: config.gitCommitHash
                        )
                    }
                    List emailAdditionalDistList = []
                    if (config.alwaysEmailDistList) {
                        emailAdditionalDistList = config.alwaysEmailDistList
                    }
                    if (config.gitBranch in ['origin/main','main']) {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList=emailAdditionalDistList)
                    } else {
                        log.info("post(${config.gitBranch}): sendEmail(${currentBuild.result}, 'RequesterRecipientProvider')")
                        sendEmail(currentBuild, env, [[$class: 'RequesterRecipientProvider']])
                    }
                    log.info("Empty current workspace dir")
                    cleanWs()
                }
            }
        }
    }

} // body

//@NonCPS
Map loadPipelineConfig(Logger log, Map params) {
    String logPrefix="loadPipelineConfig():"
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("${logPrefix} params[${key}]=${value}")
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"ansible")
//     config.logLevel = config.get('logLevel', "INFO")
    config.logLevel = config.get('logLevel', "DEBUG")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')
    config.tmpDirMaxFileCount = config.get('tmpDirMaxFileCount', 100)

//    config.emailDist = config.emailDist ?: "ljohnson@dettonville.org"
    config.emailDist = config.get('emailDist',"ljohnson@dettonville.org")
    // config.alwaysEmailDist = config.alwaysEmailDist ?: "ljohnson@dettonville.org"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.skipDefaultCheckout = config.get('skipDefaultCheckout', false)
    config.gitPerformCheckout = config.get('gitPerformCheckout', !config.get('skipDefaultCheckout',false))

    config.varFilesRelativeToPlaybookDir = config.get('varFilesRelativeToPlaybookDir', false)
    config.inventoryPathRelativeToPlaybookDir = config.get('inventoryPathRelativeToPlaybookDir', false)
    config.requirementsPathsRelativeToPlaybookDir = config.get('requirementsPathsRelativeToPlaybookDir', false)

//     config.ansiblePlaybookDir = config.get('ansiblePlaybookDir', 'ansible-linux')
//     config.ansiblePlaybookDir = config.get('ansiblePlaybookDir',"ansible/${env.JOB_NAME.split('/')[-2]}")

    config.ansibleCollectionsRequirements = config.get('ansibleCollectionsRequirements', 'collections/requirements.yml')
    if (config.requirementsPathsRelativeToPlaybookDir?.toBoolean() && config.ansiblePlaybookDir) {
        config.ansibleCollectionsRequirements = "${config.ansiblePlaybookDir}/${config.ansibleCollectionsRequirements}"
    }

//     config.ansibleRolesRequirements = config.get('ansibleRolesRequirements', './roles/requirements.yml')
//    config.ansibleInventory = config.get('ansibleInventory', 'inventory')
//    config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    if ( config.ansibleInventory ) {
        if (config?.inventoryPathRelativeToPlaybookDir && config.inventoryPathRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir) {
            config.ansibleInventory = "${config.ansiblePlaybookDir}/${config.ansibleInventory}"
        }
        config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))
    }

    config.ansibleGalaxyIgnoreCerts = config.get('ansibleGalaxyIgnoreCerts', false)
    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', false)
    config.ansibleGalaxyUpgradeOpt = config.get('ansibleGalaxyUpgradeOpt', true)

    config.ansibleSshCredId = config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.ansibleVaultCredId = config.get('ansibleVaultCredId', 'ansible-vault-password-file')
// //     config.ansibleGalaxyTokenCredId = config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token-file')
//     config.ansibleGalaxyTokenCredId = config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token')
    config.ansiblePlaybook = config.get('ansiblePlaybook', 'site.yml')
    config.ansibleTags = config.get('ansibleTags', '')

    String ansibleGalaxyCmd = "ansible-galaxy"
    String ansibleCmd = "ansible"

    config.ansibleInstallation = config.get('ansibleInstallation', 'ansible-venv')
    config.ansibleGalaxyCmd = ansibleGalaxyCmd
    config.ansibleCmd = ansibleCmd

    config.ansibleGalaxyEnvVarsList = config.get('ansibleGalaxyEnvVarsList', [])
    config.galaxySecretVarsListDefault = config.get('galaxySecretVarsListDefault', [])

//     ansibleGalaxyEnvVarsList=[
//         "ANSIBLE_GALAXY_SERVER_LIST=published_repo,rh_certified,community_repo",
//         // published_repo
//         "ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_URL=https://ansible-galaxy.dettonville.int/api/galaxy/content/published/",
//         "ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_VALIDATE_CERTS=no",
//         // rh_certified
//         "ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_URL=https://ansible-galaxy.dettonville.int/api/galaxy/content/rh-certified/",
//         "ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_VALIDATE_CERTS=no",
//         // community_repo
//         "ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_URL=https://ansible-galaxy.dettonville.int/api/galaxy/content/community/",
//         "ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_VALIDATE_CERTS=no"
//     ]
//
//     config.ansibleGalaxyEnvVarsList = config.get('ansibleGalaxyEnvVarsList', ansibleGalaxyEnvVarsList)
//
//     List galaxySecretVarsListDefault=[
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_TOKEN'),
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_TOKEN'),
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_TOKEN')
//     ]
//
//     config.galaxySecretVarsListDefault = config.get('galaxySecretVarsListDefault', galaxySecretVarsListDefault)

    config.isTestPipeline = config.get('isTestPipeline', false)
    if (config.isTestPipeline) {
        config.testBaseDir = config.get('testBaseDir', "test-results")
    }

    config.ansibleEnvVarsList = config.get('ansibleEnvVarsList', [])

    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://docs.cloudbees.com/docs/cloudbees-ci/latest/cloud-secure-guide/injecting-secrets
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
    List secretVarsListDefault = []
//     List secretVarsListDefault=[
//         usernamePassword(credentialsId: 'ansible-ssh-password-linux', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//         sshUserPrivateKey(credentialsId: 'bitbucket-ssh-jenkins'),
//         string(credentialsId: 'awx-oauth-token', variable: 'TOWER_OAUTH_TOKEN'),
//         file(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_TOKEN_PATH')
//     ]

    config.ansibleSecretVarsList = config.get('ansibleSecretVarsList', secretVarsListDefault)

    log.debug("${logPrefix} params=${params}")
    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

Map loadPipelineConfigFile(Logger log, Map config) {
    String logPrefix="loadPipelineConfigFile():"

    Map ansiblePipelineConfigMap = readYaml file: config.ansiblePipelineConfigFile
    log.debug("${logPrefix} ansiblePipelineConfigMap=${JsonUtils.printToJsonString(ansiblePipelineConfigMap)}")

    ansibleRootConfigMap=ansiblePipelineConfigMap.findAll { !['repoList'].contains(it.key) }

    log.debug("${logPrefix} config.ansibleRootConfigMap=${ansibleRootConfigMap}")
    config = MapMerge.merge(config, ansibleRootConfigMap)

    if (!config.ansiblePlaybookRepo) {
        return config
    }
    log.info("${logPrefix} config.ansiblePlaybookRepo=${config.ansiblePlaybookRepo}")

    if (!ansiblePipelineConfigMap.repoList) {
        return config
    }

    if (ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]) {
        Map ansibleRepoConfigMap = ansiblePipelineConfigMap.repoList[config.ansiblePlaybookRepo]
        log.debug("${logPrefix} ansibleRepoConfigMap=${JsonUtils.printToJsonString(ansibleRepoConfigMap)}")

        ansibleRepoConfigMap=ansibleRepoConfigMap.findAll { !['SANDBOX','DEV','QA','PROD'].contains(it.key) }

        if (ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]["${config.environment}"]) {
            Map ansibleEnvConfigMap = ansiblePipelineConfigMap.repoList["${config.ansiblePlaybookRepo}"]["${config.environment}"]
            log.info("${logPrefix} ansibleEnvConfigMap=${JsonUtils.printToJsonString(ansibleEnvConfigMap)}")

            log.info("${logPrefix} Merge ansibleEnvConfigMap to ansibleRepoConfigMap")
            ansibleRepoConfigMap = MapMerge.merge(ansibleRepoConfigMap, ansibleEnvConfigMap)
        }
        log.info("${logPrefix} ansibleRepoConfigMap=${JsonUtils.printToJsonString(ansibleRepoConfigMap)}")

        log.info("${logPrefix} Merge ansibleRepoConfigMap to config map")
        config = MapMerge.merge(config, ansibleRepoConfigMap)
    }
//     if (config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir
//         && !config.ansibleCollectionsRequirements.startsWith(config.ansiblePlaybookDir))
    if (config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config?.ansiblePlaybookDir) {
        log.info("${logPrefix} Prepend ansiblePlaybookDir to config.ansibleCollectionsRequirements")
        config.ansibleCollectionsRequirements = "${config.ansiblePlaybookDir}/${config.ansibleCollectionsRequirements}"
        log.info("${logPrefix} Modified config.ansibleCollectionsRequirements=${config.ansibleCollectionsRequirements}")
    }

    log.info("${logPrefix} Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}

Map getAnsibleCommandConfig(Logger log, Map config) {
    String logPrefix="getAnsibleCommandConfig():"

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
    config.isTestPipeline = config.get('isTestPipeline', false)

    if (config.isTestPipeline) {
//         config.testBasePath = "${env.WORKSPACE}/${config.testBaseDir}"
//         config.testBasePath = "${env.WORKSPACE_TMP}/${config.testBaseDir}"
        config.testBasePath = "${env.WORKSPACE}/${config.testBaseDir}"
        config.testComponentDir = "${config.testComponentBaseDir}/${config.gitBranch}"

        extraVars.test_job__test_base_dir = config.testBasePath
        extraVars.test_git_branch = config.gitBranch
        extraVars.test_git_commit_hash = config.gitCommitHash

        extraVars.test_job_url = env.BUILD_URL
    }
    if (config.containsKey('ansiblePythonInterpreter')) {
        extraVars+=["ansible_python_interpreter" : config.ansiblePythonInterpreter]
    }
    if (extraVars.size()>0) {
        log.info("extraVars=${JsonUtils.printToJsonString(extraVars)}")
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
