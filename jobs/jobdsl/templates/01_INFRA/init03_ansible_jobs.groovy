#!/usr/bin/env groovy

// ref: https://stackoverflow.com/questions/44004636/jenkins-multibranch-pipeline-scan-without-execution
import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.JsonUtils

// separate configuration from job dsl "seedjob" code
// ref: https://stackoverflow.com/questions/47443106/jenkins-dsl-parse-yaml-for-complex-processing#54665138
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

String pipelineConfigYaml = "config.ansible-jobs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
println("configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
println("seedJobConfigs=${seedJobConfigs}")

Map basePipelineConfig = seedJobConfigs.pipelineConfig
println("basePipelineConfig=${basePipelineConfig}")

String baseFolder = basePipelineConfig.baseFolder
List yamlProjectConfigList = basePipelineConfig.yamlProjectConfigList

println("yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    println("Creating Ansible Jobs for ${projectConfigYamlFile}")

    Map ansibleJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    // println("seedJobConfigs=${ansibleJobConfigs}")

    Map pipelineConfig = ansibleJobConfigs.pipelineConfig
    println("pipelineConfig=${pipelineConfig}")

    createAnsibleJobs(this, pipelineConfig)

    // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
    // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
    listView("${baseFolder}/ansible-jobs") {
        jobs {
            regex("${baseFolder}/")
        }
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }

    listView("${baseFolder}/ansible-jobs-unstable") {
        jobs {
            regex("${baseFolder}/")
        }
        jobFilters {
            status {
                status(Status.UNSTABLE)
            }
        }
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }

}
println("Finished creating ansible jobs")

//******************************************************
//  Function definitions from this point forward
//
void createAnsibleJobs(def dsl, Map config) {
    String logPrefix = "createAnsibleJobs():"

    String baseFolder = config.baseFolder
    String repoFolder = config.repoFolder
    String repoUrl = config.repoUrl
    String mirrorRepoDir = config.mirrorRepoDir
    String gitCredentialsId = config.gitCredentialsId
    List runEnvList = config.runEnvList
    List ansibleJobList = config.ansibleJobList

    // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
    dsl.folder(baseFolder) {
        description "This folder contains jobs to run ANSIBLE SITE PLAY TAGS"
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    //     configure scmFolderLibraryTraits
    }

    runEnvList.each { String run_environment ->

        dsl.folder("${baseFolder}/${run_environment}") {
            description "This folder contains jobs to run ansible SITE play tags for ${run_environment}"
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        ansibleJobList.each { Map job_config ->
            String ansible_tag = job_config.ansible_tag
            String ansible_limit = job_config.get('ansible_limit', '')
            boolean skip_untagged = job_config.get('skip_untagged', false)

            dsl.folder("${baseFolder}/${run_environment}/${repoFolder}") {
                description "This folder contains jobs to run ansible SITE play tags for ${run_environment}/${ansible_tag}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
            }

            dsl.pipelineJob("${baseFolder}/${run_environment}/${repoFolder}/${ansible_tag}") {
                description "Run ansible SITE play tag for ${run_environment}/${ansible_tag}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
                keepDependencies(false)
                // ref: https://stackoverflow.com/questions/49262174/how-to-pass-paramaters-to-a-pipelinejob-in-dsl
                // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/job-parameters
                parameters {
                    stringParam("AnsibleLimitHosts", ansible_limit, "Limit playbook to specified inventory hosts\nE.g., 'app_adm','app_tableau','host01', 'host01,host02'" )
                    choiceParam("AnsibleDebugFlag", ['', '-v', '-vv', '-vvv', '-vvvv'], "Choose Ansible Debug Level")
                    booleanParam("AnsibleGalaxyForceOpt", false, 'Use Ansible Galaxy Force Mode?')
                    booleanParam("AnsibleGalaxyUpgradeOpt", true, 'Use Ansible Galaxy Upgrade?')
                    booleanParam("UseCheckDiffMode", false, 'Use Check+Diff Mode (Dry Run with Diffs)?')
                    booleanParam("SkipUntagged", skip_untagged, 'Skip Untagged plays?')
                }
                definition {
                    logRotator {
                       daysToKeep(-1)
                       numToKeep(10)
                       artifactNumToKeep(-1)
                       artifactDaysToKeep(-1)
                    }
                    cpsScm {
                        lightweight(true)
                        scriptPath("runAnsibleSiteTag.groovy")
                        scm {
                            git {
                                remote {
                                    url(repoUrl)
                                    credentials(gitCredentialsId)
                                }
                                branch("*/main")
                                // ref: https://jenkinsci.github.io/job-dsl-plugin/#method/javaposse.jobdsl.dsl.helpers.ScmContext.git
                                // ref: https://stackoverflow.com/questions/47620060/jenkins-add-git-submodule-to-multibranchpipelinejob-with-dsl#48693179
                                extensions {
    //                                 cleanBeforeCheckout()
    //                                 cleanCheckout {
    //                                     // Deletes untracked submodules and any other subdirectories which contain .git directories.
    //                                     deleteUntrackedNestedRepositories(true)
    //                                 }
                                    cloneOptions {
                                        shallow(true)
                                        depth(2)
                                        noTags(true)
                                        reference(mirrorRepoDir)
                                        honorRefspec(false)
                                        timeout(1)
                                    }
                                    submoduleOptions {
                                        disable(false)
                                        recursive(true)
                                        tracking(true)
                                        reference(mirrorRepoDir)
                                        timeout(null)
                                        parentCredentials(true)
                                    }
                                }
                            }
                        }
                    }
                }
                disabled(false)
            }
        }

        // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
        // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
        dsl.listView("${baseFolder}/${run_environment}/ansible-jobs-${repoFolder}") {
            jobs {
                regex("${baseFolder}/${repoFolder}/")
            }
            columns {
                status()
                weather()
                name()
                lastSuccess()
                lastFailure()
                lastDuration()
                buildButton()
            }
        }

        dsl.listView("${baseFolder}/${run_environment}/ansible-jobs-${repoFolder}-unstable") {
            jobs {
                regex("${baseFolder}/${repoFolder}/")
            }
            jobFilters {
                status {
                    status(Status.UNSTABLE)
                }
            }
            columns {
                status()
                weather()
                name()
                lastSuccess()
                lastFailure()
                lastDuration()
                buildButton()
            }
        }

    }

    // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
    // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
    dsl.listView("${baseFolder}/ansible-jobs-${repoFolder}") {
        jobs {
            regex("${baseFolder}/${repoFolder}/")
        }
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }

    dsl.listView("${baseFolder}/ansible-jobs-${repoFolder}-unstable") {
        jobs {
            regex("${baseFolder}/${repoFolder}/")
        }
        jobFilters {
            status {
                status(Status.UNSTABLE)
            }
        }
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }

}

//
// // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#method/javaposse.jobdsl.dsl.DslFactory.organizationFolder
// organizationFolder('example') {
//     description('This contains branch source jobs for Bitbucket and GitHub')
//     displayName('Organization Folder')
//     triggers {
//         cron('@midnight')
//     }
// }
