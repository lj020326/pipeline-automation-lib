#!/usr/bin/env groovy

import createGitTag
import getMavenProjectVersion
import groovy.json.JsonOutput
import runSonarScan
import sendEmail

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)

    pipeline {

        agent { label "QA-LINUX || PROD-LINUX" }  // for sonar to work cannot run on DEVCLD

        tools {
            maven 'M3'
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            skipDefaultCheckout()
            timestamps()
            timeout(time: 1, unit: 'HOURS')
        }
        environment {
            ARA_CLI = 'https://artifacts.dettonville.int/artifactory/releases/com/dettonville/ara/ara-release-cli/1.0.3/ara-release-cli-1.0.3.tar'
            DEPLOY_JOB_DEV = "DCAPI/DeploymentJobs/DeployOpenAPINotifierDev/main"
            DEPLOY_JOB_STAGE = "DCAPI/DeploymentJobs/DeployOpenAPINotifierStage/main"
        }

        stages {

            // Not sure what is the purpose of the following input?
            // presumably upon starting this job one knows if one wants to release or not
            // which should just be a boolean param provided to the job
//            stage('Prompt for Release (stage)') {
//                steps {
//                    script {
//                        config.releaseDev=promptForBoolean("Release to DEV")
//                    }
//                }
//            }

            stage('Checkout') {
                steps {
                    // wipe the workspace so we are building completely clean
                    deleteDir()
                    step([$class: 'WsCleanup'])

                    checkout scm

                    stash name: 'pom', includes: 'pom.xml'
                }
            }
            stage('Compile') {
                steps {
                    sh 'mvn clean compile -U --batch-mode'
                }
            }

            stage('Test') {
                steps {
                    sh 'mvn test --batch-mode'
                }
            }

            stage('Sonar') {
                steps {
                    runSonarScan(BRANCH_NAME)
                }
            }

            stage('Publish to Artifactory') {
                steps {
                    configFileProvider([configFile(fileId: 'infra-maven-settings-overrides', variable: 'MAVEN_SETTINGS')]) {
                        sh """
                            mvn -s "${MAVEN_SETTINGS}" deploy -DskipTests --batch-mode
                        """
                    }
                }
            }

            stage('Release (dev)') {
                agent {
                    label 'DTL'
                }
                when {
                    branch 'develop'
                    expression { config.releaseDev }
                }
                steps {
                    unstash 'pom'

                    build job: "${DEPLOY_JOB_DEV}",
                            parameters: [
                                    string(name: 'ARTIFACT_VERSION', value: "${getMavenProjectVersion()}")
                            ]
                }
            }

            stage('Release (stage)') {
                agent {
                    label 'DTL'
                }
                when {
                    expression { config.releaseStage }
                }
                steps {
                    unstash 'pom'

                    build job: "${DEPLOY_JOB_STAGE}",
                            parameters: [
                                    string(name: 'ARTIFACT_VERSION', value: "${getMavenProjectVersion()}")
                            ]
                }
            }
        }

        post {
            always {
                dir('target/surefire-reports') {
                    junit '*.xml'
                }
                script {
                    // HOUSE KEEPING
                    String duration = currentBuild.durationString.replace(' and counting', '')
                    currentBuild.description = "Job Duration: ${duration}<br>Release Dev: ${config.releaseDev}<br>Release Stage: ${config.releaseStage}"
                }
            }

            changed {
                sendEmail(currentBuild, env)
            }

            success {
                script {
                    if (BRANCH_NAME == 'main') {
                        createGitTag(getMavenProjectVersion(), env)
                    }
                }
            }
        }
    }
}

String printToJsonString(Map config) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(config))
}

// ref: https://stackoverflow.com/questions/4052840/most-efficient-way-to-make-the-first-character-of-a-string-lower-case
String decapitalize(String string) {
    if (string == null || string.length() == 0) {
        return string;
    }
    return string.substring(0, 1).toLowerCase() + string.substring(1);
}

Map loadPipelineConfig(Map params, String configFile=null) {
    Map config = [:]

    def buildDeployDefaultsTxt = libraryResource 'buildDeployDefaults.yml'
    Map defaultSettings = readYaml text: "${buildDeployDefaultsTxt}"
    config=defaultSettings.pipeline

    if (configFile != null && fileExists(configFile)) {
        log.info("pipeline config file ${configFile} present, loading ...")
        Map configSettings = readYaml file: "${configFile}"
        config=config + configSettings.pipeline
    }
    else {
        log.info("pipeline config file ${configFile} not present, using defaults...")
    }

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

//    config.get('logLevel', "INFO")
    config.get('logLevel', "DEBUG")
    config.get('debugPipeline', false)

    log.info("params=${params}")

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

//    config.get('useSimulationMode', true)
    config.get('useSimulationMode', false)

    // secret vars
    config.get('jenkinsArtifactoryCredId',"dcapi_ci_vcs_user")
//    config.secretVars = getSecretEnvVars(config)

    //
    // essential/minimal params
    //
    log.debug("env.JOB_NAME = ${env.JOB_NAME}")
    config.get('application', env.JOB_NAME.replaceAll('%2F', '/').replaceAll('/', '-').replaceAll(' ', '-').toUpperCase())

    log.debug("config.application = ${config.application}")
    config.buildNumber = currentBuild.number

    config.repoBranch = config.repoBranch ?: config.repoBranch ?: env.BRANCH_NAME ?: "develop"

    log.debug("env.BRANCH_NAME = ${env.BRANCH_NAME}")
    log.debug("config.repoBranch = ${config.repoBranch}")

    config.emailFrom=config.get('emailFrom',"DCAPI.deployAutomation@dettonville.com")

    //
    // main release inputs
    //
    config.releaseDev=config.get('releaseDev',false)
    config.releaseStage=config.get('releaseStage',false)

    log.debug("config=${printToJsonString(config)}")

    return config
}

boolean promptForBoolean(String boolQuestion) {
    boolean release=false
    try {
        timeout(time: timeoutValue as Integer, unit: 'SECONDS') {
            release = input parameters: [
                    [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: "${boolQuestion}"]]
        }
    } catch(err) {
        release = false
        error "Timeout reached on ${env.BRANCH_NAME}. Pipeline failed: ${err}"
    }

    return release
}
