// pipeline-automation-lib/vars/createRepoTestJobs.groovy
// This file now simply calls the static method
import com.dettonville.jenkins.jobdsl.RepoTestJobCreator

def call(dsl, Map repoJobConfigs) {
    RepoTestJobCreator.createRepoTestJobsStatic(dsl, repoJobConfigs)
}
