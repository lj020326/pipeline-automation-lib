
// ref: https://stackoverflow.com/questions/36199072/how-to-get-the-script-name-in-groovy
// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field String scriptName = this.class.getName()

String baseFolder = "INFRA"

println("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")

jobFolder = "${baseFolder}/bootstrap-reference-repos"

println("${scriptName}: Creating ${jobFolder} job")

pipelineJob(jobFolder) {
    parameters {
		stringParam('JenkinsNodeLabel', 'controller', "Specify the Jenkins node/label")
    }
    definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
        cps {
            script("bootstrapReferenceReposJob()")
            sandbox()
        }
    }
}


jobFolder = "${baseFolder}/bootstrap-all-reference-repos"
println("${scriptName}: Creating ${jobFolder} job")

pipelineJob(jobFolder) {
    definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
        cps {
            script("bootstrapAllReferenceReposJob()")
            sandbox()
        }
    }
//     triggers {
//       cron('@midnight')
//     }
}

println("${scriptName}: Finished creating reference repo jobs")
