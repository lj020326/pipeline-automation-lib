
// ref: https://argus-sec.com/blog/engineering-blog/how-you-can-use-git-reference-repository-to-reduce-jenkins-disk-space/

import java.io.File
import java.nio.file.Paths

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.JsonUtils

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)

    String baseDir = "/var/jenkins_home/git_repo_references"
    String gitCredentialsId = "bitbucket-ssh-jenkins"

    pipeline {

//         agent { label 'controller' }
        agent {
            label config.jenkinsNodeLabel
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            disableConcurrentBuilds()
            timestamps()
            timeout(time: 3, unit: 'HOURS')
        }

        stages {

            stage("Pre-Checks") {
                steps {
                    script {
                        log.info("repoList=${JsonUtils.printToJsonString(config.repoList)}")
//                         sh("tree ${baseDir}")

                        for (repoConfig in config.repoList) {

                            String repoName = repoConfig.name
                            String repoUrl = repoConfig.url
                            String repoDir = "${baseDir}/${repoName}"

                            // ref: https://stackoverflow.com/questions/46705569/how-to-check-if-directory-exists-outside-of-workspace-from-a-jenkins-pipeline-sc
                            if (!fileExists(repoDir)) {

                                dir(repoDir) {
                                    log.info("New dir: ${repoDir} successfully created!")
                                    log.info("Cloning relevant git repo...")
                                    gitInitialClone(this, gitCredentialsId, repoDir, repoConfig)
                                }

                            }
                            else {
                                log.info("Directory: ${repoDir} already exists!")
                            }
                        }
                    }
                }
            }

            stage("Git update") {
                steps {
                    script {
                        for (repoConfig in config.repoList) {
                            String repoName = repoConfig.name
                            String repoUrl = repoConfig.url
                            String repoDir = "${baseDir}/${repoName}"
                            log.info("Updating the git repo: ${repoName}, located in ${repoDir}")
                            gitFetchPrune(this, gitCredentialsId, repoDir, repoConfig)
                        }
                    }
                }

            }
        }

    }
}

//@NonCPS
Map loadPipelineConfig(Map params) {
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"controller")
//     config.logLevel = config.get('logLevel', "INFO")
    config.logLevel = config.get('logLevel', "DEBUG")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')

//    config.emailDist = config.emailDist ?: "lee.johnson@dettonville.com"
    config.emailDist = config.get('emailDist',"lee.johnson@dettonville.com")
    // config.alwaysEmailDist = config.alwaysEmailDist ?: "lee.johnson@dettonville.com"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    //Specify the list of your git projects/repositories.
    List repoListDefault = [
        [name: 'ansible-datacenter', url: "git@bitbucket.org:lj020326/ansible-datacenter.git"],
        [name: 'vm-templates', url: "git@bitbucket.org:lj020326/vm-templates.git"]
    ]
    config.repoList = config.get('repoList',repoListDefault)

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"controller")

    log.debug("params=${params}")
    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}

def gitInitialClone(def dsl, String gitCredentialsId, String repoDir, Map repoConfig) {
    String repoName = repoConfig.name
    String repoUrl = repoConfig.url
    String repoGitName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.length())
    String repoGitDir = "${repoDir}/${repoGitName}"

    dsl.dir(repoDir) {
        log.info("Git initially cloning repo: ${repoName}")
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
        log.info("Git fetch repo: ${repoGitDir}")
        dsl.sshagent([gitCredentialsId]) {
            dsl.sh "git fetch --all --prune"
        }
        log.info("Finished fetching git repo on ${repoGitDir}")
    }
}
