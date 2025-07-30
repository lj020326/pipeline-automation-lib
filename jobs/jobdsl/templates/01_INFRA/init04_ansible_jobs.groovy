#!/usr/bin/env groovy

// // Get a reference to your shared library's entry point
// def pipelineAutomationLib = this.getBinding().getProperty('pipelineAutomationLib')

// ref: https://stackoverflow.com/questions/44004636/jenkins-multibranch-pipeline-scan-without-execution
import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.JenkinsLogger

// separate configuration from job dsl "seedjob" code
// ref: https://stackoverflow.com/questions/47443106/jenkins-dsl-parse-yaml-for-complex-processing#54665138
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

// ref: https://stackoverflow.com/questions/36199072/how-to-get-the-script-name-in-groovy
// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)
//@Field JenkinsLogger log = new JenkinsLogger(this, logLevel: 'DEBUG', prefix: scriptName)

String pipelineConfigYaml = "config.ansible-jobs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
log.debug("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
// log.info("${scriptName}: seedJobConfigs=${seedJobConfigs}")

Map basePipelineConfig = seedJobConfigs.pipelineConfig
log.debug("${scriptName}: basePipelineConfig=${JsonUtils.printToJsonString(basePipelineConfig)}")

String baseFolder = basePipelineConfig.baseFolder
List yamlProjectConfigList = basePipelineConfig.yamlProjectConfigList

log.debug("${scriptName}: yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    log.info("${scriptName}: Creating Ansible Jobs for ${projectConfigYamlFile}")

    Map ansibleJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    // log.info("${scriptName}: seedJobConfigs=${JsonUtils.printToJsonString(ansibleJobConfigs)}")

    Map pipelineConfig = ansibleJobConfigs.pipelineConfig
    log.debug("${scriptName}: pipelineConfig=${JsonUtils.printToJsonString(pipelineConfig)}")

    createAnsibleJobs(this, pipelineConfig)

}
log.info("${scriptName}: Finished creating ansible jobs")

//******************************************************
//  Function definitions from this point forward
//
void createAnsibleJobs(def dsl, Map pipelineConfig) {
    String logPrefix = "createAnsibleJobs():"

//     log.info("${logPrefix} pipelineConfig=${JsonUtils.printToJsonString(pipelineConfig)}")

    String baseFolder = pipelineConfig.baseFolder
    String repoFolder = pipelineConfig.repoFolder
    String repoUrl = pipelineConfig.repoUrl
    String mirrorRepoDir = pipelineConfig.mirrorRepoDir
    String gitCredentialsId = pipelineConfig.gitCredentialsId
    String jobScript = pipelineConfig.jobScript
    List ansibleJobList = pipelineConfig.ansibleJobList

    Map runEnvMap = pipelineConfig.runEnvMap

    // ref: https://stackoverflow.com/questions/40215394/how-to-get-environment-variable-in-jenkins-groovy-script-console
    def envVars = Jenkins.instance.
                       getGlobalNodeProperties().
                       get(hudson.slaves.EnvironmentVariablesNodeProperty).
                       getEnvVars()

    if (!envVars?.JENKINS_ENV) {
        log.info("${scriptName}: JENKINS_ENV not defined - skipping vm-templates project definition")
        return
    }

    String jenkinsEnv = envVars.JENKINS_ENV

    if (!runEnvMap.containsKey(jenkinsEnv)) {
        log.info("${scriptName}: key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` project definition, skipping ansible-jobs build")
        return
    }

    List runEnvList = runEnvMap[jenkinsEnv]

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
    }

    runEnvList.each { Map envConfigsRaw ->
        Map envConfigs = MapMerge.merge(pipelineConfig.findAll { !["runEnvMap","ansibleJobList"].contains(it.key) }, envConfigsRaw)
        String runEnvironment = envConfigs.environment

        dsl.folder("${baseFolder}/${runEnvironment}") {
            description "This folder contains jobs to run ansible SITE play tags for ${runEnvironment}"
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        ansibleJobList.each { Map jobConfigsRaw ->

            log.info("${logPrefix} jobConfigsRaw=${JsonUtils.printToJsonString(jobConfigsRaw)}")

            Map jobConfigs = MapMerge.merge(envConfigs, jobConfigsRaw)
            log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

            String ansibleTag = jobConfigs.ansible_tag
            String ansibleLimit = jobConfigs.get('ansible_limit', '')
            boolean skipUntagged = jobConfigs.get('skip_untagged', false)
            boolean skipAlwaysTag = jobConfigs.get('skip_always_tag', false)

            dsl.folder("${baseFolder}/${runEnvironment}/${repoFolder}") {
                description "This folder contains jobs to run ansible SITE play tags for ${runEnvironment}/${ansibleTag}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
            }

            // ref: https://github.com/jenkinsci/job-dsl-plugin/wiki/Job-DSL-Commands#job
            // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#path/multibranchPipelineJob
            def jobObject = dsl.pipelineJob("${baseFolder}/${runEnvironment}/${repoFolder}/${ansibleTag}") {
                description "Run ansible SITE play tag for ${runEnvironment}/${ansibleTag}"
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
                    stringParam("AnsibleLimitHosts", ansibleLimit, "Limit playbook to specified inventory hosts\nE.g., 'app_adm','app_tableau','host01', 'host01,host02'" )
                    choiceParam("AnsibleDebugFlag", ['', '-v', '-vv', '-vvv', '-vvvv'], "Choose Ansible Debug Level")
                    booleanParam("AnsibleGalaxyForceOpt", false, 'Use Ansible Galaxy Force Mode?')
                    booleanParam("AnsibleGalaxyUpgradeOpt", false, 'Use Ansible Galaxy Upgrade?')
                    booleanParam("UseCheckDiffMode", false, 'Use Check+Diff Mode (Dry Run with Diffs)?')
                    booleanParam("SkipUntagged", skipUntagged, 'Skip Untagged plays?')
                    booleanParam("SkipAlwaysTag", skipAlwaysTag, "Skip 'always' tagged plays?")
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
                        scriptPath(jobScript)
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
//                                     cleanBeforeCheckout()
//                                     cleanCheckout {
//                                         // Deletes untracked submodules and any other subdirectories which contain .git directories.
//                                         deleteUntrackedNestedRepositories(true)
//                                     }
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
            // ref: https://stackoverflow.com/questions/62760438/jenkins-job-dsl-trigger-is-deprecated
            if (jobConfigs?.cronSpecification) {
                log.info("${scriptName}: adding to job cronSpecification=[${jobConfigs?.cronSpecification}]")
                jobObject.properties {
                    pipelineTriggers {
                        triggers {
                            cron {
                                spec(jobConfigs.cronSpecification)
                            }
                        }
                    }
                }
            }
        }

        // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
        // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
        dsl.listView("${baseFolder}/ansible-jobs") {
            jobs {
                regex(".*")
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

        dsl.listView("${baseFolder}/ansible-jobs-unstable") {
            jobs {
                regex(".*")
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

        // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
        // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
        dsl.listView("${baseFolder}/${runEnvironment}/ansible-jobs-${repoFolder}") {
            jobs {
                regex(".*")
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

        dsl.listView("${baseFolder}/${runEnvironment}/ansible-jobs-${repoFolder}-unstable") {
            jobs {
                regex(".*")
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

        // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
        // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
        dsl.listView("${baseFolder}/ansible-jobs-${repoFolder}") {
            jobs {
                regex(".*")
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
                regex(".*")
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
}
