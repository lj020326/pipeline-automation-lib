// // Get a reference to your shared library's entry point
// def pipelineAutomationLib = this.getBinding().getProperty('pipelineAutomationLib')

// ref: https://stackoverflow.com/questions/36199072/how-to-get-the-script-name-in-groovy
// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field

import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.JenkinsLogger

@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)
//@Field JenkinsLogger log = new JenkinsLogger(this, logLevel: 'DEBUG', prefix: scriptName)

String baseFolder = "INFRA"

jobFolder = "${baseFolder}/build-docker-image"

// ref: https://stackoverflow.com/questions/40215394/how-to-get-environment-variable-in-jenkins-groovy-script-console
log.info("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")
createDockerJobs(this)
log.info("${scriptName}: Finished creating docker jobs")

//******************************************************
//  Function definitions from this point forward
//
void createDockerJobs(def dsl) {

    dsl.pipelineJob(jobFolder) {
        parameters {
            booleanParam('InitializeParamsOnly', false, 'Set to true to only initialize parameters and skip execution of stages.')
            stringParam('GitRepoUrl', "ssh://git@gitea.admin.dettonville.int:2222/infra/docker-jenkins.git", "Specify the git repo image URL")
            stringParam('GitRepoBranch', "main", "Specify the git repo branch")
            stringParam('GitCredentialsId', "gitea-ssh-jenkins", "Specify the git repo credential ID")
            stringParam("RegistryUrl", "https://media.johnson.int:5000", "Specify the RegistryUrl")
            stringParam("RegistryCredId", "docker-registry-admin", "Specify the RegistryCredId")
            stringParam("BuilderImage", "", "Specify the docker builder image (e.g., 'media.johnson.int:5000/ansible/ansible-runner:stable-2.18-py3.13')")
            stringParam("BuilderUid", "", "Specify the build runner UID (e.g., 'jenkins')")
            stringParam("BuilderGid", "", "Specify the build runner GID (e.g., 'jenkins')")
            stringParam("BuildImageName", "", "Specify the BuildImageName (e.g., 'docker-jenkins')")
            stringParam("BuildDir", ".", "Specify relative directory where docker build will run (e.g., 'image/base')")
            stringParam("BuildPath", ".", "Specify relative directory used as final argument to the docker command")
            stringParam("BuildTags", "", "Specify the docker image tags in comma delimited format (e.g., 'build-123,latest')")
            stringParam('BuildPlatforms', "linux/amd64", "Target architectures comma-separated (e.g., linux/amd64,linux/arm64)")
            stringParam("BuildArgs", "", "Specify the BuildArgs in JSON string format")
            stringParam('BuildTestCommand', "", "The shell command to run post-build, pre-push")
            booleanParam('BuildTestAppendIdArg', false, 'If true - append env.BUILD_NUMBER to test command')
            stringParam('BuildTestAppendIdOption', "", "Append option name to shell command followed by env.BUILD_NUMBER")
            booleanParam('RunTestCommandInsideContainer', false, 'If true - run buildTestCommand inside of target build container')
            stringParam('TestResultsPath', "", "Path to test result files to archive")
            stringParam('DockerFile', "", "Specify the docker file")
            stringParam('ChangedEmailList', "", "Specify the email recipients for job 'changed' status")
            stringParam('AlwaysEmailList', "", "Specify the email recipients for job 'always' status")
            stringParam('FailedEmailList', "", "Specify the email recipients for job 'failed' status")
            stringParam('Timeout', "4", "Specify the job timeout")
            stringParam('TimeoutUnit', "HOURS", "Specify the job timeout unit (HOURS, MINUTES, etc)")
        }
        definition {
            logRotator {
               daysToKeep(-1)
               numToKeep(40)
               artifactNumToKeep(-1)
               artifactDaysToKeep(-1)
            }
            cps {
                script("buildDockerImagePipeline()")
                sandbox()
            }
        }
        throttleConcurrentBuilds {
            categories(['docker_image_builds'])
        }
    }
}
