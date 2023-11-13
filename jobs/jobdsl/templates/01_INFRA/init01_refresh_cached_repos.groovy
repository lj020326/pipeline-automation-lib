
String baseFolder = "INFRA"

jobFolder = "${baseFolder}/reset-cached-repos"

pipelineJob(jobFolder) {
    definition {
        cps {
            script("refreshCachedRepos()")
            sandbox()
        }
    }
    triggers {
      cron("H/5 * * * *")
    }
}
