
// ref: https://stackoverflow.com/questions/36199072/how-to-get-the-script-name-in-groovy
// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field String scriptName = this.class.getName()

String baseFolder = "INFRA"

jobFolder = "${baseFolder}/build-docker-image"

// // ref: https://stackoverflow.com/questions/40215394/how-to-get-environment-variable-in-jenkins-groovy-script-console
// def envVars = Jenkins.instance.
//                    getGlobalNodeProperties().
//                    get(hudson.slaves.EnvironmentVariablesNodeProperty).
//                    getEnvVars()
//
// if (!envVars?.JENKINS_ENV) {
//     println("JENKINS_ENV not defined - skipping vm-templates project definition")
//     return
// }
//
// String jenkinsEnv = envVars.JENKINS_ENV

println("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")
if (JENKINS_ENV=='PROD') {
    createDockerJobs(this)

    println("${scriptName}: Finished creating docker jobs")
}

//******************************************************
//  Function definitions from this point forward
//
void createDockerJobs(def dsl) {

    dsl.pipelineJob(jobFolder) {
        parameters {
            stringParam('GitRepoUrl', "ssh://git@gitea.admin.dettonville.int:2222/infra/docker-jenkins.git", "Specify the git repo image URL")
            stringParam('GitRepoBranch', "main", "Specify the git repo branch")
            stringParam('GitCredentialsId', "bitbucket-ssh-jenkins", "Specify the git repo credential ID")
            stringParam('RegistryUrl', "https://media.johnson.int:5000", "Specify the RegistryUrl")
            stringParam('RegistryCredId',  "docker-registry-admin", "Specify the RegistryCredId")
            stringParam('BuildImageLabel',  "docker-jenkins:latest", "Specify the BuildImageLabel")
            stringParam('BuildDir', "image/base", "Specify the BuildDir")
            stringParam('BuildPath', ".", "Specify the BuildPath")
            stringParam('BuildArgs', "", "Specify the BuildArgs")
            stringParam('DockerFile', "", "Specify the docker file")
            stringParam('ChangedEmailList', "", "Specify the email recipients for changed status")
            stringParam('AlwaysEmailList', "", "Specify the email recipients for always status")
            stringParam('FailedEmailList', "", "Specify the email recipients for failed status")
            stringParam('Timeout', "1", "Specify the job timeout")
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
                script("buildDockerImageJob()")
                sandbox()
            }
        }
    }
}