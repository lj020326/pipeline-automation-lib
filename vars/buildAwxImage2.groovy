#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

def call(Map params=[:]) {

    def email_from="runAnsiblePlaybook@dettonville.org"
    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    Map config=loadPipelineConfig(log, params)
    def agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)

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
            stage('Checkout') {
                when {
                    expression { config.gitPerformCheckout }
                }
                steps {
                    script {
                        // git credentialsId: 'bitbucket-ssh-lj020326', url: 'git@bitbucket.org:lj020326/ansible-datacenter.git'
                        checkout scm: [
                            $class: 'GitSCM',
                            branches: [[name: "${config.gitBranch}"]],
                            userRemoteConfigs: [[credentialsId: "${config.gitCredId}",
                                                 url: "${config.gitRepoUrl}"]]
                        ]

                    }
                }
            }
            stage('Run collections and roles Install') {
                when {
                    expression { config.ansibleCollectionsRequirements || config.ansibleRolesRequirements }
                }
                steps {
                    script {
                        // install galaxy roles
                        if (fileExists(config.ansibleCollectionsRequirements)) {
                            sh "ansible-galaxy collection install ${config.ansibleGalaxyForceOpt} -r ${config.ansibleCollectionsRequirements}"
                        }
                        if (fileExists(config.ansibleRolesRequirements)) {
                            sh "ansible-galaxy install ${config.ansibleGalaxyForceOpt} -r ${config.ansibleRolesRequirements}"
                        }
                    }
                }
            }

            stage('Run Ansible Playbook') {
                steps {
                    script {

                        Map ansibleCfg = [
                            (ANSIBLE) : [
                                (ANSIBLE_INSTALLATION)    : "ansible-local",
                                (ANSIBLE_PLAYBOOK)        : "${config.ansiblePlaybook}",
                                (ANSIBLE_INVENTORY)       : "${config.ansibleInventory}",
                                (ANSIBLE_TAGS)            : "${config.ansibleTags}",
                                (ANSIBLE_CREDENTIALS_ID)  : "${config.ansibleSshCredId}",
                                (ANSIBLE_COLORIZED)       : true,
                                (ANSIBLE_DISABLE_HOST_KEY_CHECK): true,
                                (ANSIBLE_EXTRA_PARAMETERS): [],
//                                (ANSIBLE_EXTRA_PARAMETERS): ["-vvvv"],
//                                (ANSIBLE_SUDO_USER)       : "root",
//                                (ANSIBLE_EXTRA_VARS)      : [
//                                    "ansible_python_interpreter" : "/usr/bin/python3"
//                                ]
                            ]
                        ]

                        if (config.containsKey('ansibleVaultCredId')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_VAULT_CREDENTIALS_ID]=config.ansibleVaultCredId
                        }
                        if (config.containsKey('ansiblePythonInterpreter')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_VARS]=["ansible_python_interpreter" : config.ansiblePythonInterpreter]
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
                        if (config.containsKey('ansibleLimitHosts')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_LIMIT]=config.ansibleLimitHosts
                        }
                        if (config?.ansibleLogLevel) {
                            ansibleCfg[ANSIBLE][ANSIBLE_LOG_LEVEL]=config.ansibleLogLevel
                        }

                        config = MapMerge.merge(ansibleCfg, config)
                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        sh "tree ${config.ansibleInventoryDir}/group_vars"

                        withEnv(config.ansibleEnvVarsList) {
                            withCredentials(config.ansibleSecretVarsList) {
                                ansible.execPlaybook(config)
                            }
                        }

                    }
                }
            }
        }
        post {
            always {
                script {
                    if (fileExists("ansible.log")) {
                        ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                        sendEmailReport(config.emailFrom, config.emailDist, currentBuild, 'ansible.log')
                        // def build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
                        // emailext (
                        //     to: "${appConfigs.pipeline.alwaysEmailList}",
                        //     from: "${email_from}",
                        //     subject: "BUILD ${build_status}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                        //     body: "${env.EMAIL_BODY} \n\nBuild Log:\n${ansibleLogSummary}",
                        // )
                    }
                    sendEmail(currentBuild, env)
                }

//                 echo "Empty current workspace dir"
//                 deleteDir()
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
//    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', '--force')
    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', '')
//    config.ansibleInventory = config.get('ansibleInventory', 'inventory')
    config.ansibleInventory = config.get('ansibleInventory', 'hosts.ini')
    config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))

    config.ansibleSshCredId = config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.ansibleVaultCredId = config.get('ansibleVaultCredId', 'ansible-vault-pwd-file')
    config.ansiblePlaybook = config.get('ansiblePlaybook', 'site.yml')
    config.ansibleTags = config.get('ansibleTags', '')

    config.ansibleEnvVarsList = config.get('ansibleEnvVarsList', [])

    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
    List secretVarsListDefault=[
        usernamePassword(credentialsId: 'dcapi-ansible-ssh-password', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME')
    ]

    config.ansibleSecretVarsList = config.get('ansibleSecretVarsList', secretVarsListDefault)

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${config}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}