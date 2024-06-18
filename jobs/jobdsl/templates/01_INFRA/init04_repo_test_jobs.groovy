#!/usr/bin/env groovy

// ref: https://stackoverflow.com/questions/44004636/jenkins-multibranch-pipeline-scan-without-execution
import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge

// separate configuration from job dsl "seedjob" code
// ref: https://stackoverflow.com/questions/47443106/jenkins-dsl-parse-yaml-for-complex-processing#54665138
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

String pipelineConfigYaml = "config.repo-test-jobs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
println("configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
// println("seedJobConfigs=${seedJobConfigs}")

Map basePipelineConfig = seedJobConfigs.pipelineConfig
println("basePipelineConfig=${basePipelineConfig}")

String baseFolder = basePipelineConfig.baseFolder
List yamlProjectConfigList = basePipelineConfig.yamlProjectConfigList

println("yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    println("Creating Repo Test Jobs for ${projectConfigYamlFile}")

    Map repoTestJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    // println("seedJobConfigs=${repoTestJobConfigs}")

    Map pipelineConfig = repoTestJobConfigs.pipelineConfig
//     println("pipelineConfig=${JsonUtils.printToJsonString(pipelineConfig)}")

    createRepoTestJobs(this, pipelineConfig)

}
println("Finished creating repo test jobs")

//******************************************************
//  Function definitions from this point forward
//
void createRepoTestJobs(def dsl, Map repoJobConfigs) {
    String logPrefix = "createRepoTestJobs():"

//     println("${logPrefix} repoJobConfigs=${JsonUtils.printToJsonString(repoJobConfigs)}")

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
        println("${logPrefix} JENKINS_ENV not defined - skipping test jobs definition")
        return
    }

    String jenkinsEnv = envVars.JENKINS_ENV

    if (!runEnvMap.containsKey(jenkinsEnv)) {
        println("${logPrefix} key for JENKINS_ENV=${jenkinsEnv} not found in `runEnvMap` test jobs definition, skipping test-jobs build")
        return
    }

    Map envConfigsRaw = runEnvMap[jenkinsEnv]
    Map envConfigs = MapMerge.merge(repoJobConfigs.findAll { !["runEnvMap","jobList"].contains(it.key) }, envConfigsRaw)
//     String runEnvironment = envConfigs.environment

    // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
    println("${logPrefix} creating baseFolder=[${baseFolder}]")
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

//     baseConfigs = repoJobConfigs.findAll { !['jobList'].contains(it.key) }
//     if (repoJobConfigs?.jobList) {
//         createTestJobs(dsl, repoJobConfigs)
//     }
    createTestJobs(dsl, repoJobConfigs)

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
    println("Finished creating repo test jobs")
}

