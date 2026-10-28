package com.dettonville.jobdsl

import jenkins.model.Jenkins
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.JenkinsLogger

class RepoJobCreator {

    static void createRepoJobs(def dsl, Map repoJobConfigs) {
        def factory = new PipelineJobFactory(dsl)
        def log = new JenkinsLogger(dsl, prefix: 'com.dettonville.jobdsl.RepoJobCreator')

        log.debug("repoJobConfigs=${JsonUtils.printToJsonString(repoJobConfigs)}")

        String jobFolder = repoJobConfigs.get('jobFolder', repoJobConfigs.get('baseFolder', ''))
        String repoName = repoJobConfigs.get('repoName', '')
        String repoDescription = repoJobConfigs.get('repoDescription', "This folder contains repo jobs")
        String baseFolder = jobFolder && jobFolder.contains("/") ? jobFolder.substring(0, jobFolder.lastIndexOf("/")) : jobFolder

        Map runEnvMap = repoJobConfigs.get('runEnvMap', null)
        Map repoJobEnvConfigs = repoJobConfigs

        if (runEnvMap != null) {
            def envVars = Jenkins.instance.
                               getGlobalNodeProperties().
                               get(hudson.slaves.EnvironmentVariablesNodeProperty).
                               getEnvVars()

            if (!envVars?.JENKINS_ENV) {
                log.info("JENKINS_ENV not defined - skipping jobs definition")
                return
            }

            String jenkinsEnv = envVars.JENKINS_ENV

            if (!runEnvMap.containsKey(jenkinsEnv)) {
                log.info("key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` jobs definition, skipping repo jobs build")
                return
            }

            Map envConfigsRaw = runEnvMap[jenkinsEnv]
            repoJobEnvConfigs = MapMerge.merge(repoJobConfigs.findAll { !["runEnvMap"].contains(it.key) }, envConfigsRaw)
            log.info("envConfigsRaw=[${envConfigsRaw}]")
        } else {
            log.info("runEnvMap not defined - proceeding to create environment-agnostic pipeline jobs")
        }

        if (baseFolder) {
            log.info("creating baseFolder=[${baseFolder}]")
            dsl.folder(baseFolder) {
                description "${repoDescription}"
                properties {
                    authorizationMatrix {
                        inheritanceStrategy {
                            inheriting()
                        }
                    }
                }
            }
        }

        factory.createJobs(repoJobEnvConfigs)

        if (jobFolder) {
            dsl.listView("${jobFolder}") {
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

            dsl.listView("${jobFolder}/unstable") {
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
        log.info("Finished creating repo jobs")
    }
}
