
@Library("pipelineAutomationLib")

import release
import sendEmail

/**
 * This pipeline requires four values:
 *
 * ARTIFACT_VERSION: Which version to deploy? Passed in as a job parameter.
 * ENV_SPEC: What environment to deploy to? Specified within the job configuration.
 * RELEASE_SPEC: Where to deploy it? Specified within the job configuration.
 * WORKFLOW: How to deploy it? Specified within the job configuration.
 *
 */
pipeline {

    agent {
        label 'DTL'
    }

    parameters {
        string(name: 'ARTIFACT_VERSION')
        booleanParam(defaultValue: true, description: "Run Simulated Mode?", name: 'UseSimulationMode')
    }

    options {
        timestamps()
    }

    environment {
        ARA_CLI = 'https://gitrepository.dettonville.int/artifactory/releases/com/dettonville/ara/ara-release-cli/1.0.3/ara-release-cli-1.0.3.tar'
        ENV_SPEC = "DCAPI/env_specs/stage_env_spec.json"
        RELEASE_SPEC = "DCAPI/dcapi_flows/devportal.json"
        WORKFLOW = "mc_api_dfs_workflows.dcapi_deploy_devportal"

        POST_RELEASE_TEST = "DCAPI/Acceptance_Test_Jobs/Post_Release_Test_Stage"
    }

    stages {

        stage("Setup") {
            agent {
                label 'DEVCLD-LIN7'
            }
            steps {
                deleteDir()
                script {
                    git 'https://gitrepository.dettonville.int/stash/scm/api/testsslserver.git'
                    sh 'mvn clean compile'
                    stash name: 'connectivity-check'
                }
            }
        }

        stage('Release') {
            when {
                expression { !params.UseSimulationMode }
            }
            steps {
                echo "Release the hounds!!"
                release(
                        "${ENV_SPEC}",
                        "${RELEASE_SPEC}",
                        "${WORKFLOW}",
                        "${ARTIFACT_VERSION}"
                )
            }
        }

        stage('Start Post Release Acceptance Test') {
            steps {
                script {
                    componentName = RELEASE_SPEC.split("\\/")[-1].split("\\.", 2)[0]
                    echo "componentName=${componentName}"
                    recipients = emailextrecipients([
                            [$class: 'CulpritsRecipientProvider'],
                            [$class: 'DevelopersRecipientProvider'],
                            [$class: 'RequesterRecipientProvider']
                    ])
                    List paramList = [
                            [$class: 'StringParameterValue', name: 'componentName', value: componentName],
                            [$class: 'StringParameterValue', name: 'componentVersion', value: ARTIFACT_VERSION],
                            [$class: 'StringParameterValue', name: 'alwaysEmailList', value: recipients]
                    ]
                    echo "paramList=${paramList}"

                    if (!params.UseSimulationMode) {
                        build job: "${POST_RELEASE_TEST}", parameters: paramList, wait: false
                    }
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