void createTestJobs(dsl, Map testJobConfigs) {

    String jobFolder = testJobConfigs.jobFolder

    String logPrefix = "createTestJobs(${jobFolder}):"

//     println("${logPrefix} testJobConfigs=${JsonUtils.printToJsonString(testJobConfigs)}")

    String folderDescription = testJobConfigs.get('folderDescription', "This folder contains jobs to run REPO TEST PLAYS")
    // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
    println("${logPrefix} creating jobFolder=[${jobFolder}]")
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

    if (testJobConfigs?.jobGroupName) {
        String jobGroupName = testJobConfigs.jobGroupName
        String jobGroupFolder = "${jobFolder}/${jobGroupName}"
        println("${logPrefix} testJobConfigs.jobGroupName=${testJobConfigs.jobGroupName}")
        // ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
        println("${logPrefix} creating jobGroupFolder ${jobGroupFolder}")
        dsl.folder(jobGroupFolder) {
            description "Jobs to run REPO TEST PLAYS FOR ${jobGroupName}"
        }

        Map groupJobConfig = testJobConfigs.findAll { !["jobGroupName"].contains(it.key) }
        groupJobConfig.jobFolder = jobGroupFolder
        createTestJobs(dsl, groupJobConfig)
    } else if (testJobConfigs?.jobFolders) {
        List jobFolderList = testJobConfigs.jobFolders
        println("${logPrefix} jobFolderList=${jobFolderList}")
        jobFolderList.each { Map jobFolderConfig ->
            println("${logPrefix} jobFolderConfig=${JsonUtils.printToJsonString(jobFolderConfig)}")
            jobChildFolder = "${testJobConfigs.jobFolder}/${jobFolderConfig.name}"
            println("${logPrefix} creating jobChildFolder=[${jobChildFolder}]")
            folder(jobChildFolder) {
                description "${jobFolderConfig.description}"
            }

            Map folderJobConfig = testJobConfigs.findAll { !["jobFolders"].contains(it.key) }
            folderJobConfig.jobFolder = jobChildFolder
            createTestJobs(dsl, folderJobConfig)
        }
    } else if (testJobConfigs?.jobList) {

        List jobList = testJobConfigs.jobList

        jobList.each { Map testJobConfigRaw ->

            Map testJobConfig = MapMerge.merge(testJobConfigs.findAll {
                !["jobList"].contains(it.key)
                }, testJobConfigRaw)

            println("${logPrefix} creating child job with testJobConfig=${JsonUtils.printToJsonString(testJobConfig)}")
            createTestJobs(dsl, testJobConfig)
        }
    } else {
        println("${logPrefix} lesf node with testJobConfigs=${JsonUtils.printToJsonString(testJobConfigs)}")
        if (testJobConfigs?.jobName && testJobConfigs?.jobScript) {
            println("${logPrefix} Create test job for testJobConfigs.jobName=${testJobConfigs.jobName}")
            createTestJob(dsl, testJobConfigs)
        }
    }

}

