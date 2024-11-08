
String baseFolder = "INFRA"

jobFolder = "${baseFolder}/bootstrap-reference-repos"

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
    triggers {
      cron('@midnight')
    }
}
