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

// ref: https://stackoverflow.com/questions/36199072/how-to-get-the-script-name-in-groovy
// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field String scriptName = this.class.getName()

String pipelineConfigYaml = "config.gitea-orgs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
println("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
println("${scriptName}: seedJobConfigs=${seedJobConfigs}")

Map giteaOrgConfig = seedJobConfigs.pipelineConfig
println("${scriptName}: giteaOrgConfig=${giteaOrgConfig}")

String baseFolder = giteaOrgConfig.baseFolder

println("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")

if (JENKINS_ENV=='PROD') {
    createGiteaOrgFolder(this, giteaOrgConfig)

    println("${scriptName}: Finished creating GITEA Organization Folder jobs")
}

// ref: https://stackoverflow.com/questions/25039270/how-to-groovy-ify-a-null-check
// ref: https://blog.mrhaki.com/2009/08/groovy-goodness-elvis-operator.html
def defaultValue(def value, def defaultValue) {
    return value ?: defaultValue
}


//******************************************************
//  Function definitions from this point forward
//
void createGiteaOrgFolder(def dsl, Map config) {
    String logPrefix = "${scriptName}->createGiteaOrgFolder():"

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

    ownerList.each { Map ownerConfigRaw ->

        println("${logPrefix} ownerConfigRaw=${ownerConfigRaw}")

        Map ownerConfig = MapMerge.merge(config.findAll { !["ownerList"].contains(it.key) }, ownerConfigRaw)
//         println("${logPrefix} ownerConfig=${ownerConfig}")

        String orgOwner = ownerConfig.ownerName
        String orgFolder = "${baseFolder}/${orgOwner}"

        // ref: https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/demos/jobs/gitea.yaml
        def folderObject = dsl.organizationFolder(orgFolder) {
            organizations {
                gitea {
                    serverUrl(orgServerUrl)
                    repoOwner(orgOwner)
                    credentialsId(gitCredentialsId)

                    traits {
                        giteaExcludeArchivedRepositories {}
                        giteaTagDiscovery {}
                        giteaBranchDiscovery {
                            // Values
                            //  1 : Exclude branches that are also filed as PRs
                            //  2 : Only branches that are also filed as PRs
                            //  3 : All branches
                            strategyId(defaultValue(ownerConfig.giteaBranchDiscoveryStrategyId, 3))
                        }
                        // consider pull requests from origin
                        giteaPullRequestDiscovery {
                            // Values
                            //  1 : Merging the pull request with the current target branch revision
                            //  2 : The current pull request revision
                            //  3 : Both the current pull request revision and the pull request merged with the current target branch revision
                            strategyId(defaultValue(ownerConfig.giteaPullRequestDiscoveryStrategyId, 3))
                        }
                        // consider pull requests from forks
                        giteaForkDiscovery {
                            // Values
                            //  1 : Merging the pull request with the current target branch revision
                            //  2 : The current pull request revision
                            //  3 : Both the current pull request revision and the pull request merged with the current target branch revision
                            strategyId(defaultValue(ownerConfig.giteaForkDiscoveryStrategyId, 3))

                            // only one trust level is allowed; default: trust contributors
                            trust {
                                giteaTrustContributors {}
                            }
                        }
                        // Override webhook management; only one mode is allowed
                        giteaWebhookRegistration {
                            mode(defaultValue(ownerConfig.giteaWebhookRegistration, 'ITEM'))
                            // mode('DISABLED')
                        }
//                         giteaSSHCheckout {
//                             credentialsId(ownerConfig.gitSshCredentialsId)
//                         }
                    }
                }
                // not unique to Gitea but useful for fine-grained configuration of e.g. tag building strategies
                buildStrategies {
                    buildRegularBranches {}
                    buildChangeRequests {
                        ignoreTargetOnlyChanges(defaultValue(ownerConfig.ignoreTargetOnlyChanges, true))
                        ignoreUntrustedChanges(defaultValue(ownerConfig.ignoreUntrustedChanges, false))
                    }
                    buildTags {
                        atLeastDays('-1')
                        atMostDays('1')
                    }
                }
            }
            triggers {
                cron('@midnight')
            }
            // "Orphaned Item Strategy"
            orphanedItemStrategy {
                discardOldItems {
                    daysToKeep(defaultValue(ownerConfig.daysToKeep, -1))
                    numToKeep(defaultValue(ownerConfig.numToKeep, -1))
                }
            }

        }

        if (ownerConfig?.scriptPath) {
            println("${scriptName}: adding to job scriptPath=[${ownerConfig.scriptPath}]")
            folderObject.projectFactories {
                workflowMultiBranchProjectFactory {
                    // Relative location within the checkout of your Pipeline script.
                    scriptPath(ownerConfig.scriptPath)
                }
            }
        }

        // ref: https://stackoverflow.com/questions/62760438/jenkins-job-dsl-trigger-is-deprecated
        if (ownerConfig?.cronSpecification) {
            println("${scriptName}: adding to job cronSpecification=[${ownerConfig.cronSpecification}]")
            folderObject.cron {
                spec(ownerConfig.cronSpecification)
            }
        }
        if (ownerConfig?.periodicInterval) {
            println("${scriptName}: adding to job periodicInterval=[${ownerConfig.periodicInterval}]")
            folderObject.triggers {
                periodicFolderTrigger {
                    interval(ownerConfig.periodicInterval)
                }
            }
        }
        if (ownerConfig?.childTriggerInterval) {
            println("${scriptName}: adding to job periodicFolderTriggerInterval=[${ownerConfig.childTriggerInterval}]")
            // "Scan Organization Folder Triggers" : 5 minutes
            // ref: https://issues.jenkins.io/browse/JENKINS-59642
            // time in milliseconds
            //   1 min = 1000 milliseconds (1 second) * 60 seconds = 60000 milliseconds)
            //   5 min = 1000 milliseconds (1 second) * 60 seconds * 5 minutes = 300000 milliseconds)
            //   1 hour = 1000 milliseconds (1 second) * 60 seconds * 60 minutes = 3600000 milliseconds)
            //   1 day = 1000 milliseconds (1 second) * 60 seconds * 60 minutes * 24 hours= 86400000 milliseconds)
            folderObject.configure { node ->
                def templates = node / 'properties' / 'jenkins.branch.OrganizationChildTriggersProperty' / templates
                templates << 'com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger' {
                    // spec('* * * * *')
                    spec('H H * * *')
                    interval(ownerConfig.childTriggerInterval)
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
