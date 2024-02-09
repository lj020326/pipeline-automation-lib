
String baseFolder = "INFRA"

jobFolder = "${baseFolder}/reset-cached-repos"

pipelineJob(jobFolder) {
    definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
        cps {
            script("refreshCachedRepos()")
            sandbox()
        }
    }
    triggers {
      cron("H/5 * * * *")
    }
}
