package com.dettonville.jobdsl

import jenkins.model.Jenkins
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.JenkinsLogger

// You can keep PipelineJobFactory if it has other reusable logic,
// or integrate its relevant parts directly into this class.
// For simplicity, let's assume PipelineJobFactory is still valid and imported.

class RepoTestJobCreator {

    // Make the method static so it can be called directly on the class
    static void createRepoTestJobs(def dsl, Map repoJobConfigs) {
        // Re-use your existing logic from vars/createRepoTestJobs.groovy here
        // Note: 'dsl' needs to be passed in as it's the Job DSL context.

        def factory = new PipelineJobFactory(dsl)
        def log = new JenkinsLogger(dsl, prefix: 'com.dettonville.jobdsl.RepoTestJobCreator')

        log.debug("repoJobConfigs=${JsonUtils.printToJsonString(repoJobConfigs)}")

        String jobFolder = repoJobConfigs.jobFolder
        String repoName = repoJobConfigs.repoName
        String baseFolder = "${jobFolder.substring(0, jobFolder.lastIndexOf("/"))}"
        Map runEnvMap = repoJobConfigs.runEnvMap

        def envVars = Jenkins.instance.
                           getGlobalNodeProperties().
                           get(hudson.slaves.EnvironmentVariablesNodeProperty).
                           getEnvVars()

        if (!envVars?.JENKINS_ENV) {
            log.info("JENKINS_ENV not defined - skipping test jobs definition")
            return
        }

        String jenkinsEnv = envVars.JENKINS_ENV

        if (!runEnvMap.containsKey(jenkinsEnv)) {
            log.info("key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` test jobs definition, skipping test-jobs build")
            return
        }

        Map envConfigsRaw = runEnvMap[jenkinsEnv]
        // Map envConfigs = MapMerge.merge(repoJobConfigs.findAll { !["runEnvMap","jobList"].contains(it.key) }, envConfigsRaw) // Re-enable if MapMerge is setup

        log.info("creating baseFolder=[${baseFolder}]")
        dsl.folder(baseFolder) {
            description "This folder contains jobs to run REPO TEST PLAYS"
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        factory.createJobs(repoJobConfigs)

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

        dsl.listView("${jobFolder}/repo-test-jobs-unstable") {
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
        log.info("Finished creating repo test jobs")
    }
}
