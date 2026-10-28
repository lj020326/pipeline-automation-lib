#!/usr/bin/env groovy

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.JenkinsLogger
import com.dettonville.jobdsl.PipelineJobFactory

@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

import groovy.transform.Field
@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)

String pipelineConfigYaml = "config.ansible-jobs.yml"
String configFilePath = "${new File(__FILE__).parent}"
log.debug("${scriptName}: configFilePath:${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
List yamlProjectConfigList = seedJobConfigs.yamlProjectConfigList

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    log.info("${scriptName}: Creating Ansible Jobs for ${projectConfigYamlFile}")

    Map ansibleJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    createAnsibleJobs(this, ansibleJobConfigs)
}
log.info("${scriptName}: Finished creating ansible jobs")

void createAnsibleJobs(def dsl, Map pipelineConfig) {
    String logPrefix = "createAnsibleJobs():"
    def factory = new PipelineJobFactory(dsl)

    String baseFolder = pipelineConfig.baseFolder
    String repoFolder = pipelineConfig.repoFolder
    List ansibleJobList = pipelineConfig.ansibleJobList
    Map runEnvMap = pipelineConfig.runEnvMap

    def envVars = Jenkins.instance.
                       getGlobalNodeProperties().
                       get(hudson.slaves.EnvironmentVariablesNodeProperty).
                       getEnvVars()

    if (!envVars?.JENKINS_ENV) {
        log.info("${scriptName}: JENKINS_ENV not defined - skipping project definition")
        return
    }

    String jenkinsEnv = envVars.JENKINS_ENV

    if (!runEnvMap.containsKey(jenkinsEnv)) {
        log.info("${scriptName}: key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` project definition, skipping ansible-jobs build")
        return
    }

    List runEnvList = runEnvMap[jenkinsEnv]

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
            Map jobConfigs = MapMerge.merge(envConfigs, jobConfigsRaw)
            String ansibleTag = jobConfigs.ansible_tag

            jobConfigs.jobName = ansibleTag
            jobConfigs.jobType = 'standard'
            jobConfigs.scmSourceType = 'cpsScm'
            jobConfigs.jobFolder = "${baseFolder}/${runEnvironment}/${repoFolder}"

            dsl.folder(jobConfigs.jobFolder) {
                description "This folder contains jobs to run ansible SITE play tags for ${runEnvironment}/${ansibleTag}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
            }

            factory.createStandardPipelineJob(jobConfigs.jobFolder, jobConfigs)
        }

        // ListView definitions
        dsl.listView("${baseFolder}/ansible-jobs") {
            jobs { regex(".*") }
            columns { status(); weather(); name(); lastSuccess(); lastFailure(); lastDuration(); buildButton() }
        }
        dsl.listView("${baseFolder}/ansible-jobs-unstable") {
            jobs { regex(".*") }
            jobFilters { status { status(Status.UNSTABLE) } }
            columns { status(); weather(); name(); lastSuccess(); lastFailure(); lastDuration(); buildButton() }
        }
    }
}
