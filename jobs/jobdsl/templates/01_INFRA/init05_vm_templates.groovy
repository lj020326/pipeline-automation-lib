#!/usr/bin/env groovy

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.JenkinsLogger

@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

import groovy.transform.Field
@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)

String pipelineConfigYaml = "config.vm-template-jobs.yml"
String configFilePath = "${new File(__FILE__).parent}"
log.debug("${scriptName}: configFilePath:${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)

createVmTemplateJobs(this, seedJobConfigs)

log.info("${scriptName}: Finished creating vm-template jobs")

//******************************************************
//  Function definitions from this point forward
//
void createVmTemplateJobs(def dsl, Map pipelineConfig) {
    String logPrefix = "createVmTemplateJobs():"

    String baseFolder = pipelineConfig.baseFolder
    String repoUrl = pipelineConfig.repoUrl
    String mirrorRepoDir = pipelineConfig.mirrorRepoDir
    String gitCredentialsId = pipelineConfig.gitCredentialsId
    String jobScript = pipelineConfig.jobScript
    List vmTemplateList = pipelineConfig.vmTemplateList

    Map runEnvMap = pipelineConfig.runEnvMap
    log.info("${logPrefix} runEnvMap=${JsonUtils.printToJsonString(runEnvMap)}")

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
        log.info("${scriptName}: key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` project definition, skipping vm-template-jobs build")
        return
    }

    List runEnvList = runEnvMap[jenkinsEnv]

    dsl.folder(baseFolder) {
        description "This folder contains jobs to BUILD VMWARE/VSPHERE TEMPLATES"
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

    runEnvList.each { Map envConfigsRaw ->
        Map envConfigs = MapMerge.merge(pipelineConfig.findAll { !["runEnvMap","vmTemplateList"].contains(it.key) }, envConfigsRaw)
        String runEnvironment = envConfigs.environment

        dsl.folder("${baseFolder}/${runEnvironment}") {
            description "This folder contains jobs to build VM templates for ${runEnvironment}"
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        vmTemplateList.each { Map templateConfigsRaw ->

            Map templateConfigs = MapMerge.merge(envConfigs.findAll { !["jobList"].contains(it.key) }, templateConfigsRaw)
            templateConfigs.buildType = templateConfigs.get("buildType", "medium")
            log.info("${logPrefix} templateConfigs=${JsonUtils.printToJsonString(templateConfigs)}")

            List templateDistFolderList = templateConfigs.buildDistribution.split('/')[0..-1]
            if (templateDistFolderList.size() > 0) {
                templateDistFolderList.eachWithIndex { String folder, idx ->
                    String currentFolder = templateDistFolderList[0..idx].join('/')
                    dsl.folder("${baseFolder}/${runEnvironment}/${currentFolder}") {
                        description "VM templates for ${runEnvironment}/${currentFolder}"
                        properties {
                            authorizationMatrix {
                                inheritanceStrategy {
                                    inheriting()
                                }
                            }
                        }
                    }
                }
            }

            dsl.folder("${baseFolder}/${runEnvironment}/${templateConfigs.buildDistribution}") {
                description "VM templates for ${runEnvironment}/${templateConfigs.buildDistribution}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
            }
            dsl.folder("${baseFolder}/${runEnvironment}/${templateConfigs.buildDistribution}/${templateConfigs.buildRelease}") {
                description "This folder contains jobs to build VM templates for release ${templateConfigs.buildDistribution}/${templateConfigs.buildRelease}"
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
            def jobObject = dsl.pipelineJob("${baseFolder}/${runEnvironment}/${templateConfigs.buildDistribution}/${templateConfigs.buildRelease}/${templateConfigs.buildType}") {
                description "Build VM template jobs for ${templateConfigs.buildDistribution}/${templateConfigs.buildRelease}/${templateConfigs.buildType}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
                keepDependencies(false)
                parameters {
                    booleanParam("ReplaceExistingTemplate", templateConfigs.replaceExistingTemplate, 'Replace Existing Template?')
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
                                extensions {
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
            if (templateConfigs?.cronSpecification) {
                log.info("${scriptName}: adding to job cronSpecification=[${templateConfigs?.cronSpecification}]")
                jobObject.properties {
                    pipelineTriggers {
                        triggers {
                            cron {
                                spec(templateConfigs.cronSpecification)
                            }
                        }
                    }
                }
            }
        }

        if (envConfigs?.jobList) {
            envJobList = envConfigs.jobList
            envJobList.each { Map jobConfigsRaw ->
                Map jobConfigs = MapMerge.merge(envConfigs.findAll { !["jobList"].contains(it.key) }, jobConfigsRaw)
                def envJobsObject = dsl.pipelineJob("${baseFolder}/${runEnvironment}/${jobConfigs.jobName}") {
                    description(jobConfigs.description)
                    keepDependencies(false)
                    parameters {
                        booleanParam("ReplaceExistingTemplate", jobConfigs.replaceExistingTemplate, 'Replace Existing Template?')
                    }
                    definition {
                        logRotator {
                           daysToKeep(-1)
                           numToKeep(10)
                           artifactNumToKeep(-1)
                           artifactDaysToKeep(-1)
                        }
                        cpsScm {
                            scm {
                                git {
                                    remote {
                                        url(repoUrl)
                                        credentials(gitCredentialsId)
                                    }
                                    branch("*/main")
                                }
                            }
                            scriptPath(jobConfigs.jobScript)
                        }
                    }
                    disabled(false)
                }

                if (jobConfigs?.cronSpecification) {
                    log.info("${scriptName}: adding to job cronSpecification=[${jobConfigs?.cronSpecification}]")
                    envJobsObject.properties {
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
        }

        dsl.listView("${baseFolder}/${runEnvironment}/vm-template-jobs") {
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

        dsl.listView("${baseFolder}/${runEnvironment}/vm-template-jobs-unstable") {
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
