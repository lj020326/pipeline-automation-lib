@Library('dcapi-jenkins-pipeline-libs')
import release

/**
 * This pipeline requires five values:
 *
 * ARTIFACT_VERSION: Which version to deploy? Passed in as a job parameter.
 * ENV_SPEC: What environment to deploy to? Specified within the job configuration.
 * RELEASE_SPEC: Where to deploy it? Specified within the job configuration.
 * WORKFLOW: How to deploy it? Specified within the job configuration.
 * SMOKE_TEST_ENV: The App Environment used by the SMOKE TEST in the QA test env.
 *
 */
pipeline {

    agent {
        label 'QA-LINUX'
    }

    parameters {
        string(name: 'ARTIFACT_VERSION')
        booleanParam(name: 'RUN_POSTDEPLOY_TESTS', defaultValue: true, description: "Run post deployment tests?\nE.g., Set false if deployment is part of a group deployment")
    }

    options {
        timestamps()
    }

    environment {
        ARA_CLI = 'https://artifacts.dettonville.int/artifactory/releases/com/dettonville/ara/ara-release-cli/1.0.3/ara-release-cli-1.0.3.tar'
        ENV_SPEC = "DCAPI/env_specs/dev_env_spec.json"
        RELEASE_SPEC = "DCAPI/dcapi_flows/devportal.json"
        WORKFLOW = "mc_api_dfs_workflows.dcapi_deploy_devportal"

//        SMOKE_TEST_ENV = "STAGE_EXTERNAL"
        SMOKE_TEST_ENV = "DEV"
    }

    stages {
        stage('Release') {
            steps {
                cleanWs()

                release(
                    "${ENV_SPEC}",
                    "${RELEASE_SPEC}",
                    "${WORKFLOW}",
                    "${ARTIFACT_VERSION}"
                )
                cleanWs()
            }
        }
    }

    post {
        always {
            script {
                final GString subject = "Job '${env.JOB_NAME.replaceAll('%2F', '/')}' (${currentBuild.displayName}) has finished with ${currentBuild.result ? currentBuild.result : "SUCCESS"}"
                if (env.DEPLOYMENT_NOTIFICATION_LIST) {
                    // Send email to predefined email list.
                    emailext (
                        mimeType: 'text/html',
                        body: '${SCRIPT, template="groovy-html.template"}',
                        recipientProviders: [culprits(), developers(), requestor()],
                        subject: "${subject}",
                        to: "${env.DEPLOYMENT_NOTIFICATION_LIST}"
                    )
                } else {
                    emailext (
                        mimeType: 'text/html',
                        body: '${SCRIPT, template="groovy-html.template"}',
                        recipientProviders: [culprits(), developers(), requestor()],
                        subject: "${subject}"
                    )
                }
                if (params.RUN_POSTDEPLOY_TESTS) {
                    runPostDeploymentTests("${SMOKE_TEST_ENV}")
                }
            }
        }
    }
}

/**
 * Run QA Smoke Test
 * @param environment - QA environment based on site endpoints. One of:
 *                          ['DEV",
 *                          "DEV_CLOUD",
 *                          "STAGE",
 *                          "STAGE_EXTERNAL",
 *                          "PROD_COPRO",
 *                          "PROD_STL",
 *                          "PROD_KSC",
 *                          "PROD_EXTERNAL"]
 */
void runPostDeploymentTests(String appEnvironment) {

//    String jobFolder = "DCAPI/Acceptance_Test_Jobs/${appEnvironment}/SMOKE/Multi-Platform"
    String jobFolder = "DCAPI/Acceptance_Test_Jobs/${appEnvironment}/SMOKE/Chrome"
    echo "jobFolder=${jobFolder}"

    build job: "${jobFolder}", wait: false

}
