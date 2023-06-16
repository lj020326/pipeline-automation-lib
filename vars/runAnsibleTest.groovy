#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

def call(Map params=[:]) {

    Logger log = new Logger(this, LogLevel.INFO)

    Map config=loadPipelineConfig(log, params)
    def agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)
    AnsibleTestUtil ansibleTestUtil = new AnsibleTestUtil(this)

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
                        // git credentialsId: 'bitbucket-ssh-jenkins', url: 'git@bitbucket.org:lj020326/ansible-datacenter.git'
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
                            sh "ansible-galaxy collection install ${config.ansibleGalaxyForceOptString} -r ${config.ansibleCollectionsRequirements}"
                        }
                        if (fileExists(config.ansibleRolesRequirements)) {
                            sh "ansible-galaxy install ${config.ansibleGalaxyForceOptString} -r ${config.ansibleRolesRequirements}"
                        }
                    }
                }
            }

            stage('Run Ansible Playbook') {
                steps {
                    script {

                        dir(config.collectionDir) {
                            ansibleTestUtil.withTestConfigVault(config.ansibleVaultCredId) {
                                ansibleTestUtil.runAnsibleTest(
                                    command="integration",
                                    color = "auto",
                                    verbosity="-v",
                                    pythonVersion="3.6",
                                    target = "update_hosts"
                                )
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
//                         sendEmailReport(config.emailFrom, config.emailDist, currentBuild, 'ansible.log')
                        ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                        def build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
                        emailext (
                            to: "${config.emailDist}",
                            from: "${config.emailFrom}",
                            subject: "BUILD ${build_status}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                            body: "${env.EMAIL_BODY} \n\nBuild Log:\n${ansibleLogSummary}",
                        )
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
//    config.ansibleInventory = config.get('ansibleInventory', 'inventory')
//    config.ansibleInventory = config.get('ansibleInventory', 'hosts.ini')
    config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))

    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', false)
    config.ansibleGalaxyForceOptString=""
    if (config.ansibleGalaxyForceOpt) {
        config.ansibleGalaxyForceOptString="--force"
    }

//    config.ansibleSshCredId = config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.ansibleVaultCredId = config.get('ansibleVaultCredId', 'ansible-vault-password')
    config.ansibleTags = config.get('ansibleTags', '')

    config.ansibleEnvVarsList = config.get('ansibleEnvVarsList', [])

    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
//     List secretVarsListDefault=[
//         usernamePassword(credentialsId: 'infra-ansible-ssh-password', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME')
//     ]
//     config.ansibleSecretVarsList = config.get('ansibleSecretVarsList', secretVarsListDefault)
    config.ansibleSecretVarsList = config.get('ansibleSecretVarsList', [])

    config.collectionDir=config.get('collectionDir', 'ansible_collections/dettonville/inventory')

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${config}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}
