#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.ath.ApiTestHarness

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

    ApiTestHarness ath = new ApiTestHarness(this)

    pipeline {

        agent {
            label "QA-LINUX || PROD-LINUX"
        }

        tools {
            maven 'M3'
        }

        environment {
            // ref: https://stackoverflow.com/questions/48292208/set-git-discovery-across-filesystem-to-allow-service-to-access-git-repo-stored-u
            GIT_DISCOVERY_ACROSS_FILESYSTEM=1
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            skipDefaultCheckout()
            timestamps()
            timeout(time: 4, unit: 'HOURS') //Should not take longer than 2 hours to run
        }

        stages {

            stage("Pre-Test Steps") {
                steps {
                    script {
                        log.debug("NODE_NAME = ${env.NODE_NAME}")
                        ath.runPreTestSteps(params)
                    }
                }
            }

            stage("Run Acceptance Tests") {
                steps {
                    script {
                        log.info("Running tests")
                        ath.runTests()
                    }
                }
            }

            stage('Post-Test Steps') {

                steps {
                    script {
                        log.info('Run Post-Test Steps')
                        ath.runPostTestSteps()
                    }
                }
            }
        }
        post {
            success {
                script {
                    ath.runPostJobHandler("success")
                }
            }
            failure {
                script {
                    ath.runPostJobHandler("failure")
                }
            }
            aborted {
                script {
                    ath.runPostJobHandler("aborted")
                }
            }
            changed {
                script {
                    ath.runPostJobHandler("changed")
                }
            }
            always {
                script {
                    ath.runPostJobHandler("always")
                }
            }
        }

    }

} // body

