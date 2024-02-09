#!/usr/bin/env groovy

// ref: https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/integrations/src/test/resources/io/jenkins/plugins/casc/SeedJobTest_withGiteaOrganisation.yml
// ref: https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/demos/jobs/gitea.yaml

// ref: https://stackoverflow.com/questions/44004636/jenkins-multibranch-pipeline-scan-without-execution
import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.JsonUtils

// separate configuration from job dsl "seedjob" code
// ref: https://stackoverflow.com/questions/47443106/jenkins-dsl-parse-yaml-for-complex-processing#54665138
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

String pipelineConfigYaml = "config.gitea-orgs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
println("configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
println("seedJobConfigs=${seedJobConfigs}")

Map giteaOrgConfig = seedJobConfigs.pipelineConfig
println("giteaOrgConfig=${giteaOrgConfig}")

String baseFolder = giteaOrgConfig.baseFolder

createGiteaOrgFolder(this, giteaOrgConfig)

println("Finished creating GITEA Organization Folder jobs")

//******************************************************
//  Function definitions from this point forward
//
void createGiteaOrgFolder(def dsl, Map config) {
    String logPrefix = "createGiteaOrgFolder():"

    println("${logPrefix} config=${config}")

    String baseFolder = config.baseFolder
    String orgServerUrl = config.serverUrl
    String gitCredentialsId = config.gitCredentialsId
    List ownerList = config.ownerList

    // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
    dsl.folder(baseFolder) {
        description "This folder contains jobs to run GITEA org jobs"
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

    ownerList.each { Map ownerConfig ->

        println("${logPrefix} ownerConfig=${ownerConfig}")

        String orgOwner = ownerConfig.ownerName
        String repoScript = ownerConfig.repoScript
        String orgFolder = "${baseFolder}/${orgOwner}"

        // ref: https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/demos/jobs/gitea.yaml
        dsl.organizationFolder(orgFolder) {
            organizations {
                gitea {
                    serverUrl(orgServerUrl)
                    repoOwner(orgOwner)
//                     script(repoScript)
                    credentialsId(gitCredentialsId)

                    traits {
                        giteaExcludeArchivedRepositories {}
                        giteaTagDiscovery {}
                        giteaBranchDiscovery {
                            strategyId(1)
                        }
                        giteaPullRequestDiscovery {
                            strategyId(2)
                        }
                        giteaForkDiscovery {
                            strategyId(1)
                            trust {
                                giteaTrustContributors {}
                            }
                        }
                        giteaWebhookRegistration {
                            mode('ITEM')
                        }
                        giteaSSHCheckout {
                            credentialsId(gitCredentialsId)
                        }
                    }
                }
            }
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
