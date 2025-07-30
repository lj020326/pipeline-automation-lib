#!/usr/bin/env groovy

@Library("pipelineAutomationLib")

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

Logger.init(this, LogLevel.INFO)
Logger log = new Logger(this)

pipeline {

    agent {
        label 'DTL'
    }

    parameters {
        choice(choices: "STAGE\nDEV\nPROD", description: "Choose App Environment", name: 'AppEnvironment')
    }

    options {
        timestamps()
    }

    environment {
        RELEASE_SPEC=DCAPI/dcapi_flows/devportal.json
//        SMOKE_TEST = "DCAPI/Acceptance_Test_Jobs/Smoke_Test"
//        SANITY_TEST = "DCAPI/Acceptance_Test_Jobs/Sanity_Test"
        SMOKE_TEST = "DCAPI/Jobs/Test_Jobs/Smoke Test"
        SANITY_TEST = "DCAPI/Jobs/Test_Jobs/Sanity Test"
    }

    stages {
        stage("Run Smoke Test") {
            steps {
                script {
                    List paramList=[]
                    params.each { key, value ->
                        paramList.add([$class: 'StringParameterValue', name: key, value: value])
                    }

                    log.info("Run Smoke Tests")
                    build job: "${SMOKE_TEST}", parameters: paramList, wait: true
                }
            }
        }
        stage("Run Sanity Test") {
            steps {
                script {
                    log.info("Run Sanity Tests")
//                build job: "${SANITY_TEST}", parameters: params, wait: true
                }
            }
        }
    }

    post {
        always {
            sendEmail(currentBuild, env)
        }
    }
}


