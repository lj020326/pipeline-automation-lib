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

    Map config=loadPipelineConfig(log, params)
    String agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)
//     def agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)
//     String ansibleTool = 'ansible-venv'
    String ansibleLogSummary = "No results"

    pipeline {
        agent {
            label agentLabel as String
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
            stage('Run collections and roles Install') {
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
                        // install galaxy roles
                        List ansibleGalaxyArgList = []
                        if (config.ansibleGalaxyIgnoreCerts) {
                            ansibleGalaxyArgList.push("--ignore-certs")
                        }
                        if (config.ansibleGalaxyForceOpt) {
                            ansibleGalaxyArgList.push("--force")
                        }
                        String ansibleGalaxyArgs = ansibleGalaxyArgList.join(" ")

                        withCredentials(config.ansibleSecretVarsList) {
                            // ref: https://stackoverflow.com/questions/60756020/print-environment-variables-sorted-by-name-including-variables-with-newlines
//                             sh "env -0 | sort -z | tr \'\0\' \'\n\'"
                            sh "export -p | sed 's/declare -x //'"
                            sh "${config.ansibleGalaxyCmd} --version"
                            if (fileExists(config.ansibleCollectionsRequirements)) {
                                sh "${config.ansibleGalaxyCmd} collection install ${ansibleGalaxyArgs} -r ${config.ansibleCollectionsRequirements}"
                            }
                            if (fileExists(config.ansibleRolesRequirements)) {
                                sh "${config.ansibleGalaxyCmd} install ${ansibleGalaxyArgs} -r ${config.ansibleRolesRequirements}"
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

                        Map ansibleCfg = [
                            (ANSIBLE) : [
                                (ANSIBLE_PLAYBOOK)        : "${config.ansiblePlaybook}",
                                (ANSIBLE_COLORIZED)       : true,
                                (ANSIBLE_DISABLE_HOST_KEY_CHECK): true,
                                (ANSIBLE_EXTRA_PARAMETERS): [],
//                                 (ANSIBLE_INSTALLATION)    : "ansible-local-bin",
//                                 (ANSIBLE_INSTALLATION)    : "ansible",
//                                 (ANSIBLE_INVENTORY)       : "${config.ansibleInventory}",
//                                 (ANSIBLE_TAGS)            : "${config.ansibleTags}",
//                                 (ANSIBLE_CREDENTIALS_ID)  : "${config.ansibleSshCredId}",
//                                 (ANSIBLE_EXTRA_PARAMETERS): ["-vvvv"],
//                                 (ANSIBLE_SUDO_USER)       : "root",
//                                 (ANSIBLE_EXTRA_VARS)      : [
//                                    "ansible_python_interpreter" : "/usr/bin/python3"
//                                 ]
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

                        Map extraVars = [:]
                        if (config.containsKey('ansibleExtraVars')) {
                            extraVars+=config.ansibleExtraVars
                        }
                        if (config.containsKey('ansiblePythonInterpreter')) {
                            extraVars+=["ansible_python_interpreter" : config.ansiblePythonInterpreter]
                        }
                        if (extraVars.size()>0) {
                            log.info("extraVars=${JsonUtils.printToJsonString(extraVars)}")
                            ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_VARS]=extraVars
                        }

                        if (config.containsKey('ansibleVaultCredId')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_VAULT_CREDENTIALS_ID]=config.ansibleVaultCredId
                        }
                        if (config.containsKey('ansibleDebugFlag')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]+=[config.ansibleDebugFlag]
                        }
                        if (config.containsKey('ansibleCheckMode')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]+=['--check']
                        }
                        if (config.containsKey('ansibleDiffMode')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]+=['--diff']
                        }
                        if (config.containsKey('ansibleVarFiles')) {
                            config.ansibleVarFiles.each { String varFile ->
                                String extraVarFileParam = "-e @${varFile}"
                                ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]+=[extraVarFileParam]
                            }
                        }
                        if (config.containsKey('ansibleLimitHosts')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_LIMIT]=config.ansibleLimitHosts
                        }
                        if (config?.ansibleLogLevel) {
                            ansibleCfg[ANSIBLE][ANSIBLE_LOG_LEVEL]=config.ansibleLogLevel
                        }

                        config = MapMerge.merge(ansibleCfg, config)
                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        if ( fileExists("${config.ansibleInventoryDir}/group_vars") ) {
                            sh "tree ${config.ansibleInventoryDir}/group_vars"
                        }

                        withEnv(config.ansibleEnvVarsList) {
                            withCredentials(config.ansibleSecretVarsList) {
                                sh "${config.ansibleCmd} --version"
                                // ref: https://stackoverflow.com/questions/44022775/jenkins-ignore-failure-in-pipeline-build-step#47789656
                                try {
                                    catchError{
                                        ansible.execPlaybook(config)
                                    }
                                } catch (hudson.AbortException ae) {
                                    // handle an AbortException
                                    // ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
//                                     return getResultFromException(ae)
                                    if (manager.build.getAction(InterruptedBuildAction.class) ||
                                        // this ambiguous condition means a user _probably_ aborted, not sure if this one is really necessary
                                        (error instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException && error.causes.size() == 0)) {
                                        throw error
                                    } else {
                                        ansibleLogSummary = "ansible.execPlaybook error: " + e.getMessage()
                                        log.error("ansible.execPlaybook error: " + e.getMessage())
                                    }
                                    currentBuild.result = 'FAILURE'
                                }
                                if (fileExists("ansible.log")) {
                                    ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                                }
                                log.info("**** currentBuild.result=${currentBuild.result}")
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
//                     sendEmailReport(config.emailFrom, config.emailDist, currentBuild, 'ansible.log')
//                     def build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
//                     emailext (
//                         to: "${config.emailDist}",
//                         from: "${config.emailFrom}",
//                         subject: "BUILD ${build_status}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
//                         body: "${env.EMAIL_BODY} \n\nBuild Log:\n${ansibleLogSummary}",
//                     )
                    sendEmail(currentBuild, env)
                }

//                 echo "Empty current workspace dir"
//                 deleteDir()
            }
        }
    }

} // body

// ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
static Result getResultFromException(Throwable e) {
    // ref: https://issues.jenkins.io/browse/JENKINS-34376
    // ref: https://gist.github.com/stephansnyt/3ad161eaa6185849872c3c9fce43ca81#file-exceptionhandle-groovy
//     if (e.getMessage().contains('script returned exit code 143')) {
//         throw new jenkins.model.CauseOfInterruption.UserInterruptedException(e)
//     } else {
//         log.error("ansible.execPlaybook error: " + e.getMessage())
//     }
//     if (manager.build.getAction(InterruptedBuildAction.class) ||
//         // this ambiguous condition means a user _probably_ aborted, not sure if this one is really necessary
//         (error instanceof org.jenkinsci.plugins.workflow.steps.FlowInterruptedException && error.causes.size() == 0)) {
//         throw error
//     } else {
//         log.error("ansible.execPlaybook error: " + e.getMessage())
//     }

    if (e instanceof FlowInterruptedException) {
        return ((FlowInterruptedException)e).result
    } else {
        return Result.FAILURE
    }
}

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
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')

//    config.emailDist = config.emailDist ?: "ljohnson@dettonville.org"
    config.emailDist = config.get('emailDist',"ljohnson@dettonville.org")
    // config.alwaysEmailDist = config.alwaysEmailDist ?: "ljohnson@dettonville.org"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.skipDefaultCheckout = config.get('skipDefaultCheckout', false)
    config.gitPerformCheckout = config.get('gitPerformCheckout', !config.get('skipDefaultCheckout',false))
    config.gitBranch = config.get('gitBranch', '')
    config.gitRepoUrl = config.get('gitRepoUrl', '')
    config.gitCredId = config.get('gitCredId', '')

    config.ansibleCollectionsRequirements = config.get('ansibleCollectionsRequirements', './collections/requirements.yml')
    config.ansibleRolesRequirements = config.get('ansibleRolesRequirements', './roles/requirements.yml')
//    config.ansibleInventory = config.get('ansibleInventory', 'inventory')
//    config.ansibleInventory = config.get('ansibleInventory', 'hosts.ini')
    config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))

    config.ansibleGalaxyIgnoreCerts = config.get('AnsibleGalaxyIgnoreCerts',false)
    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', false)

    config.ansibleSshCredId = config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.ansibleVaultCredId = config.get('ansibleVaultCredId', 'ansible-vault-password-file')
    config.ansiblePlaybook = config.get('ansiblePlaybook', 'site.yml')
    config.ansibleTags = config.get('ansibleTags', '')

    String ansibleGalaxyCmd = "ansible-galaxy"
    String ansibleCmd = "ansible"

    config.ansibleInstallation = config.get('ansibleInstallation', 'ansible-venv')
    config.ansibleGalaxyCmd = ansibleGalaxyCmd
    config.ansibleCmd = ansibleCmd

    config.ansibleEnvVarsList = config.get('ansibleEnvVarsList', [])

    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
    List secretVarsListDefault = []
//     List secretVarsListDefault=[
//         usernamePassword(credentialsId: 'ansible-ssh-password-linux', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//         sshUserPrivateKey(credentialsId: 'bitbucket-ssh-jenkins')
//     ]

    config.ansibleSecretVarsList = config.get('ansibleSecretVarsList', secretVarsListDefault)

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${config}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}
