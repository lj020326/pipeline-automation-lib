#!/usr/bin/env groovy

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.JenkinsLogger
import com.dettonville.jobdsl.RepoTestJobCreator

@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

import groovy.transform.Field
@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)
//@Field JenkinsLogger log = new JenkinsLogger(this, logLevel: 'DEBUG', prefix: scriptName)

String pipelineConfigYaml = "config.repo-test-jobs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
log.info("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
Map basePipelineConfig = seedJobConfigs.pipelineConfig

log.info("${scriptName}: basePipelineConfig=${basePipelineConfig}")

List yamlProjectConfigList = basePipelineConfig.yamlProjectConfigList
log.info("${scriptName}: yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    log.info("${scriptName}: Creating Repo Test Jobs for ${projectConfigYamlFile}")

    Map repoTestJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    Map pipelineConfig = repoTestJobConfigs.pipelineConfig

    // Call the static method directly from the class
//     RepoTestJobCreator.createRepoTestJobsStatic(this, pipelineConfig)
    createRepoTestJobs(this, pipelineConfig)

}
log.info("${scriptName}: Finished creating repo test jobs")

//******************************************************
//  Function definitions from this point forward
//
void createRepoTestJobs(def dsl, Map repoJobConfigs) {
    String logPrefix = "createRepoTestJobs():"

//     log.info("${logPrefix} repoJobConfigs=${JsonUtils.printToJsonString(repoJobConfigs)}")

    String jobFolder = repoJobConfigs.jobFolder
    String repoName = repoJobConfigs.repoName
//     String repoFolder = "${repoJobConfigs.jobFolder}/${repoName}"
    String baseFolder = "${jobFolder.substring(0, jobFolder.lastIndexOf("/"))}"
    Map runEnvMap = repoJobConfigs.runEnvMap

    // ref: https://stackoverflow.com/questions/40215394/how-to-get-environment-variable-in-jenkins-groovy-script-console
    def envVars = Jenkins.instance.
                       getGlobalNodeProperties().
                       get(hudson.slaves.EnvironmentVariablesNodeProperty).
                       getEnvVars()

    if (!envVars?.JENKINS_ENV) {
        log.info("${logPrefix} JENKINS_ENV not defined - skipping test jobs definition")
        return
    }

    String jenkinsEnv = envVars.JENKINS_ENV

    if (!runEnvMap.containsKey(jenkinsEnv)) {
        log.info("${logPrefix} key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` test jobs definition, skipping test-jobs build")
        return
    }

    Map envConfigsRaw = runEnvMap[jenkinsEnv]
    Map envConfigs = MapMerge.merge(repoJobConfigs.findAll { !["runEnvMap","jobList"].contains(it.key) }, envConfigsRaw)
//     String runEnvironment = envConfigs.environment

    // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
    log.info("${logPrefix} creating baseFolder=[${baseFolder}]")
    dsl.folder(baseFolder) {
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

//     baseConfigs = repoJobConfigs.findAll { !['jobList'].contains(it.key) }
//     if (repoJobConfigs?.jobList) {
//         createJobs(dsl, repoJobConfigs)
//     }
    createJobs(dsl, repoJobConfigs)

    // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
    // ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
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
    log.info("${scriptName}: Finished creating repo test jobs")
}

void createJobs(dsl, Map jobConfigs) {

    String jobFolder = jobConfigs.jobFolder

    String logPrefix = "createJobs(${jobFolder}):"

//     log.info("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String folderDescription = jobConfigs.get('folderDescription', "This folder contains jobs to run REPO TEST PLAYS")
    // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
    log.info("${logPrefix} creating jobFolder=[${jobFolder}]")
    dsl.folder(jobFolder) {
        description "${folderDescription}"
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

    if (jobConfigs?.jobGroupName) {
        String jobGroupName = jobConfigs.jobGroupName
        String jobGroupFolder = "${jobFolder}/${jobGroupName}"
        log.info("${logPrefix} jobConfigs.jobGroupName=${jobConfigs.jobGroupName}")
        // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
        log.info("${logPrefix} creating jobGroupFolder ${jobGroupFolder}")
        dsl.folder(jobGroupFolder) {
            description "Jobs to run REPO TEST PLAYS FOR ${jobGroupName}"
        }

        Map groupJobConfig = jobConfigs.findAll { !["jobGroupName"].contains(it.key) }
        groupJobConfig.jobFolder = jobGroupFolder
        createJobs(dsl, groupJobConfig)
    } else if (jobConfigs?.jobFolders) {
        List jobFolderList = jobConfigs.jobFolders
        log.info("${logPrefix} jobFolderList=${jobFolderList}")
        jobFolderList.each { Map jobFolderConfig ->
            log.info("${logPrefix} jobFolderConfig=${JsonUtils.printToJsonString(jobFolderConfig)}")
            jobChildFolder = "${jobConfigs.jobFolder}/${jobFolderConfig.name}"
            log.info("${logPrefix} creating jobChildFolder=[${jobChildFolder}]")
            folder(jobChildFolder) {
                description "${jobFolderConfig.description}"
            }

            Map folderJobConfig = jobConfigs.findAll { !["jobFolders"].contains(it.key) }
            folderJobConfig.jobFolder = jobChildFolder
            createJobs(dsl, folderJobConfig)
        }
    } else if (jobConfigs?.jobList) {

        List jobList = jobConfigs.jobList

        jobList.each { Map jobConfigRaw ->

            Map jobConfig = MapMerge.merge(jobConfigs.findAll {
                !["jobList"].contains(it.key)
                }, jobConfigRaw)

            log.info("${logPrefix} creating child job with jobConfig=${JsonUtils.printToJsonString(jobConfig)}")
            createJobs(dsl, jobConfig)
        }
    } else {
        log.info("${logPrefix} lesf node with jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
        if (jobConfigs?.jobName && jobConfigs?.jobScript) {
            log.info("${logPrefix} Create job for jobConfigs.jobName=${jobConfigs.jobName}")
            createJob(dsl, jobConfigs)
        }
    }

}

// Consolidated createMultibranchPipelineJob
def createMultibranchPipelineJob(def dsl, String jobFolder, Map jobConfigs) {
    String logPrefix = "createMultibranchPipelineJob(${jobConfigs.jobName}):"
    log.info("${logPrefix} jobConfigs=${jobConfigs}") // Use jobConfigs directly, assuming JsonUtils.printToJsonString is handled or not strictly necessary here

    String jobRepoUrl = jobConfigs.repoUrl
    String gitCredentialsId = jobConfigs.gitCredentialsId
    String mirrorRepoDir = jobConfigs.mirrorRepoDir

    String jobName = jobConfigs.jobName
    String jobScript = jobConfigs.jobScript
    String jobFolderPath = "${jobFolder}/${jobName}"

    String branchSourceType = jobConfigs.get('branchSourceType', 'gitSCM')
    boolean useSuppressionStrategy = jobConfigs.get('useSuppressionStrategy', false).toBoolean()
    boolean buildAllBranchesBool = jobConfigs.get('buildAllBranches', false).toBoolean()
    List branchesToBuild = jobConfigs.get('branchesToBuild', []) as List

    String giteaServerUrl = jobConfigs.get("giteaServerUrl", '')
    String giteaCredentialsId = jobConfigs.get('giteaCredentialsId', '')
    String giteaOwner = jobConfigs.get('giteaOwner', '')
    String giteaRepoName = jobConfigs.get('giteaRepoName', '')

    String jobId = "${jobName}"
    if (jobConfigs?.jobGroupName) {
        jobId = "${jobConfigs.jobGroupName}-${jobName}"
    }

    // ref: https://github.com/jenkinsci/job-dsl-plugin/wiki/Job-DSL-Commands#job
    // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#path/multibranchPipelineJob
    def jobObject = dsl.multibranchPipelineJob("${jobFolderPath}") {
        description "Create test job ${jobName}"
        displayName(jobName)
        branchSources {
            branchSource {
                source {
                    if (branchSourceType == 'gitea') {
                        giteaSCMSource {
                            id(jobId)
                            credentialsId(giteaCredentialsId)
                            serverUrl(giteaServerUrl)
                            repoOwner(giteaOwner)
                            repository(giteaRepoName)
                            traits {
                                giteaBranchDiscovery {
                                    strategyId(3)
                                }
                                pruneStaleBranch()
                                pruneStaleTag()
                                cloneOption {
                                    extension {
                                        shallow(true)
                                        depth(2)
                                        noTags(true)
                                        reference(mirrorRepoDir)
                                        honorRefspec(false)
                                        timeout(1)
                                    }
                                }
                            }
                        }
                    } else { // Default to git
                        git {
                            // IMPORTANT: use a constant and unique identifier
                            id(jobId)
                            remote(jobRepoUrl)
                            credentialsId(gitCredentialsId)
                            traits {
                                gitBranchDiscovery()
                                pruneStaleBranch()
                                pruneStaleTag()
                                cloneOption {
                                    extension {
                                        shallow(true)
                                        depth(2)
                                        noTags(true)
                                        reference(mirrorRepoDir)
                                        honorRefspec(false)
                                        timeout(1)
                                    }
                                }
                            }
                        }
                    }
                }
                // END OF CHANGE for gitea traits
                if (buildAllBranchesBool) {
                    buildStrategies {
                        buildAllBranches {
                            strategies {
                                buildRegularBranches()
                            }
                        }
                    }
                } else if (branchesToBuild) {
                    buildStrategies {
                        buildNamedBranches {
                            filters {
                                wildcards {
                                    includes(branchesToBuild.join(' '))
                                    excludes('')
                                    caseSensitive(true)
                                }
                            }
                        }
                    }
                }
//                 'buildStrategies' {
//                     if (buildAllBranches) {
//                         'jenkins.branch.buildstrategies.BuildAllBranchesStrategy'()
//                     } else if (branchesToBuild) {
//                         'jenkins.branch.buildstrategies.NamedBranchesFilterStrategy' {
//                             filters {
//                                 'jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait' {
//                                     includes(branchesToBuild.join(' '))
//                                     excludes('')
//                                     caseSensitive(true)
//                                 }
//                             }
//                         }
//                     } else {
//                         'jenkins.branch.DefaultBranchPropertyStrategy' {
//                             'props' {
//                                 'jenkins.branch.NoTriggerBranchProperty'()
//                             }
//                         }
//                     }
//                 }
            }
        }
        // ref: https://stackoverflow.com/questions/48284589/jenkins-jobdsl-multibranchpipelinejob-change-script-path
        factory {
            workflowBranchProjectFactory {
                scriptPath(jobScript)
            }
        }
        orphanedItemStrategy {
            discardOldItems {
                numToKeep(10)
//                 daysToKeep(-1)
            }
            defaultOrphanedItemStrategy {
//                 abortBuilds(true)
                pruneDeadBranches(true)
                numToKeepStr("10")
                daysToKeepStr("-1")
            }
        }
        properties {
            if (useSuppressionStrategy) {
                suppressFolderAutomaticTriggering {
                    strategy('NONE')
                    branches('^$')
                }
            } else if (branchesToBuild) {
                suppressFolderAutomaticTriggering {
                    strategy('INDEXING')
                    branches(branchesToBuild.join(' '))
                }
            }
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

    if (useSuppressionStrategy && branchesToBuild) {
        log.info("${logPrefix} adding trigger indexing suppression strategy for branches ${branchesToBuild}")
        jobObject.configure {
            def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
            traits << 'jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait' {
                includes(branchesToBuild.join(' '))
                excludes('')
            }
        }
    }

    return jobObject
}

void createJob(def dsl, Map jobConfigs) {
    String jobFolder = jobConfigs.jobFolder
    String logPrefix = "createJob(${jobConfigs.jobFolder}, ${jobConfigs.jobName}):"

    log.info("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    def jobObject = createMultibranchPipelineJob(dsl, jobFolder, jobConfigs)

//     // ref: https://github.com/jenkinsci/job-dsl-plugin/wiki/Job-DSL-Commands#job
//     // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#path/multibranchPipelineJob
//     def jobObject
//     if (jobConfigs.useSuppressionStrategy) {
//         jobObject = createMultibranchPipelineJobNoAutoBuilds(dsl, jobFolder, jobConfigs)
//     } else {
//         if (jobConfigs?.branchesToBuild && jobConfigs.branchesToBuild) {
//             jobObject = createMultibranchPipelineJobBranchAutoBuilds(dsl, jobFolder, jobConfigs)
//         } else {
//             jobObject = createMultibranchPipelineJobAutoBuilds(dsl, jobFolder, jobConfigs)
//         }
//     }

    if (jobConfigs?.skipInitialBuildOnFirstBranchIndexing && jobConfigs.skipInitialBuildOnFirstBranchIndexing.toBoolean()) {
        log.info("${logPrefix} adding skipInitialBuildOnFirstBranchIndexing() strategy")
        // ref: https://stackoverflow.com/questions/27929548/how-to-use-jenkins-jobdsl-to-set-check-out-to-specific-local-branch-in-git-plu
        jobObject.configure {
            it / sources / data / 'jenkins.branch.BranchSource' / buildStrategies {
                skipInitialBuildOnFirstBranchIndexing
            }
        }
    }

    // ref: https://stackoverflow.com/a/69783984
    // ref: https://stackoverflow.com/questions/62760438/jenkins-job-dsl-trigger-is-deprecated
    if (jobConfigs?.periodicFolderTriggerInterval) {
        log.info("${logPrefix} configuring job with periodicFolderTriggerInterval=[${jobConfigs?.periodicFolderTriggerInterval}]")
        jobObject.triggers {
            periodicFolderTrigger {
                interval(jobConfigs.periodicFolderTriggerInterval)
            }
        }
    }

    if (jobConfigs?.branchesToDiscover) {
        // ref: https://stackoverflow.com/questions/68773981/how-to-let-jenkins-pipeline-build-only-for-given-branches
        // ref: https://stackoverflow.com/questions/62858219/multibranchpipelinejob-job-dsl-how-to-enable-filter-by-name-with-wildcards
        // ref: https://ftclausen.github.io/infra/jenkins/jenkins-jobdsl-git-traits/
        // ref: https://groups.google.com/g/jenkinsci-users/c/GHsvyCI7Xoo
        // ref: https://stackoverflow.com/questions/69813779/what-is-jcasc-job-dsl-option-for-build-when-a-change-is-pushed-to-bitbucket
        log.info("${logPrefix} adding build strategy for branches: [${jobConfigs.branchesToDiscover.join(' ')}]")
        jobObject.configure {
            def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
            traits << 'jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait' {
                includes(jobConfigs.branchesToDiscover.join(' '))
                excludes('')
            }
        }
//    if (jobConfigs?.branchesToBuild) {
//         jobObject.configure {
//             it / sources / data / 'jenkins.branch.BranchSource' / buildStrategies {
//                 buildNamedBranches {
//                     filters {
//                         wildcards {
//                             includes(jobConfigs.branchesToBuild.join(' '))
//                         }
//                     }
//                 }
//             }
//         }
//         else {
//             log.info("${logPrefix} adding build strategy for ALL branches")
//             jobObject.configure {
//                 it / sources / data / 'jenkins.branch.BranchSource' / buildStrategies {
//                     buildAllBranches {
//                         strategies {
//                             buildRegularBranches()
//                         }
//                     }
//                 }
//             }
//         }
    }

//     log.info("${logPrefix} Initialize test jobs")
//     if (jobConfigs?.initializeJobMode && jobConfigs.initializeJobMode.toBoolean()) {
//         log.info("${logPrefix} Running test job initialization for jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
//         initJobs(dsl, jobFolder, jobConfigs)
//     }

}

void initJobs(def dsl, String jobFolder, Map jobConfigs) {
    String logPrefix = "initJobs():"

    log.info("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String jobName = jobConfigs.jobName
    String jobFolderPath = "${jobFolder}/${jobName}"

//     def allJobs = Jenkins.instance.getItemByFullName(jobFolderPath)
//     def allJobs = Jenkins.instance.getAllItems()

    // ref: https://stackoverflow.com/questions/46303259/how-to-get-jenkins-list-of-jobs-in-folder
    def allJobs = hudson.model.Hudson.getInstance().getAllItems(hudson.model.Job.class).findAll {
        it.getFullName().contains(jobFolderPath)
    }

    log.info("${logPrefix} allJobs=${allJobs}")
    for(job in allJobs) {

        log.info("${logPrefix} Initializing job ${job}")
//         // ref: https://stackoverflow.com/questions/41693505/jenkins-groovy-scheduled-job-get-link
//         // ref: https://stackoverflow.com/questions/49659175/jenkins-jobdsl-queue-with-parameters

        List params = []
        if (jobConfigs?.initializeJobMode && jobConfigs.initializeJobMode.toBoolean()) {
            params.push(new hudson.model.BooleanParameterValue('InitializeJobMode', true))
        }
        if (jobConfigs?.enableGitTestResults && jobConfigs.enableGitTestResults.toBoolean()) {
            params.push(new hudson.model.BooleanParameterValue('EnableGitTestResults', true))
        }
        def paramsAction = new hudson.model.ParametersAction(params)
        def currentBuild = hudson.model.Executor.currentExecutor().currentExecutable
        def cause = new hudson.model.Cause.UpstreamCause(currentBuild)
        def causeAction = new hudson.model.CauseAction(cause)
        def scheduledJob = hudson.model.Hudson.instance.queue.schedule(job, 0, causeAction, paramsAction)
    }

}
