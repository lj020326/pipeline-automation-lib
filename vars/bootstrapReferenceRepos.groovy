
// ref: https://argus-sec.com/blog/engineering-blog/how-you-can-use-git-reference-repository-to-reduce-jenkins-disk-space/

import java.io.File
import java.nio.file.Paths

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils


def call() {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    //Specify the list of your git projects/repositories.
    List repoList = [
        [name: 'ansible-datacenter', url: "git@bitbucket.org:lj020326/ansible-datacenter.git"],
        [name: 'vm-templates', url: "git@bitbucket.org:lj020326/vm-templates.git"]
    ]

    String baseDir = "/var/jenkins_home/git_repo_references"
    String gitCredentialsId = "bitbucket-ssh-jenkins"

    // Cron job configurations – configured to run every day at 23:00 PM
    cron_cfg="H 23 * * *"
    properties([pipelineTriggers([cron("${cron_cfg}")])])

    pipeline {

        agent { label 'controller' }

        stages {

            stage("Pre-Checks") {
                steps {
                    script {
                        log.info("repoList=${JsonUtils.printToJsonString(repoList)}")

                        for (repoConfig in repoList) {

                            String repoName = repoConfig.name
                            String repoUrl = repoConfig.url
                            String repoDir = "${baseDir}/${repoName}"
                            if (!Paths.get(repoDir).toFile().isDirectory()) {

                                File dir = new File(repoDir)
                                if (dir.mkdirs()) {
                                    println("New dir: ${repoDir} successfully created!")
                                    println("Cloning relevant git repo...")
                                    gitInitialClone(this, gitCredentialsId, repoDir, repoConfig)
                                }
                                else {
                                    error("Failed to create dir: ${repoDir}. Exiting...")
                                }
                            }
                            else {
                                println("Directory: ${repoDir} already exists!")
                            }
                        }
                    }
                }
            }

            stage("Git update") {
                steps {
                    script {
                        for (repoConfig in repoList) {
                            String repoName = repoConfig.name
                            String repoUrl = repoConfig.url
                            String repoDir = "${baseDir}/${repoName}"
                            println("Updating the git repo: ${repoName}, located in ${repoDir}")
                            gitFetchPrune(this, gitCredentialsId, repoDir, repoConfig)
                        }
                    }
                }

            }
        }

    }
}

def gitInitialClone(def dsl, String gitCredentialsId, String repoDir, Map repoConfig) {
    String repoName = repoConfig.name
    String repoUrl = repoConfig.url
    String repoGitName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.length())
    String repoGitDir = "${repoDir}/${repoGitName}"

    dsl.dir(repoDir) {
        println("Git initially cloning repo: ${repoName}")
        dsl.sshagent([gitCredentialsId]) {
            // ref: https://docs.cloudbees.com/docs/cloudbees-ci-kb/latest/client-and-managed-controllers/how-to-create-and-use-a-git-reference-repository
            // ref: https://plugins.jenkins.io/git/#plugin-content-combining-repositories
            // ref: https://argus-sec.com/blog/engineering-blog/how-you-can-use-git-reference-repository-to-reduce-jenkins-disk-space/
            dsl.sh "git clone --mirror ${repoUrl}"
        }
    }
}

def gitFetchPrune(def dsl, String gitCredentialsId, String repoDir, Map repoConfig) {
    String repoUrl = repoConfig.url
    String repoGitName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.length())
    String repoGitDir = "${repoDir}/${repoGitName}"
    dsl.dir(repoGitDir) {
        println("Git fetch repo: ${repoGitDir}...")
        dsl.sshagent([gitCredentialsId]) {
            dsl.sh "git fetch --all --prune"
        }
        println("Finished fetching git repo on ${repoGitDir}")
    }
}
