#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

def call(Map config=[:]) {

    def email_from="runAnsiblePlaybook@dettonville.org"
    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    pipeline {
        agent any
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout()
            timeout(time: 1, unit: 'HOURS')
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
//                        sh "ansible-galaxy install -vvv -r provision/requirements.yml -p provision/roles/"
                    sh "ansible-galaxy install -r roles/requirements.yml"
                    sh "ansible-galaxy collection install -r roles/requirements.yml"

                }
            }

            stage('Run Ansible Playbook') {
                steps {
                    script {

                        Map ansibleCfg = [
                            (ANSIBLE) : [
                                (ANSIBLE_INSTALLATION)    : "ansible-local",
                                (ANSIBLE_PLAYBOOK)        : 'site.yml',
                                (ANSIBLE_INVENTORY)       : 'inventory',
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

