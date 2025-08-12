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
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map args=[:]) {

    Map config = args.clone()
//     log.enableDebug()
//     Map config = loadAnsibleConfigs(arg)
//     log.info("params=${JsonUtils.printToJsonString(params)}")
    log.info("config=${JsonUtils.printToJsonString(config)}")

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

//     withEnv(config.ansibleEnvVarsList) {
//         withCredentials(config.ansibleSecretVarsList) {
    runWithConditionalEnvAndCredentials(config.ansibleEnvVarsList, config.ansibleSecretVarsList) {

        if (log.isLogActive(LogLevel.DEBUG)) {
            sh "export -p | sed 's/declare -x //' | sed 's/export //'"
        }
        Map ansibleConfig = getAnsibleCommandConfig(config)

        config = MapMerge.merge(ansibleConfig, config)
        log.info("config.ansible=${JsonUtils.printToJsonString(config.ansible)}")

        sh "${config.ansibleCmd} --version"

        // ref: https://stackoverflow.com/questions/44022775/jenkins-ignore-failure-in-pipeline-build-step#47789656
        try {
            catchError{
                ansible.execPlaybook(config)
            }
            currentBuild.result = 'SUCCESS'
            config.gitRemoteBuildStatus = "SUCCESSFUL"
        } catch (hudson.AbortException ae) {
            config.gitRemoteBuildStatus = "FAILED"
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

            log.info("**** currentBuild.result=${currentBuild.result}")
        }
    }

} // body

Map getAnsibleCommandConfig(Map config) {

    log.info("debug=${JsonUtils.printToJsonString(config)}")

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