def createMultibranchPipelineJobNoAutoBuilds(def dsl, String jobFolder, Map jobConfigs) {
    String logPrefix = "createMultibranchPipelineJobNoAutoBuilds(${jobConfigs.jobName}):"

    println("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String testRepoUrl = jobConfigs.repoUrl
    String gitCredentialsId = jobConfigs.gitCredentialsId
    String mirrorRepoDir = jobConfigs.mirrorRepoDir
//     String periodicFolderTriggerIntervalDefault = "30m"
//     String periodicFolderTriggerInterval = jobConfigs.get('periodicFolderTriggerInterval', periodicFolderTriggerIntervalDefault)

    String jobName = jobConfigs.jobName
    String jobScript = jobConfigs.jobScript
    String jobFolderPath = "${jobFolder}/${jobName}"

    String testJobId = "test-${jobName}"
    if (jobConfigs?.jobGroupName) {
        testJobId = "test-${jobConfigs.jobGroupName}-${jobName}"
    }

    // ref: https://github.com/jenkinsci/job-dsl-plugin/wiki/Job-DSL-Commands#job
    // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#path/multibranchPipelineJob
    def jobObject = dsl.multibranchPipelineJob("${jobFolderPath}") {
        description "Create test job ${jobName}"
        branchSources {
            branchSource {
                source {
                    git {
                        // IMPORTANT: use a constant and unique identifier
                        id(testJobId)
                        remote(testRepoUrl)
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
                strategy {
                    defaultBranchPropertyStrategy {
                        props {
                            noTriggerBranchProperty()
                        }
                    }
                }
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
            suppressFolderAutomaticTriggering {
                strategy('NONE')
                branches('^$')
            }
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

    // ref: https://stackoverflow.com/questions/62760438/jenkins-job-dsl-trigger-is-deprecated
    if (jobConfigs?.useSuppressionStrategy && jobConfigs.useSuppressionStrategy.toBoolean()) {
        println("${logPrefix} adding job suppression strategy")

        // ref: https://stackoverflow.com/questions/77314303/dsl-script-suppressfolderautomatictriggering-property-isnt-working
        // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#path/multibranchPipelineJob-properties-suppressFolderAutomaticTriggering-strategy
        // Allows you to control which branches should be built automatically
        // and which could be only scheduled manually.
        // Two configuration options are available
        // - (1) allowed branches regular expression and
        // - (2) suppression strategy.
        //
        // The used algorithm works as follows:
        //
        //     all branches which names don't match the regular expression are suppressed
        //     when a branch name matches the regular expression, the suppression strategy is checked
        //     to determine whether the trigger should be suppressed or not
        //
        if (jobConfigs?.branchesToBuild) {
            println("${logPrefix} adding trigger indexing suppression strategy for branches ${jobConfigs.branchesToBuild}")
            jobObject.properties {
                suppressFolderAutomaticTriggering {
                    strategy('EVENTS')
                    branches(jobConfigs.branchesToBuild.join(' '))
                }
            }
            jobObject.configure {
                def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
                traits << 'jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait' {
                    includes(jobConfigs.branchesToBuild.join(' '))
                    excludes('')
                }
            }
        }
//         else {
//             // ref: https://stackoverflow.com/questions/47291748/adding-suppress-automatic-scm-triggering-except-for-named-branches-in-jenkins-jo
//             // ref: https://stackoverflow.com/questions/77314303/dsl-script-suppressfolderautomatictriggering-property-isnt-working
//             println("${logPrefix} adding trigger suppression strategy - no branches automatically build/trigger")
//             jobObject.properties {
//                 suppressFolderAutomaticTriggering {
//                     branches('^$')
//                     strategy('NONE')
//                 }
//             }
//             jobObject.configure {
//                 it / sources / data / 'jenkins.branch.BranchSource' / strategy {
//                     allBranchesSame {
//                         props {
//                             suppressAutomaticTriggering {
//                                 strategy('NONE')
//                                 triggeredBranchesRegex('^$')
//                             }
//                         }
//                     }
//                 }
//             }
//         }
    }
    return jobObject
}

def createMultibranchPipelineJobAutoBuilds(def dsl, String jobFolder, Map jobConfigs) {
    String logPrefix = "createMultibranchPipelineJobAutoBuilds(${jobConfigs.jobName}):"

    println("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String testRepoUrl = jobConfigs.repoUrl
    String gitCredentialsId = jobConfigs.gitCredentialsId
    String mirrorRepoDir = jobConfigs.mirrorRepoDir
//     String periodicFolderTriggerIntervalDefault = "30m"
//     String periodicFolderTriggerInterval = jobConfigs.get('periodicFolderTriggerInterval', periodicFolderTriggerIntervalDefault)

    String jobName = jobConfigs.jobName
    String jobScript = jobConfigs.jobScript
    String jobFolderPath = "${jobFolder}/${jobName}"

    String testJobId = "test-${jobName}"
    if (jobConfigs?.jobGroupName) {
        testJobId = "test-${jobConfigs.jobGroupName}-${jobName}"
    }

    def jobObject = dsl.multibranchPipelineJob("${jobFolderPath}") {
        description "Create test job ${jobName}"
        branchSources {
            branchSource {
                source {
                    git {
                        // IMPORTANT: use a constant and unique identifier
                        id(testJobId)
                        remote(testRepoUrl)
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
                // NOTE: REQUIRES the `basic-branch-build-strategies` plugin for the buildStrategies part to work
                // ref: https://stackoverflow.com/questions/48284589/jenkins-jobdsl-multibranchpipelinejob-change-script-path
                buildStrategies {
                    buildAllBranches {
                        strategies {
                            buildRegularBranches()
                        }
                    }
                }
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
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }
    return jobObject
}


def createMultibranchPipelineJobBranchAutoBuilds(def dsl, String jobFolder, Map jobConfigs) {
    String logPrefix = "createMultibranchPipelineJobBranchAutoBuilds(${jobConfigs.jobName}):"

    println("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String testRepoUrl = jobConfigs.repoUrl
    String gitCredentialsId = jobConfigs.gitCredentialsId
    String mirrorRepoDir = jobConfigs.mirrorRepoDir
//     String periodicFolderTriggerIntervalDefault = "30m"
//     String periodicFolderTriggerInterval = jobConfigs.get('periodicFolderTriggerInterval', periodicFolderTriggerIntervalDefault)

    String jobName = jobConfigs.jobName
    String jobScript = jobConfigs.jobScript
    String jobFolderPath = "${jobFolder}/${jobName}"

    String testJobId = "test-${jobName}"
    if (jobConfigs?.jobGroupName) {
        testJobId = "test-${jobConfigs.jobGroupName}-${jobName}"
    }

    def jobObject = dsl.multibranchPipelineJob("${jobFolderPath}") {
        description "Create test job ${jobName}"
        branchSources {
            branchSource {
                source {
                    git {
                        // IMPORTANT: use a constant and unique identifier
                        id(testJobId)
                        remote(testRepoUrl)
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
                // NOTE: REQUIRES the `basic-branch-build-strategies` plugin for the buildStrategies part to work
                // ref: https://stackoverflow.com/questions/48284589/jenkins-jobdsl-multibranchpipelinejob-change-script-path
                buildStrategies {
                    buildNamedBranches {
                        filters {
                            wildcards {
                                includes(jobConfigs.branchesToBuild.join(' '))
                                excludes('')
                                caseSensitive(true)
                            }
                        }
                    }
                }
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
            suppressFolderAutomaticTriggering {
                strategy('INDEXING')
                branches(jobConfigs.branchesToBuild.join(' '))
            }
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }
    return jobObject
}

void createTestJob(def dsl, Map jobConfigs) {
    String jobFolder = jobConfigs.jobFolder
    String logPrefix = "createTestJob(${jobConfigs.jobFolder}, ${jobConfigs.jobName}):"

    println("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    // ref: https://github.com/jenkinsci/job-dsl-plugin/wiki/Job-DSL-Commands#job
    // ref: https://jenkins.admin.dettonville.int/plugin/job-dsl/api-viewer/index.html#path/multibranchPipelineJob
    def jobObject
    if (jobConfigs.useSuppressionStrategy) {
        jobObject = createMultibranchPipelineJobNoAutoBuilds(dsl, jobFolder, jobConfigs)
    } else {
        if (jobConfigs?.branchesToBuild && jobConfigs.branchesToBuild) {
            jobObject = createMultibranchPipelineJobBranchAutoBuilds(dsl, jobFolder, jobConfigs)
        } else {
            jobObject = createMultibranchPipelineJobAutoBuilds(dsl, jobFolder, jobConfigs)
        }
    }

    if (jobConfigs?.skipInitialBuildOnFirstBranchIndexing && jobConfigs.skipInitialBuildOnFirstBranchIndexing.toBoolean()) {
        println("${logPrefix} adding skipInitialBuildOnFirstBranchIndexing() strategy")
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
        println("${logPrefix} configuring job with periodicFolderTriggerInterval=[${jobConfigs?.periodicFolderTriggerInterval}]")
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
        println("${logPrefix} adding build strategy for branches: [${jobConfigs.branchesToDiscover.join(' ')}]")
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
//             println("${logPrefix} adding build strategy for ALL branches")
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

//     println("${logPrefix} Initialize test jobs")
//     if (jobConfigs?.initializeJobMode && jobConfigs.initializeJobMode.toBoolean()) {
//         println("${logPrefix} Running test job initialization for jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
//         initTestJobs(dsl, jobFolder, jobConfigs)
//     }

}

void initTestJobs(def dsl, String jobFolder, Map jobConfigs) {
    String logPrefix = "initTestJobs():"

    println("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

    String jobName = jobConfigs.jobName
    String jobFolderPath = "${jobFolder}/${jobName}"

//     def allJobs = Jenkins.instance.getItemByFullName(jobFolderPath)
//     def allJobs = Jenkins.instance.getAllItems()

    // ref: https://stackoverflow.com/questions/46303259/how-to-get-jenkins-list-of-jobs-in-folder
    def allJobs = hudson.model.Hudson.getInstance().getAllItems(hudson.model.Job.class).findAll {
        it.getFullName().contains(jobFolderPath)
    }

    println("${logPrefix} allJobs=${allJobs}")
    for(job in allJobs) {

        println("${logPrefix} Initializing job ${job}")
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
