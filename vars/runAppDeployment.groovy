#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.deployment.AppDeploymentUtil

def call(Map params=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)
    AppDeploymentUtil deployUtil = new AppDeploymentUtil(this)

    pipeline {

        agent {
            label "QA-LINUX || PROD-LINUX"
        }

        tools {
            maven 'M3'
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            skipDefaultCheckout()
            timestamps()
            timeout(time: 4, unit: 'HOURS') //Should not take longer than 2 hours to run
        }

        stages {

            stage("Initialize Pipeline") {
                steps {
                    script {
                        deployUtil.runPreDeploymentSteps(params)
                    }
                }
            }

            stage("Deploy Application Component(s)") {
                steps {
                    script {
                        deployUtil.runAppDeployment()
                    }
                }
            }

            stage("Run Post-Deployment Tests") {
                steps {
                    script {
                        deployUtil.runPostDeploymentSteps()
                    }
                }
            }

        }
        post {
            success {
                script {
                    deployUtil.runPostJobHandler("success")
                }
            }
            failure {
                script {
                    deployUtil.runPostJobHandler("failure")
                }
            }
            aborted {
                script {
                    deployUtil.runPostJobHandler("aborted")
                }
            }
            changed {
                script {
                    deployUtil.runPostJobHandler("changed")
                }
            }
            always {
                script {
                    deployUtil.runPostJobHandler("always")
                    cleanWs()
                }
            }
        }

    }

} // body

