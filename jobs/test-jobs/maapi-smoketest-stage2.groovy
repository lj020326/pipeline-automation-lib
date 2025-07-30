@Library("pipelineAutomationLib")_

import sendEmail

/**
 * This pipeline requires four values:
 *
 * ARTIFACT_VERSION: Which version to deploy? Passed in as a job parameter.
 * ENV_SPEC: What environment to deploy to? Specified within the job configuration.
 * RELEASE_SPEC: Where to deploy it? Specified within pipeline code.
 * WORKFLOW: How to deploy it? Specified within pipeline code.
 *
 */
pipeline {

    agent {
        label 'DTL'
    }

    options {
        timestamps()
    }

    environment {
//        SMOKE_TEST = "DCAPI/Acceptance_Test_Jobs/Smoke_Test"
        SMOKE_TEST = "DCAPI/Jobs/Test_Jobs/Smoke Test"
    }

    stages {
        stage('Smoke Test') {
            steps {
                script {
                    List paramList = [
                            [$class: 'StringParameterValue', name: 'AppEnvironment', value: "STAGE"]
                    ]

                    build job: "${SMOKE_TEST}", parameters: paramList, wait: false
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
