package com.dettonville.jobdsl

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.JenkinsLogger

class PipelineJobFactory implements Serializable {

    def dsl
    def log

    PipelineJobFactory(dslContext) {
        this.dsl = dslContext
        this.log = new JenkinsLogger(this.dsl, prefix: this.class.name)
    }

    /**
     * Helper to add common SCM traits and clone options, reducing gitea/git duplication.
     */
    private void addCommonTraits(def traitsContext, Map jobConfigs, String wildcardIncludes) {
        traitsContext.with {
            pruneStaleBranch()
            pruneStaleTag()
            headWildcardFilter {
                includes(wildcardIncludes)
                excludes('')
            }
            cloneOption {
                extension {
                    shallow(true)
                    depth(2)
                    noTags(false)
                    if (jobConfigs?.mirrorRepoDir) {
                        reference(jobConfigs.mirrorRepoDir)
                    } else {
                        reference('')
                    }
                    honorRefspec(true)
                    timeout(15)
                }
            }
        }
    }

    def createMultibranchPipelineJob(String jobFolder, Map jobConfigs) {
        String logPrefix = "createMultibranchPipelineJob(${jobConfigs.jobName}):"
        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        String jobRepoUrl = jobConfigs.repoUrl
        String gitCredentialsId = jobConfigs.gitCredentialsId
        String jobName = jobConfigs.jobName
        String jobScript = jobConfigs.jobScript
        String jobFolderPath = "${jobFolder}/${jobName}"

        String branchSourceType = jobConfigs.get('branchSourceType', 'git')
        boolean useSuppressionStrategy = jobConfigs.get('useSuppressionStrategy', false).toBoolean()
        boolean buildAllBranchesBool = jobConfigs.get('buildAllBranches', false).toBoolean()
        List branchesToBuild = jobConfigs.get('branchesToBuild', []) as List
        String orphanedItemDaysToKeep = jobConfigs.get('orphanedItemDaysToKeep', '1')

        List branchesToDiscover = jobConfigs.get('branchesToDiscover', []) as List
        String wildcardIncludes = branchesToDiscover ? branchesToDiscover.join(' ') : '*'

        String giteaServerUrl = jobConfigs.get('giteaServerUrl', '')
        String giteaCredentialsId = jobConfigs.get('giteaCredentialsId', '')
        String giteaOwner = jobConfigs.get('giteaOwner', '')
        String giteaRepoName = jobConfigs.get('giteaRepoName', '')

        String jobId = jobConfigs?.jobGroupName ? "job-${jobConfigs.jobGroupName}-${jobName}" : "job-${jobName}"

        log.info("${logPrefix} creating multibranch job for jobName=${jobName} with branchSourceType=${branchSourceType}")

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
                                    addCommonTraits(delegate, jobConfigs, wildcardIncludes)
                                }
                            }
                        } else {
                            git {
                                id(jobId)
                                remote(jobRepoUrl)
                                credentialsId(gitCredentialsId)
                                traits {
                                    gitBranchDiscovery()
                                    addCommonTraits(delegate, jobConfigs, wildcardIncludes)
                                }
                            }
                        }
                    }
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
//                     'buildStrategies' {
//                         if (buildAllBranchesBool) {
//                             'jenkins.branch.buildstrategies.BuildAllBranchesStrategy'()
//                         } else if (branchesToBuild) {
//                             'jenkins.branch.buildstrategies.NamedBranchesFilterStrategy' {
//                                 filters {
//                                     'jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait' {
//                                         includes(branchesToBuild.join(' '))
//                                         excludes('')
//                                         caseSensitive(true)
//                                     }
//                                 }
//                             }
//                         } else {
//                             'jenkins.branch.DefaultBranchPropertyStrategy' {
//                                 'props' {
//                                     'jenkins.branch.NoTriggerBranchProperty'()
//                                 }
//                             }
//                         }
//                     }
                }
            }
            factory {
                workflowBranchProjectFactory {
                    scriptPath(jobScript)
                }
            }
            orphanedItemStrategy {
                discardOldItems {
                    daysToKeep(orphanedItemDaysToKeep.toInteger())
                }
                defaultOrphanedItemStrategy {
                    abortBuilds(true)
                    pruneDeadBranches(true)
                    numToKeepStr('10')
                    daysToKeepStr("1")
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

    /**
     * Helper to dynamically construct parameters from YAML configuration lists.
     */
    def addParameters(def parametersContext, List paramConfigs, Map jobConfigs) {
        parametersContext.with {
            paramConfigs.each { Map param ->
                String type = param.get('type', 'string')
                String name = param.name
                def defaultValue = param.get('defaultValue', param.get('default', ''))

                // Dynamic resolution or overrides from individual job configs
                if (name == 'AnsibleLimitHosts' && jobConfigs.containsKey('ansible_limit')) {
                    defaultValue = jobConfigs.get('ansible_limit')
                } else if (name == 'SkipUntagged' && jobConfigs.containsKey('skip_untagged')) {
                    defaultValue = jobConfigs.get('skip_untagged').toString().toBoolean()
                } else if (name == 'SkipAlwaysTag' && jobConfigs.containsKey('skip_always_tag')) {
                    defaultValue = jobConfigs.get('skip_always_tag').toString().toBoolean()
                }

                String description = param.get('description', '')

                switch (type.toLowerCase()) {
                    case 'string':
                    case 'stringparam':
                        stringParam(name, defaultValue?.toString() ?: '', description)
                        break
                    case 'boolean':
                    case 'booleanparam':
                        booleanParam(name, defaultValue != null ? defaultValue.toString().toBoolean() : false, description)
                        break
                    case 'choice':
                    case 'choiceparam':
                        List choices = param.get('choices', []) as List
                        choiceParam(name, choices, description)
                        break
                    case 'text':
                    case 'textparam':
                        textParam(name, defaultValue?.toString() ?: '', description)
                        break
                    case 'password':
                    case 'passwordparam':
                        passwordParam(name, defaultValue?.toString() ?: '', description)
                        break
                    default:
                        stringParam(name, defaultValue?.toString() ?: '', description)
                        break
                }
            }
        }
    }

    /**
     * New method to create a standard (non-multibranch) pipeline job.
     * Supports 'cps' / 'git' (passing to 'script') and 'cpsScm' (passing to 'scriptPath').
     *
     * Enhanced standard (non-multibranch) pipeline job supporting throttleConcurrentBuilds,
     * copyArtifactPermissionProperty, and initialization parameters.
     */
    def createStandardPipelineJob(String jobFolder, Map jobConfigs) {
        String logPrefix = "createStandardPipelineJob(${jobConfigs.jobName}):"
        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        String jobName = jobConfigs.jobName
        String jobScript = jobConfigs.get('jobScript', jobConfigs.get('jobScriptPath', ''))
        String jobFolderPath = "${jobFolder}/${jobName}"
        String scmSourceType = jobConfigs.get('scmSourceType', jobConfigs.get('definitionType', 'cpsScm'))
        boolean replaceExistingTemplate = jobConfigs.get('replaceExistingTemplate', false).toBoolean()

        String jobRepoUrl = jobConfigs.get('repoUrl', '')
        String gitCredentialsId = jobConfigs.get('gitCredentialsId', '')
        String mirrorRepoDir = jobConfigs.get('mirrorRepoDir', '')
        String gitBranch = jobConfigs.get('branch', '*/main')
        String cronSpecification = jobConfigs.get('cronSpecification', '')

        boolean addInitParams = jobConfigs.get('addInitializeParamsOnly', true).toBoolean()
        String copyArtifactProjects = jobConfigs.get('copyArtifactProjects', '')
        List throttleCategories = jobConfigs.get('throttleCategories', [])
        List parametersList = jobConfigs.get('parameters', [])

        log.info("${logPrefix} creating standard pipeline job for jobName=${jobName} with scmSourceType=${scmSourceType}")

        def jobObject = dsl.pipelineJob(jobFolderPath) {
            description jobConfigs.get('description', "Run ${jobName}")
            keepDependencies(false)

            parameters {
                if (addInitParams) {
                    booleanParam('InitializeParamsOnly', false, 'Set to true to only initialize parameters without full execution.')
                }
                if (jobConfigs.containsKey('replaceExistingTemplate')) {
                    booleanParam("ReplaceExistingTemplate", replaceExistingTemplate, 'Replace Existing Template?')
                }
                if (parametersList) {
                    addParameters(delegate, parametersList, jobConfigs)
                }
            }
            properties {
                logRotator {
                   daysToKeep(jobConfigs.get('daysToKeep', -1).toInteger())
                   numToKeep(jobConfigs.get('numToKeep', 10).toInteger())
                   artifactNumToKeep(jobConfigs.get('artifactNumToKeep', -1).toInteger())
                   artifactDaysToKeep(jobConfigs.get('artifactDaysToKeep', -1).toInteger())
                }
                if (copyArtifactProjects) {
                    copyArtifactPermissionProperty {
                        projectNames(copyArtifactProjects)
                    }
                }
                authorizationMatrix {
                    inheritanceStrategy { inheriting() }
                }
            }
            definition {
                if (scmSourceType == 'cps' || scmSourceType == 'git') {
                    cps {
                        script(jobScript)
                        sandbox(true)
                    }
                } else {
                    cpsScm {
                        lightweight(jobConfigs.get('lightweight', true).toBoolean())
                        scriptPath(jobScript)
                        scm {
                            git {
                                remote {
                                    url(jobRepoUrl)
                                    credentials(gitCredentialsId)
                                }
                                branch(gitBranch)
                                if (mirrorRepoDir) {
                                    extensions {
                                        cloneOptions {
                                            shallow(true)
                                            depth(2)
                                            noTags(true)
                                            reference(mirrorRepoDir)
                                            timeout(15)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (throttleCategories) {
                throttleConcurrentBuilds {
                    categories(throttleCategories)
                }
            }
            disabled(false)
        }

        if (cronSpecification) {
            log.info("${logPrefix} adding cronSpecification=[${cronSpecification}]")
            jobObject.properties {
                pipelineTriggers {
                    triggers {
                        cron { spec(cronSpecification) }
                    }
                }
            }
        }

        return jobObject
    }

    void createJob(Map jobConfigs) {
        String jobFolder = jobConfigs.jobFolder
        String jobType = jobConfigs.get('jobType', jobConfigs.get('pipelineType', 'multibranch'))
        String logPrefix = "createJob(${jobFolder}/${jobConfigs.jobName}):"

        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        def jobObject
        if (jobType == 'pipeline' || jobType == 'standard' || (jobConfigs.containsKey('scmSourceType') && jobConfigs.scmSourceType == 'cps')) {
            jobObject = createStandardPipelineJob(jobFolder, jobConfigs)
        } else {
            jobObject = createMultibranchPipelineJob(jobFolder, jobConfigs)
        }

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
//         log.info("${logPrefix} Initialize test jobs")
//         if (jobConfigs?.initializeJobMode && jobConfigs.initializeJobMode.toBoolean()) {
//             log.info("${logPrefix} Running test job initialization for jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
//             initJobs(dsl, jobFolder, jobConfigs)
//         }
    }

    void createJobs(Map jobConfigs) {
        String jobFolder = jobConfigs.jobFolder
        String logPrefix = "createJobs(${jobFolder}):"
        log.debug("${logPrefix} jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")

        log.info("${logPrefix} creating jobFolder=[${jobFolder}]")
        dsl.folder(jobFolder) {
            description jobConfigs.get('folderDescription', "")
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        if (jobConfigs?.jobGroupName) {
            String jobGroupFolder = "${jobFolder}/${jobConfigs.jobGroupName}"
            log.debug("${logPrefix} creating jobGroupFolder ${jobGroupFolder}")
            dsl.folder(jobGroupFolder) {
                description "Jobs for ${jobConfigs.jobGroupName}"
            }

            Map groupJobConfig = jobConfigs.findAll { !["jobGroupName"].contains(it.key) }
            groupJobConfig.jobFolder = jobGroupFolder
            createJobs(groupJobConfig)
        } else if (jobConfigs?.jobFolders) {
            jobConfigs.jobFolders.each { Map jobFolderConfig ->
                String jobChildFolder = "${jobConfigs.jobFolder}/${jobFolderConfig.name}"
                dsl.folder(jobChildFolder) { description "${jobFolderConfig.description}" }
                Map folderJobConfig = jobConfigs.findAll { !["jobFolders"].contains(it.key) }
                folderJobConfig.jobFolder = jobChildFolder
                log.debug("${logPrefix} folderJobConfig=${JsonUtils.printToJsonString(folderJobConfig)}")
                createJobs(folderJobConfig)
            }
        } else if (jobConfigs?.jobList) {
            jobConfigs.jobList.each { Map jobConfigRaw ->
                Map jobConfig = MapMerge.merge(jobConfigs.findAll { !["jobList"].contains(it.key) }, jobConfigRaw)
                log.debug("${logPrefix} creating child job with jobConfig=${JsonUtils.printToJsonString(jobConfig)}")
                createJobs(jobConfig)
            }
        } else {
            log.debug("${logPrefix} leaf node with jobConfigs=${JsonUtils.printToJsonString(jobConfigs)}")
            if (jobConfigs?.jobName && (jobConfigs?.jobScript || jobConfigs?.jobScriptPath)) {
                createJob(jobConfigs)
            }
        }
    }

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
            def cause = new hudson.model.Cause.UserCause()
            def causeAction = new hudson.model.CauseAction(cause)
            def scheduledJob = hudson.model.Hudson.instance.queue.schedule(job, 0, causeAction, paramsAction)
        }
    }
}
