#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import groovy.transform.Field
@Field Logger log = new Logger(this)

Map call(Map args=[:]) {

    Map config = args.clone()
//     log.enableDebug()
    log.info("config=${JsonUtils.printToJsonString(config)}")

    if ( config.ansibleInventoryDir && fileExists("${config.ansibleInventoryDir}/group_vars") ) {
        sh "tree ${config.ansibleInventoryDir}/group_vars"
//         sh "find ${config.ansibleInventoryDir}/group_vars -type f"
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

    // Use runWithConditionalEnvAndCredentials for non-SSH secrets
    runWithConditionalEnvAndCredentials(config.ansibleEnvVarsList, config.ansibleSecretVarsList) {
        if (log.isLogActive(LogLevel.DEBUG)) {
            sh "export -p | sed 's/declare -x //' | sed 's/export //'"
        }
        Map ansibleConfig = getAnsibleCommandConfig(config)
        config = MapMerge.merge(ansibleConfig, config)
        log.info("config.ansible=${JsonUtils.printToJsonString(config.ansible)}")

        // Wrap the Ansible playbook execution with the Jenkins sshagent step
        sshagent([config.ansibleSshCredId]) {
            sh 'echo SSH_AUTH_SOCK=$SSH_AUTH_SOCK SSH_AGENT_PID=$SSH_AGENT_PID'
            sh 'ssh-add -l'
            sh "${config.ansibleCmd} --version"
            try {
                ansible.execPlaybook(config)
                currentBuild.result = 'SUCCESS'
                config.gitRemoteBuildStatus = "SUCCESSFUL"
            } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                config.gitRemoteBuildStatus = "FAILED"
                currentBuild.result = 'FAILURE'
                // Check if the interruption was a user-initiated abort
                e.getCauses().each { cause ->
                    log.error("Cause: ${cause.getClass().getName()}")
                }
                // It is crucial to re-throw the exception to correctly mark the build as ABORTED
                throw e
            } catch (hudson.AbortException ae) {
                config.gitRemoteBuildStatus = "FAILED"
                currentBuild.result = 'FAILURE'
                // handle an AbortException
                // ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
                // ref: https://gist.github.com/stephansnyt/3ad161eaa6185849872c3c9fce43ca81
                log.error("ansible.execPlaybook abort error: " + ae.getMessage())
                throw ae
            } catch (Exception e) {
                // General catch-all for any other unexpected failures
                log.error("An unexpected error occurred: ${e.message}")
                config.gitRemoteBuildStatus = "FAILED"
                currentBuild.result = 'FAILURE'
                throw e
            } finally {
                log.info("**** config.gitRemoteBuildStatus=${config.gitRemoteBuildStatus}")
                log.info("**** currentBuild.result=${currentBuild.result}")
            }
        }
    }
    return config
} // body

Map getAnsibleCommandConfig(Map config) {
    log.debug("config=${JsonUtils.printToJsonString(config)}")
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

    if (config?.ansibleInstallation) {
        ansibleCfg[ANSIBLE][ANSIBLE_INSTALLATION]=config.ansibleInstallation
    }
    if (config?.ansibleInventory) {
        ansibleCfg[ANSIBLE][ANSIBLE_INVENTORY]=config.ansibleInventory
    }
    if (config?.ansibleTags) {
        ansibleCfg[ANSIBLE][ANSIBLE_TAGS]=config.ansibleTags
    }
    if (config?.ansibleSkipTags) {
        ansibleCfg[ANSIBLE][ANSIBLE_SKIPPED_TAGS]=config.ansibleSkipTags
    }
    // This is the line that was commented out
    // if (config?.ansibleSshCredId) {
    //     ansibleCfg[ANSIBLE][ANSIBLE_CREDENTIALS_ID]=config.ansibleSshCredId
    // }
    if (config?.ansibleVaultCredId) {
        ansibleCfg[ANSIBLE][ANSIBLE_VAULT_CREDENTIALS_ID]=config.ansibleVaultCredId
    }
    if (config?.ansibleLimitHosts) {
        ansibleCfg[ANSIBLE][ANSIBLE_LIMIT]=config.ansibleLimitHosts
    }
    if (config?.ansibleLogLevel) {
        ansibleCfg[ANSIBLE][ANSIBLE_LOG_LEVEL]=config.ansibleLogLevel
    }

    // ANSIBLE EXTRA VARS
    Map extraVars = [:]
    if (config?.ansibleExtraVars) {
        extraVars+=config.ansibleExtraVars
    }
    config.get('isTestPipeline', false)

    if (config?.ansiblePythonInterpreter) {
        extraVars+=["ansible_python_interpreter" : config.ansiblePythonInterpreter]
    }
    // Override both SSH private key variables to null for Jenkins runs (forces ssh-agent usage)
    extraVars+=["ansible_ssh_private_key_file" : null]
    extraVars+=["ansible_ssh_private_key" : null]
    if (extraVars.size()>0) {
        log.debug("extraVars=${JsonUtils.printToJsonString(extraVars)}")
        ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_VARS]=extraVars
    }

    // ANSIBLE EXTRA PARAMS
    List ansibleExtraParams = []
    if (config?.ansibleDebugFlag) {
        ansibleExtraParams+=[config.ansibleDebugFlag]
    }
    if (config?.ansibleCheckMode) {
        ansibleExtraParams+=['--check']
    }
    if (config?.ansibleDiffMode) {
        ansibleExtraParams+=['--diff']
    }
    if (config?.ansibleVarFiles) {
        config.ansibleVarFiles.each { String varFile ->
            String extraVarFileParam = "-e @${varFile}"
            if (config.varFilesRelativeToPlaybookDir?.toBoolean() && config.ansiblePlaybookDir) {
                extraVarFileParam = "-e @${config.ansiblePlaybookDir}/${varFile}"
            }
            ansibleExtraParams+=[extraVarFileParam]
        }
    }
    if (config?.ansibleExtraParams) {
        config.ansibleExtraParams.each { String extraParam ->
            ansibleExtraParams+=[extraParam]
        }
    }
    if (ansibleExtraParams.size()>0) {
        ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]=ansibleExtraParams
    }

    return ansibleCfg
}
