
String baseFolder = "INFRA"

jobFolder = "${baseFolder}/bootstrap-reference-repos"

pipelineJob(jobFolder) {
    definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
        cps {
            script("bootstrapReferenceRepos()")
            sandbox()
        }
    }
    triggers {
      cron("H/5 * * * *")
    }
}
