
@Library('dcapi-jenkins-pipeline-libs')
import createGitTag
import getMavenProjectVersion
import gitCheckout
import runSonarScan
import sendEmail

def call(def buildSlaveLabel) {

    pipeline(buildSlaveLabel) {

        agent any

        tools {
            maven 'M3'
        }

        options {
            skipDefaultCheckout(true)
        }

        stages {
            stage('Checkout') {
                steps {
                    gitCheckout(scm)
                }
            }

            stage('Compile') {
                steps {
                    sh 'mvn clean compile -U'
                }
            }

            stage('Test') {
                steps {
                    sh 'mvn test'
                }
            }

            stage('Sonar') {
                steps {
                    runSonarScan(BRANCH_NAME)
                }
            }

            stage('Publish to Artifactory') {
                steps {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        post {
            always {
                junit 'target/surefire-reports/*.xml'
            }

            changed {
                sendEmail(currentBuild, env)
            }

            success {
                script {
                    if (BRANCH_NAME == 'master') {
                        createGitTag(getMavenProjectVersion(), env)
                    }
                }
            }
        }
    }
}