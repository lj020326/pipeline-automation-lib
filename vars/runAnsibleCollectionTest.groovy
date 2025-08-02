#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

    Map config = [:]
    List testTagsParam = params.get('testTagsParam',[])
    config.testType = params.get('testType','module')

    List paramList = []

    Map paramMap = [
        testCaseIdList     : string(
            defaultValue: "",
            description: "Limit test to specified comma-delimited test cases\nE.g., '01','02,05'",
            name: 'TestCaseIdList'),
        ansibleTags        : choice(choices: testTagsParam.join('\n'), description: "Choose Test Tag", name: 'AnsibleTags'),
        ansibleLimitHosts  : string(
            defaultValue: "",
            description: "Limit playbook to specified inventory hosts\nE.g., 'testgroup_lnx','host01,host02'",
            name: 'AnsibleLimitHosts'),
        ansibleDebugFlag   : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
        ansibleGalaxyForceOpt  : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Force Mode?", name: 'AnsibleGalaxyForceOpt'),
        ansibleGalaxyUpgradeOpt : booleanParam(defaultValue: true, description: "Use Ansible Galaxy Upgrade?", name: 'AnsibleGalaxyUpgradeOpt'),
        useCheckDiffMode   : booleanParam(defaultValue: false, description: "Use Check+Diff Mode (Dry Run with Diffs)?", name: 'UseCheckDiffMode'),
        initializeJobMode  : booleanParam(defaultValue: false, description: "Initialize Job Mode?", name: 'InitializeJobMode')
    ]

    paramMap.each { String key, def param ->
        if (config.testType == 'role') {
            if (key != 'testCaseIdList') {
                paramList.addAll([param])
            }
        } else {
            paramList.addAll([param])
        }
    }

    properties([
        parameters(paramList),
        disableConcurrentBuilds()
    ])

    params.each { key, value ->
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key] = value
        }
    }

    if (config.useCheckDiffMode) {
        config.ansibleCheckMode=true
        config.ansibleDiffMode=true
    }

    config.get('ansiblePlaybook',"${env.JOB_NAME.split('/')[-2]}.yml")
    config.get("galaxyYamlPath", "galaxy.yml")
    config.get('ansibleCollectionsRequirements', 'tests/integration/requirements.yml')
    config.get('pipRequirementsFile', 'tests/integration/requirements.txt')

    config.get("gitRemoteRepoType","gitea")
    config.get("gitRemoteBuildKey",'collection tests')
    config.get("gitRemoteBuildName", 'Collection Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")
    config.get("gitRemoteBuildConclusion", "NEUTRAL")
    config.get("gitRemoteBuildStatus", "IN_PROGRESS")

    //     ref: https://stackoverflow.com/questions/62213910/run-only-tasks-with-a-certain-tag-or-untagged
    // config.ansibleTags = config.get('ansibleTags',"untagged,${env.JOB_NAME.split('/')[-2]}")

    Map ansibleExtraVars = config.get('ansibleExtraVars',[:])
    if (config.testCaseIdList) {
        ansibleExtraVars.test_case_id_list_string = config.testCaseIdList
    }
    config.ansibleExtraVars = ansibleExtraVars

    // using the docker ansible version
    // DO NOT use the standard ansible ansibleInstallation
    config.useDockerAnsibleInstallation = true
    config.isTestPipeline = true

    config = loadAnsibleConfigs(config)

    config.get("ansibleVersion", "stable-2.16")
    config.get("pythonVersion", "3.12")
    config.get("dockerRegistry", "media.johnson.int:5000")

    config.dockerImage = getAnsibleDockerImageTag(
                            ansibleVersion: config.ansibleVersion,
                            pythonVersion: config.pythonVersion,
                            dockerRegistry: config.dockerRegistry)

    log.info("config=${JsonUtils.printToJsonString(config)}")
    log.info("Running tests inside docker container: ${config.dockerImage}")

    pipeline {
        agent {
            docker {
                label 'docker'
                image config.dockerImage
//                 args '-u root' // Optional: Add custom arguments to the docker run command
//                 args "-v /var/run/docker.sock:/var/run/docker.sock --privileged"
            }
        }
//         tools {
//             // ref: https://webapp.chatgpt4google.com/s/MzY5MjYw
//             // ref: https://stackoverflow.com/questions/47895668/how-to-select-multiple-jdk-version-in-declarative-pipeline-jenkins#48368506
//             ansible "${config.ansibleInstallation}"
//         }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '20'))
            skipDefaultCheckout(false)
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }
        stages {
            stage('Install Test Ansible Collection requirements') {
                when {
                    expression { config.ansibleCollectionsRequirements || config.ansibleRolesRequirements }
                }
                steps {
                    script {
//                         String gitBranch = java.net.URLDecoder.decode(env.BRANCH_NAME, "UTF-8")
                        String gitBranch = java.net.URLDecoder.decode(env.GIT_BRANCH, "UTF-8")
                        config.get('gitBranch',"${gitBranch}")
                        config.gitCommitId = env.GIT_COMMIT
                        log.debug("config.gitBranch=${config.gitBranch}")
                        log.debug("config.gitCommitId=${config.gitCommitId}")

                        log.info("Installing collection requirements")
                        installAnsibleGalaxyDeps(
                            ansibleCollectionsRequirements: config.ansibleCollectionsRequirements,
                            ansibleRolesRequirements: config.ansibleRolesRequirements,
                            ansibleGalaxyIgnoreCerts: config.ansibleGalaxyIgnoreCerts,
                            ansibleGalaxyForceOpt: config.ansibleGalaxyForceOpt,
                            ansibleGalaxyUpgradeOpt: config.ansibleGalaxyUpgradeOpt,
                            ansibleGalaxyEnvVarsList: config.ansibleGalaxyEnvVarsList,
                            galaxySecretVarsList: config.galaxySecretVarsList,
                            ansibleGalaxyCmd: config.ansibleGalaxyCmd
                        )
                    }
                }
            }
            stage('Install Test Python package requirements') {
                when {
                    expression { config?.pipRequirementsFile }
                }
                steps {
                    script {
                        installPipRequirements(
                            pipRequirementsFile: config.pipRequirementsFile
                        )
                    }
                }
            }
            stage('Setup Ansible Test Environment') {
                steps {
                    script {
                        config = setupCollectionTestEnv(config)
                    }
                }
            }
            stage('Run Collection Tests') {
                steps {
                    script {
                        try {
                            catchError{
                                runAnsiblePlaybook(config)
//                                 dir(config.targetCollectionDir) {
//                                     runAnsiblePlaybook(config)
//                                 }
                            }
                            config.gitRemoteBuildStatus = "SUCCESS"
                            config.gitRemoteBuildConclusion = "SUCCESS"
                            currentBuild.result = 'SUCCESS'
                        } catch (hudson.AbortException ae) {
                            // handle an AbortException
                            // ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
                            // ref: https://gist.github.com/stephansnyt/3ad161eaa6185849872c3c9fce43ca81
                            config.gitRemoteBuildConclusion = "ABORTED"
                            config.gitRemoteBuildStatus = "FAILED"
                            if (manager.build.getAction(InterruptedBuildAction.class) ||
                                // this ambiguous condition means a user _probably_ aborted, not sure if this one is really necessary
                                (ae instanceof FlowInterruptedException && ae.causes.size() == 0)) {
                                throw ae
                            } else {
                                ansibleLogSummary = "ansible.execPlaybook error: " + ae.getMessage()
                                log.error("ansible.execPlaybook error: " + ae.getMessage())
                            }
                            currentBuild.result = 'FAILURE'
                        } finally {
                            if (fileExists("ansible.log")) {
                                ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                            }

                            if (fileExists(config.testComponentDir)) {
//                                 sh "tree ${config.testBasePath}/"
//                                 archiveArtifacts allowEmptyArchive: true, artifacts: "${config.testBaseDir}/**"
//                                 sh "tree ${config.testComponentDir}/"
                                archiveArtifacts allowEmptyArchive: true, artifacts: "${config.testComponentDir}/**"
                                publishHTML([allowMissing         : true,
                                             alwaysLinkToLastBuild: true,
                                             keepAll              : false,
                                             reportDir            : "${config.testComponentDir}/",
                                             reportFiles          : "${config.testComponentDir}/test-results.md",
                                             reportName           : "Test Results for ${config.gitBranch}"])
                            }
                            log.info("**** currentBuild.result=${currentBuild.result}")
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
                        gitRemoteBuildConclusion: config.gitRemoteBuildConclusion,
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
