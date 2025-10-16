#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

def call(Map pipelineConfig=[:]) {

    pipelineConfig.get('testTagsParam',[])
    pipelineConfig.get('testCaseIdDefault','')

    List paramList = []

    Map paramMap = [
        testCaseIdList     : string(
            defaultValue: pipelineConfig.testCaseIdDefault,
            description: "Limit test to specified comma-delimited test cases\nE.g., '01','02,05'",
            name: 'TestCaseIdList'),
        ansibleTags        : choice(choices: pipelineConfig.testTagsParam.join('\n'), description: "Choose Test Tag", name: 'AnsibleTags'),
        ansibleLimitHosts  : string(
            defaultValue: "",
            description: "Limit playbook to specified inventory hosts\nE.g., 'testgroup_lnx','host01,host02'",
            name: 'AnsibleLimitHosts'),
        ansibleDebugFlag   : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
        ansibleGalaxyForceOpt  : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Force Mode?", name: 'AnsibleGalaxyForceOpt'),
        ansibleGalaxyUpgradeOpt : booleanParam(defaultValue: true, description: "Use Ansible Galaxy Upgrade?", name: 'AnsibleGalaxyUpgradeOpt'),
        useCheckDiffMode   : booleanParam(defaultValue: false, description: "Use Check+Diff Mode (Dry Run with Diffs)?", name: 'UseCheckDiffMode'),
        initializeJobMode  : booleanParam(defaultValue: false, description: "Initialize Job Mode?", name: 'InitializeJobMode'),
        enableGitTestResults  : booleanParam(defaultValue: false, description: "Enable Storing Test Results to Ansible-Automation-Test Git Repo?", name: 'EnableGitTestResults'),
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList),
        disableConcurrentBuilds()
    ])

    Map config = loadPipelineConfig(params)
    config = MapMerge.merge(config, pipelineConfig)
    log.info("config=${JsonUtils.printToJsonString(config)}")

    log.info("Running tests inside docker container: ${config.dockerImage}")

    pipeline {
        agent {
            docker {
                label 'docker'
                image config.dockerImage
//                 args '-u root' // Optional: Add custom arguments to the docker run command
//                 args "-v /var/run/docker.sock:/var/run/docker.sock --privileged"
                reuseNode true
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
            stage('Load Collection Galaxy config') {
                steps {
                    script {
                        config = loadCollectionTestConfig(config)
                    }
                }
            }
            stage('Install Ansible Collection requirements') {
                when {
                    // This stage will only run if InitializeJobMode is false
                    expression { return !config.initializeJobMode }
                    expression { config.ansibleCollectionsRequirements || config.ansibleRolesRequirements }
                }
                steps {
                    script {
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
            stage('Install Python package requirements') {
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
                        setupCollectionTestEnv(config)
                    }
                }
            }
            stage('Run Collection Tests') {
                steps {
                    script {
                        try {
                            runAnsiblePlaybook(config)
                            config.gitRemoteBuildStatus = "COMPLETED"
                            config.gitRemoteBuildConclusion = "SUCCESS"
                            currentBuild.result = 'SUCCESS'
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            config.gitRemoteBuildStatus = "COMPLETED"
                            config.gitRemoteBuildConclusion = "FAILURE"
                            // Check if the interruption was a user-initiated abort
                            e.getCauses().each { cause ->
                                log.error("Cause: ${cause.getClass().getName()}")
                            }
                            // It is crucial to re-throw the exception to correctly mark the build as ABORTED
                            currentBuild.result = 'FAILURE'
                            throw e
                        } catch (hudson.AbortException ae) {
                            // handle an AbortException
                            // ref: https://github.com/jenkinsci/pipeline-model-definition-plugin/blob/master/pipeline-model-definition/src/main/groovy/org/jenkinsci/plugins/pipeline/modeldefinition/Utils.groovy
                            // ref: https://gist.github.com/stephansnyt/3ad161eaa6185849872c3c9fce43ca81
                            config.gitRemoteBuildConclusion = "COMPLETED"
                            config.gitRemoteBuildConclusion = "FAILURE"
                            log.error("ansible.execPlaybook abort error: " + ae.getMessage())
                            currentBuild.result = 'FAILURE'
                            // It is crucial to re-throw the exception to correctly mark the build as ABORTED
                            throw ae
                        } catch (Exception e) {
                            config.gitRemoteBuildStatus = "COMPLETED"
                            config.gitRemoteBuildConclusion = "FAILURE"
                            // General catch-all for any other unexpected failures
                            log.error("An unexpected error occurred: ${e.message}")
                            currentBuild.result = 'FAILURE'
                            throw e
                        } finally {
                            if (fileExists("ansible.log")) {
                                ansibleLogSummary = sh(returnStdout: true, script: "tail -n 50 ansible.log").trim()
                            }

                            log.debug("config.testComponentDir=${config.testComponentDir}")
                            if (log.isLogActive(LogLevel.DEBUG)) {
                                sh "pwd"
                                sh "find . -type d"
                            }
                            if (fileExists(config.testComponentDir)) {
                                if (log.isLogActive(LogLevel.DEBUG)) {
                                    sh "find ${config.testComponentDir} -type f"
                                }
                                archiveArtifacts(
                                    allowEmptyArchive: true,
                                    artifacts: "${config.testComponentDir}/**",
                                    fingerprint: true)

                                publishHTML([allowMissing         : true,
                                             alwaysLinkToLastBuild: true,
                                             keepAll              : false,
                                             reportDir            : "${config.testComponentDir}/",
                                             reportFiles          : "${config.testComponentDir}/test-results.md",
                                             reportName           : "Test Results for ${config.gitBranch}"])

                                log.info("config.testJunitXmlReportFile=${config.testComponentDir}/${config.testJunitXmlReportFile}")
                                junit(testResults: "${config.testComponentDir}/*.xml",
                                      skipPublishingChecks: true,
                                      allowEmptyResults: true)
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

Map loadPipelineConfig(Map params) {
    Map config = [:]

    params.each { key, value ->
        key=Utilities.decapitalize(key)
        if (value!="") {
            if (key=="ansibleTags") {
                if (value!="all") {
                    config[key] = value
                }
            } else {
                config[key] = value
            }
        }
    }

    // if defined, set default test tag to `config.testTagDefaultIdx`
    // ref: https://stackoverflow.com/questions/47873401/how-do-i-set-a-default-choice-in-jenkins-pipeline
    if (config?.testTagDefaultIdx) {
        config.ansibleTags = config.testTagsParam[config.testTagDefaultIdx]
    }

    if (config.useCheckDiffMode) {
        config.ansibleCheckMode=true
        config.ansibleDiffMode=true
    }

    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')

    config.get('enableGitTestResults', false)
    config.get('testType','module')

    List jobParts = env.JOB_NAME.split('/')
    config.testGitBranch = jobParts[-1]
    log.debug("config.testGitBranch=${config.testGitBranch}")
    config.gitCommitId = env.GIT_COMMIT
//     config.testGitBranchAbbrev = config.testGitBranch.replaceAll(/^(.*)-(\d+)-(.*)$/, '$1-$2').replace('/','-').replace('%2F','-')
//     log.debug("config.testGitBranchAbbrev=${config.testGitBranchAbbrev}")

    config.get('ansiblePlaybook',"${jobParts[-2]}.yml")
    config.testComponent = config.get('testComponent',"${jobParts[-3]}")

    config.get("galaxyYamlPath", "galaxy.yml")
    config.get('ansibleCollectionsRequirements', 'tests/integration/requirements.yml')
    config.get('pipRequirementsFile', 'tests/integration/requirements.txt')

    config.get("gitRemoteRepoType","gitea")
    config.get("gitRemoteBuildKey",'collection tests')
    config.get("gitRemoteBuildName", 'Collection Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")
    config.get("gitRemoteBuildConclusion", "NEUTRAL")
    config.get("gitRemoteBuildStatus", "IN_PROGRESS")

    // using the docker ansible version
    // DO NOT use the standard ansible ansibleInstallation
    config.useDockerAnsibleInstallation = true
    config.isTestPipeline = true

    config = loadAnsibleConfigs(config)

    config.get("ansibleVersion", "2.18")
    config.get("pythonVersion", "3.13")
    config.get("dockerRegistry", "media.johnson.int:5000")

    config.dockerImage = getAnsibleDockerImageId(
                            ansibleVersion: config.ansibleVersion,
                            pythonVersion: config.pythonVersion,
                            dockerRegistry: config.dockerRegistry)

    config.testBaseDir = config.get('testBaseDir', ".test-results")
    log.debug("config.testComponent=${config.testComponent}")

//         config.testJunitXmlReportDir = "${config.testComponentDir}"
//         config.testJunitXmlReport = "${config.testJunitXmlReportDir}/junit-report.xml"
//         config.testJunitXmlReport = "${config.testComponentDir}/junit-report.xml"

    config.testJunitXmlReportFile = "junit-report.xml"
    log.debug("config.testJunitXmlReportFile=${config.testJunitXmlReportFile}")

    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
