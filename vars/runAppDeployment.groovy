#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.deployment.AppDeploymentUtil

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

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
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }
        }

    }

} // body

