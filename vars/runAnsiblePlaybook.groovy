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
//        agent any
//        agent {
//            label "ansible"
//        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout()
//            timeout(time: 2, unit: 'HOURS')
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
            stage('Checkout') {
                steps {
                    script {
//                        deleteDir()
//                        git credentialsId: 'bitbucket-ssh-lj020326', url: 'git@bitbucket.org:lj020326/ansible-datacenter.git'
                        checkout scm: [
                            $class: 'GitSCM',
                            branches: [[name: "master"]],
                            userRemoteConfigs: [[credentialsId: 'bitbucket-ssh-lj020326',
                                                 url: 'git@bitbucket.org:lj020326/ansible-datacenter.git']]
                        ]

                    }
                }
            }
            stage('Run Galaxy Install') {
                steps {
                    // install galaxy roles
//                    sh "ansible-galaxy install -r roles/requirements.yml"
//                    sh "ansible-galaxy collection install -r roles/requirements.yml"
                    sh "ansible-galaxy install ${config.ansibleGalaxyForceOpt} -r roles/requirements.yml"
                    sh "ansible-galaxy collection install ${config.ansibleGalaxyForceOpt} -r roles/requirements.yml"

                }
            }

            stage('Run Ansible Playbook') {
                steps {
                    script {

                        Map ansibleCfg = [
                            (ANSIBLE) : [
                                (ANSIBLE_INSTALLATION)    : "ansible-local",
                                (ANSIBLE_PLAYBOOK)        : 'site.yml',
//                                (ANSIBLE_INVENTORY)       : 'inventory/hosts.ini',
                                (ANSIBLE_INVENTORY)       : "${config.ansibleInventory}",
                                (ANSIBLE_TAGS)            : "${env.JOB_BASE_NAME}",
                                (ANSIBLE_CREDENTIALS_ID)  : "jenkins-ansible-ssh",
                                (ANSIBLE_VAULT_CREDENTIALS_ID)  : "ansible-vault-pwd-file",
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

                        if (config.containsKey('ansibleDebugFlag')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_EXTRA_PARAMETERS]+=[config.ansibleDebugFlag]
                        }
                        if (config.containsKey('ansibleLimitHosts')) {
                            ansibleCfg[ANSIBLE][ANSIBLE_LIMIT]=config.ansibleLimitHosts
                        }

                        config = MapMerge.merge(ansibleCfg, config)
                        log.info("config=${JsonUtils.printToJsonString(config)}")

//                        ansible.execPlaybook(config)

//                        List secretVars=[usernamePassword(credentialsId: 'dcapi-ansible-ssh-password', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME')]
                        // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
                        // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
                        List secretVars=[
                                usernamePassword(credentialsId: 'dcapi-ansible-ssh-password', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//                                sshUserPrivateKey(credentialsId: 'jenkins-ansible-ssh', keyFileVariable: 'ssh-key', usernameVariable: 'ssh-user')
                        ]

                        withCredentials(secretVars) {
                            ansible.execPlaybook(config)
                        }

                    }
                }
            }
        }
        post {
            always {
                script {
                    ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
//                    def build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
//                    emailext (
//                        to: "${appConfigs.pipeline.alwaysEmailList}",
//                        from: "${email_from}",
//                        subject: "BUILD ${build_status}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
//                        body: "${env.EMAIL_BODY} \n\nBuild Log:\n${ansibleLogSummary}",
//                    )
                    sendEmail(currentBuild, env)
                }

                echo "Empty current workspace dir"
//                deleteDir()
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

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"ansible")
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')
//    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', '--force')
    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', '')
//    config.ansibleInventory = config.get('ansibleInventory', 'inventory')
    config.ansibleInventory = config.get('ansibleInventory', 'inventory/hosts.ini')

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${config}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}
