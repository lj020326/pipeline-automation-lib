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
                            sh "env | sort"
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

                        dir(config.collectionDir) {
                            sh "${config.ansibleCmd} --version"
                            // ref: https://stackoverflow.com/questions/44022775/jenkins-ignore-failure-in-pipeline-build-step#47789656
                            try {
                                catchError{

                                    List ansibleTestCommandList = []
                                    ansibleTestCommandList.push("ansible-test")
                                    ansibleTestCommandList.push("${config.ansibleTestCommand}")
                                    ansibleTestCommandList.push("${config.ansibleTestVerbosity} --color")
                                    ansibleTestCommandList.push("--python ${config.testPythonVersion}")
                                    ansibleTestCommandList.push("${config.target}")

                                    String ansibleTestCommand = ansibleTestCommandList.join(" ")

                                    sh "${ansibleTestCommand}"
//                                     ansibleTestUtil.withTestConfigVault(config.ansibleVaultCredId) {
//                                         ansibleTestUtil.runAnsibleTest(
//                                             command="integration",
//                                             color = "auto",
//                                             verbosity="-v",
//                                             pythonVersion="3.6",
//                                             target = "update_hosts"
//                                         )
//                                     }
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

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    // config.alwaysEmailDist = config.alwaysEmailDist ?: "lee.james.johnson@gmail.com"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.skipDefaultCheckout = config.get('skipDefaultCheckout', false)
    config.gitPerformCheckout = config.get('gitPerformCheckout', !config.get('skipDefaultCheckout',false))
    config.gitBranch = config.get('gitBranch', '')
    config.gitRepoUrl = config.get('gitRepoUrl', '')
    config.gitCredId = config.get('gitCredId', '')

    config.ansibleCollectionsRequirements = config.get('ansibleCollectionsRequirements', './collections/requirements.yml')
//     config.ansibleRolesRequirements = config.get('ansibleRolesRequirements', './roles/requirements.yml')
//    config.ansibleInventory = config.get('ansibleInventory', 'inventory')
//    config.ansibleInventory = config.get('ansibleInventory', 'hosts.ini')
    config.ansibleInventory = config.get('ansibleInventory', 'hosts.yml')
    config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))

    config.ansibleGalaxyIgnoreCerts = config.get('ansibleGalaxyIgnoreCerts',false)
    config.ansibleGalaxyForceOpt = config.get('ansibleGalaxyForceOpt', false)

//     config.ansibleSshCredId = config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.ansibleVaultCredId = config.get('ansibleVaultCredId', 'ansible-vault-password-file')
    config.ansibleTags = config.get('ansibleTags', '')

    String ansibleGalaxyCmd = "ansible-galaxy"
    String ansibleCmd = "ansible"

    config.ansibleInstallation = config.get('ansibleInstallation', 'ansible-venv')
    config.ansibleGalaxyCmd = ansibleGalaxyCmd
    config.ansibleCmd = ansibleCmd

    config.ansibleTestCommand = config.get('ansibleTestCommand', 'integration')
    config.ansibleTestVerbosity = config.get('ansibleTestVerbosity', '-v')
    config.ansibleTestPythonVersion = config.get('ansibleTestPythonVersion', '3.9')
    config.ansibleTestTarget = config.get('ansibleTestTarget', 'update_hosts')

    config.ansibleEnvVarsList = config.get('ansibleEnvVarsList', [])

    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
//     List secretVarsListDefault = []
//     List secretVarsListDefault=[
//         usernamePassword(credentialsId: 'ansible-ssh-password-linux', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//         sshUserPrivateKey(credentialsId: 'bitbucket-ssh-jenkins')
//     ]
    List secretVarsListDefault=[
        file(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_TOKEN_PATH')
    ]

    config.ansibleSecretVarsList = config.get('ansibleSecretVarsList', secretVarsListDefault)
    
    config.collectionDir=config.get('collectionDir', 'ansible_collections')

    log.debug("${logPrefix} params=${params}")
    log.debug("${logPrefix} config=${config}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}
