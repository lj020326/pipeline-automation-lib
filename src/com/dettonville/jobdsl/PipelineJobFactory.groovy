package com.dettonville.jobdsl

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.JenkinsLogger

class PipelineJobFactory implements Serializable {

    // You might pass 'script' object if you need access to global variables or specific dsl context,
    // but typically for job-dsl functions, 'dsl' (JobDslContext) is sufficient.
    // If you need `scriptName` or other global variables from your seed job,
    // consider passing them explicitly or initializing them differently in the shared library.
    def dsl
    def log

    PipelineJobFactory(dslContext) {
        this.dsl = dslContext
        this.log = new JenkinsLogger(this.dsl, prefix: this.class.name)
//         this.log = new JenkinsLogger(this.dsl, logLevel: 'DEBUG', prefix: this.class.name)
    }

    // Consolidated createMultibranchPipelineJob
    def createMultibranchPipelineJob(String jobFolder, Map jobConfigs) {
        String logPrefix = "createMultibranchPipelineJob(${jobConfigs.jobName}):"
        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}") // Use jobConfigs directly, assuming JsonUtils.printToJsonString is handled or not strictly necessary here

        String jobRepoUrl = jobConfigs.repoUrl
        String gitCredentialsId = jobConfigs.gitCredentialsId
        String mirrorRepoDir = jobConfigs.mirrorRepoDir

        String jobName = jobConfigs.jobName
        String jobScript = jobConfigs.jobScript
        String jobFolderPath = "${jobFolder}/${jobName}"

        String branchSourceType = jobConfigs.get('branchSourceType', 'git')
        boolean useSuppressionStrategy = jobConfigs.get('useSuppressionStrategy', false).toBoolean()
        boolean buildAllBranchesBool = jobConfigs.get('buildAllBranches', false).toBoolean()
        List branchesToBuild = jobConfigs.get('branchesToBuild', []) as List

        String giteaServerUrl = jobConfigs.get('giteaServerUrl', '')
        String giteaCredentialsId = jobConfigs.get('giteaCredentialsId', '')
        String giteaOwner = jobConfigs.get('giteaOwner', '')
        String giteaRepoName = jobConfigs.get('giteaRepoName', '')

        String jobId = "job-${jobName}"
        if (jobConfigs?.jobGroupName) {
            jobId = "job-${jobConfigs.jobGroupName}-${jobName}"
        }

        log.info("${logPrefix} creating job for jobName=${jobName} with branchSourceType=${branchSourceType}")

        def jobObject = dsl.multibranchPipelineJob("${jobFolderPath}") {
            description "Run ${jobName}"
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
    //                     if (buildAllBranchesBool) {
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

    void createJob(Map jobConfigs) {
        String jobFolder = jobConfigs.jobFolder
        String logPrefix = "createJob(${jobConfigs.jobFolder}, ${jobConfigs.jobName}):"

        log.info("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        def jobObject = createMultibranchPipelineJob(jobFolder, jobConfigs)

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

    // createJobs function
    void createJobs(Map jobConfigs) {
        String jobFolder = jobConfigs.jobFolder
        String logPrefix = "createJobs(${jobFolder}):"
        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        String folderDescription = jobConfigs.get('folderDescription', "")
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
            log.debug("${logPrefix} jobConfigs.jobGroupName=${jobConfigs.jobGroupName}")
            log.debug("${logPrefix} creating jobGroupFolder ${jobGroupFolder}")
            dsl.folder(jobGroupFolder) {
                description "Jobs for ${jobGroupName}"
            }

            Map groupJobConfig = jobConfigs.findAll { !["jobGroupName"].contains(it.key) }
            groupJobConfig.jobFolder = jobGroupFolder
            createJobs(groupJobConfig)
        } else if (jobConfigs?.jobFolders) {
            List jobFolderList = jobConfigs.jobFolders
            log.debug("${logPrefix} jobFolderList=${jobFolderList}")
            jobFolderList.each { Map jobFolderConfig ->
                log.debug("${logPrefix} jobFolderConfig=${JsonUtils.printToJsonString(jobFolderConfig)}")

                String jobChildFolder = "${jobConfigs.jobFolder}/${jobFolderConfig.name}"
                log.info("${logPrefix} creating jobChildFolder=[${jobChildFolder}]")
                dsl.folder(jobChildFolder) { // Use dsl.folder here
                    description "${jobFolderConfig.description}"
                }

                Map folderJobConfig = jobConfigs.findAll { !["jobFolders"].contains(it.key) }
                folderJobConfig.jobFolder = jobChildFolder
                log.debug("${logPrefix} folderJobConfig=${JsonUtils.printToJsonString(folderJobConfig)}")
                createJobs(folderJobConfig)
            }
        } else if (jobConfigs?.jobList) {
            List jobList = jobConfigs.jobList
            jobList.each { Map jobConfigRaw ->
                log.info("${logPrefix} jobConfigRaw=${JsonUtils.printToJsonString(jobConfigRaw)}")
                Map jobConfig = MapMerge.merge(jobConfigs.findAll {
                    !["jobList"].contains(it.key)
                    }, jobConfigRaw)
                log.debug("${logPrefix} creating child job with jobConfig=${JsonUtils.printToJsonString(jobConfig)}")
                createJobs(jobConfig)
            }
        } else {
            log.debug("${logPrefix} leaf node with jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
            if (jobConfigs?.jobName && jobConfigs?.jobScript) {
                log.debug("${logPrefix} Create job for jobConfigs.jobName=${jobConfigs.jobName}")
                createJob(jobConfigs)
            }
        }
    }

    // initJobs function
    void initJobs(String jobFolder, Map jobConfigs) {
        String logPrefix = "initJobs():"
        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        String jobName = jobConfigs.jobName
        String jobFolderPath = "${jobFolder}/${jobName}"

        def allJobs = Jenkins.instance.getAllItems(hudson.model.Job.class).findAll {
            it.getFullName().contains(jobFolderPath)
        }

        log.debug("${logPrefix} allJobs=${allJobs}")
        for(job in allJobs) {
            log.debug("${logPrefix} Initializing job ${job}")
            List params = []
            if (jobConfigs?.initializeJobMode && jobConfigs.initializeJobMode.toBoolean()) {
                params.push(new hudson.model.BooleanParameterValue('InitializeJobMode', true))
            }
            if (jobConfigs?.enableGitTestResults && jobConfigs.enableGitTestResults.toBoolean()) {
                params.push(new hudson.model.BooleanParameterValue('EnableGitTestResults', true))
            }
            def paramsAction = new hudson.model.ParametersAction(params)
            // You might need to adjust how currentBuild and cause are obtained if this is run outside a build context
            def cause = new hudson.model.Cause.UserCause() // Simpler cause for shared library invocation
            def causeAction = new hudson.model.CauseAction(cause)
            def scheduledJob = hudson.model.Hudson.instance.queue.schedule(job, 0, causeAction, paramsAction)
        }
    }
}
