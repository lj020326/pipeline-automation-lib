package com.dettonville.api.pipeline.ath
/*-
 *
 * For info on how to configure pipeline - see here:
 * https://fusion.dettonville.int/confluence/display/MAPI/How+to+use+the+Acceptance+Test+Harness+Pipeline
 *
 * OR here:
 * ref: https://fusion.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
 * ref: https://gitrepository.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
 *
*/

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.MapMerge

import com.dettonville.api.pipeline.utils.CredentialParser
import com.dettonville.api.pipeline.utils.JenkinsApiUtils
import com.dettonville.api.pipeline.utils.ArtifactApiUtils
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

import com.dettonville.api.pipeline.ath.EmailUtils

import groovy.json.JsonOutput

import java.time.*

class AcceptanceTestHarness implements Serializable {
    private static final long serialVersionUID = 1L

    com.dettonville.api.pipeline.utils.logging.Logger log = new com.dettonville.api.pipeline.utils.logging.Logger(this)
    def dsl

    com.dettonville.api.pipeline.utils.CredentialParser credentialParser
    com.dettonville.api.pipeline.utils.JenkinsApiUtils jenkinsApiUtils
    com.dettonville.api.pipeline.utils.ArtifactApiUtils artifactApiUtils
    com.dettonville.api.pipeline.utils.JsonUtils jsonUtils
    EmailUtils emailUtils

    Map pipelineConfig
//    boolean testResults = false
    List credIdList

    def agentLabelM3
    Map currentState = [:]

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    AcceptanceTestHarness(def dsl) {
        this.dsl = dsl

        com.dettonville.api.pipeline.utils.logging.Logger.init(this.dsl, com.dettonville.api.pipeline.utils.logging.LogLevel.INFO)
        this.credentialParser = new com.dettonville.api.pipeline.utils.CredentialParser(dsl)
        this.jenkinsApiUtils = new com.dettonville.api.pipeline.utils.JenkinsApiUtils(dsl)
        this.artifactApiUtils = new com.dettonville.api.pipeline.utils.ArtifactApiUtils(dsl)
        this.emailUtils = new EmailUtils(dsl)
        this.jsonUtils = new com.dettonville.api.pipeline.utils.JsonUtils(dsl)
//        testResults = false
        currentState.deployJobsRanDuringTest = false

    }

    def runAllTestSteps(Map params) {
        String logPrefix="runAllTestSteps():"

        dsl.stage("Pre-Test Steps") {
            log.info("${logPrefix} Running Pre-Test Steps")
            runPreTestSteps(params)
        }

        dsl.stage("Run Acceptance Test Harness") {
            log.info("${logPrefix} Running Tests")
            runTests()
        }

        dsl.stage('Post-Test Steps') {
            log.info("${logPrefix} Running Post-Test Steps")
            runPostTestSteps()
        }

        return this.pipelineConfig
    }

    Map runPreTestSteps(Map params) {
        String logPrefix="runPreTestSteps():"
        log.debug("${logPrefix} started")

        initPipeline(params)

        dsl.stash name: pipelineConfig.STASH_NAME_INIT, includes: "**"

        dsl.dir(pipelineConfig.checkoutDir) {
            if (pipelineConfig.startBrowserstackLocalAgent && pipelineConfig.runBsAgentMethod == "PER_RUN") {
                log.info("${logPrefix} Setup Browserstack Agent")
                if (!dsl.fileExists("${pipelineConfig.bsAgentBinPath}/BrowserStackLocal")) {
                    getBSAgent()
                }
            }

            log.info("${logPrefix} Compile ATH")
            if (pipelineConfig.useSimulationMode) {
                dsl.figlet "SIMULATION MODE"
            }

            if (!pipelineConfig.runSingleMvnCmdMode) {
                runMvnClean()
            }-

            if (pipelineConfig.useLocalMvnRepo && !pipelineConfig.runSingleMvnCmdMode) {
                dsl.stash name: pipelineConfig.STASH_NAME, includes: "**, ${pipelineConfig.mvnLocalRepoDir}/", excludes: "deploy_configs/"
            } else {
                dsl.stash name: pipelineConfig.STASH_NAME, includes: "**", excludes: "deploy_configs/"
            }
        }

        if (pipelineConfig?.deployJobEnvName && pipelineConfig.checkIfDeployJobsRan) {
            log.info("${logPrefix} Getting Pre-Test Deploy Job results")
            Map componentDeployJobSnapshots = [:]
            componentDeployJobSnapshots.before = getDeployJobResults()
            currentState.componentDeployJobSnapshots = componentDeployJobSnapshots
        }

        return this.pipelineConfig
    }

    Map initPipeline(Map params) {
        String logPrefix = "initPipeline():"
        log.debug("${logPrefix} started")

        this.pipelineConfig=loadPipelineConfig(params)
        log.debug("${logPrefix} initial pipelineConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(pipelineConfig)}")

        log.debug("NODE_NAME = ${dsl.env.NODE_NAME}")

        agentLabelM3 = getJenkinsAgentLabel(pipelineConfig.jenkinsM3NodeLabel)

        log.debug("${logPrefix} initializing/clearing out stashes")
        dsl.stash name: pipelineConfig.STASH_NAME_INIT, excludes: "**", allowEmpty: true
        dsl.stash name: pipelineConfig.STASH_NAME, excludes: "**", allowEmpty: true
        dsl.stash name: pipelineConfig.STASH_NAME_POST_TEST, excludes: "**", allowEmpty: true
        dsl.stash name: pipelineConfig.STASH_NAME_REPORTS, excludes: "**", allowEmpty: true

        log.debug("${logPrefix} initialize job cause info")
        pipelineConfig.jobCauseMap = [:]

        log.debug("${logPrefix} pipelineConfig.athGitRepo=${pipelineConfig.athGitRepo}")
        log.debug("${logPrefix} pipelineConfig.scmUrl=${pipelineConfig.scmUrl}")
        log.debug("${logPrefix} pipelineConfig.athGitBranch=${pipelineConfig.athGitBranch}")
        log.debug("${logPrefix} pipelineConfig.scmBranch=${pipelineConfig.scmBranch}")

        log.debug("${logPrefix} pipelineConfig.scmPomVersion=${pipelineConfig.scmPomVersion}")

        if (pipelineConfig.scmUrl != pipelineConfig.athGitRepo
                || pipelineConfig.athGitBranch != pipelineConfig.scmBranch
                || pipelineConfig.scmPomVersion == null) {
            log.info("${logPrefix} Checking out ATH Source from repo")
            checkoutAutomationCode(pipelineConfig)

            log.info("${logPrefix} Loading ATH Configs")
            pipelineConfig = loadPipelineConfig(params)
            log.info("${logPrefix} ****ATH Configs Loaded")

            if (pipelineConfig.athGitRepo != pipelineConfig.scmUrl
                    || pipelineConfig.athGitBranch != pipelineConfig.scmBranch
                    || pipelineConfig.scmPomVersion == null) {
                log.warn("${logPrefix} difference between desired Repo state and current Repo state")
                log.warn("${logPrefix} pipelineConfig.athGitRepo=${pipelineConfig.athGitRepo} =? pipelineConfig.scmUrl=${pipelineConfig.scmUrl}")
                log.warn("${logPrefix} pipelineConfig.athGitBranch=${pipelineConfig.athGitBranch} =? pipelineConfig.scmBranch=${pipelineConfig.scmBranch}")
                log.warn("${logPrefix} pipelineConfig.scmPomVersion=${pipelineConfig.scmPomVersion}")
            }
        } else {
            log.info("${logPrefix} **** ATH Config File already loaded since scmUrl and scmBranch agree with config")
        }

        if (pipelineConfig.getJobCause) {
            log.info("${logPrefix} getting job cause info")
            try {
                Map jobCauseMap = jenkinsApiUtils.getCurrentJobCauseInfo()
                log.info("${logPrefix} jobCauseMap=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(jobCauseMap)}")
                pipelineConfig.jobCauseMap = jobCauseMap
                pipelineConfig.jobCause = (pipelineConfig.jobCauseMap?.shortDescription) ? pipelineConfig.jobCauseMap.shortDescription : ""
                log.info("${logPrefix} pipelineConfig.jobCause=${pipelineConfig.jobCause}")

            } catch (Exception err) {
                log.error("${logPrefix} exception occurred getting job cause info: [${err}]")
            }
        }

        updateJobDescription()

        log.info("${logPrefix} final pipelineConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(pipelineConfig)}")

        return this.pipelineConfig
    }

    def runMvnClean() {
        String logPrefix="runMvnClean():"

        String mvnCmd = "${dsl.env.M3}/bin/mvn clean test ${pipelineConfig.mvnLogOptions} -Denv=${pipelineConfig.appEnvironment} -DskipTests=${pipelineConfig.skipTests}"

        if (pipelineConfig.useLocalMvnRepo) {
            // ref: https://www.jfrog.com/jira/browse/HAP-896
            // ref: https://stackoverflow.com/questions/47224781/share-local-maven-repository-to-agents-in-jenkins-pipeline
            dsl.sh "mkdir -p ${pipelineConfig.mvnLocalRepoDir}"

            mvnCmd += " -Dmaven.repo.local=${pipelineConfig.mvnLocalRepoDir}"
        }

        if (pipelineConfig.useSimulationMode) {
            log.info("${logPrefix} **** USING SIMULATION MODE - following command not actually run *****")
            log.info("${logPrefix} mvnCmd=${mvnCmd}")
        } else {
            log.debug("${logPrefix} mvnCmd=${mvnCmd}")
            dsl.sh "${mvnCmd}"
        }

    }

    def runPostTestSteps() {
        String logPrefix="runPostTestSteps():"
        log.info("${logPrefix} started")

//        dsl.deleteDir()
        dsl.cleanWs()
        log.debug("${logPrefix} unstashing test results")

        dsl.unstash name: pipelineConfig.STASH_NAME

        if (pipelineConfig.checkIfDeployJobsRan && pipelineConfig?.deployJobEnvName) {
            log.info("${logPrefix} Getting Post-Test Deploy Job results")
            currentState.componentDeployJobSnapshots.after = getDeployJobResults()

            currentState.componentDeployJobDiffResults = getAllBuildDiffs()

            log.info("${logPrefix} currentState.componentDeployJobDiffResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentState.componentDeployJobDiffResults)}")

            if (currentState.componentDeployJobDiffResults.size()>0) {
                log.error("${logPrefix} [${currentState.componentDeployJobDiffResults.size()}] Deployment Job Differences found")
                log.error("${logPrefix} Deployment Jobs were run during the Test Cycle")
                currentState.deployJobsRanDuringTest = true
                if (pipelineConfig.buildStatusConfig.getDeployBuildResults) {
                    dsl.archiveArtifacts artifacts: '*.json', onlyIfSuccessful: false
                }
            }
        }

        if (!pipelineConfig.runSingleMvnCmdMode) {
            getTestResults(pipelineConfig)
            log.debug("${logPrefix} aggregating test results")

            log.debug("${logPrefix} Post Integration - Serenity Report Creation")
            String mvnCmd = "${dsl.env.M3}/bin/mvn test serenity:aggregate ${pipelineConfig.mvnLogOptions} -Dserenity.outputDirectory=target/site/serenity -Denv=${pipelineConfig.appEnvironment} -DskipTests=${pipelineConfig.skipTests}"

            if (pipelineConfig.useLocalMvnRepo) {
                // ref: https://www.jfrog.com/jira/browse/HAP-896
                // ref: https://stackoverflow.com/questions/47224781/share-local-maven-repository-to-agents-in-jenkins-pipeline
                mvnCmd += " -Dmaven.repo.local=${pipelineConfig.mvnLocalRepoDir}"
            }

            if (pipelineConfig.useSimulationMode) {
                log.info("${logPrefix} **** USING SIMULATION MODE - following command not actually run *****")
                log.info("${logPrefix} mvnCmd=${mvnCmd}")
            } else {
                log.debug("${logPrefix} mvnCmd=${mvnCmd}")
                dsl.sh "${mvnCmd}"
                dsl.junit 'target/site/serenity/*.xml'

                if (!pipelineConfig.skipTests) {
                    dsl.junit 'target/surefire-reports/*.xml'
                }
            }
        }

        if (pipelineConfig.debugPipeline && !pipelineConfig.useSimulationMode) {
            log.debug("${logPrefix} find results:")
            runFind()
        }

        if (pipelineConfig?.collectTestResults) {
            log.info("${logPrefix} append to aggregated test results")
            updateHistoricalTestResults(pipelineConfig)
        }

        if (!pipelineConfig.runSingleMvnCmdMode && pipelineConfig.runSerenityRpt) {
            dsl.dir("target/site/serenity") {
                String summaryFile = "summary.txt"
                if (dsl.fileExists(summaryFile)) {
                    dsl.archiveArtifacts artifacts: summaryFile
                    log.info("${logPrefix} ${summaryFile} archived")
                } else {
                    log.warn("${logPrefix} ${summaryFile} not found")
                }
            }

            if (dsl.fileExists("target/site/serenity/summary.txt")) {
                dsl.stash name: pipelineConfig.STASH_NAME_REPORTS, includes: "target/**/summary.txt"
            }

            dsl.figlet 'Publish Reports'
            if (pipelineConfig.publishSerenityRpt) {
                dsl.publishHTML(target: [allowMissing         : true,
                                         alwaysLinkToLastBuild: true,
                                         keepAll              : true,
                                         reportDir            : 'target/site/serenity',
                                         reportFiles          : 'index.html',
                                         reportName           : pipelineConfig.serenityRptName])
            }

        }

        currentState.duration = "${dsl.currentBuild.durationString.replace(' and counting', '')}"

        log.info("${logPrefix} save currentState to ${pipelineConfig.jobResultsFile}")
        log.debug("${logPrefix} removing componentDeployJobSnapshots from currentState before saving since noisy/unnecessary")
        Map currentStateSave = currentState.findAll { it.key != 'componentDeployJobSnapshots' }
        def jsonOut = dsl.readJSON text: JsonOutput.toJson(currentStateSave)
        dsl.writeJSON file: pipelineConfig.jobResultsFile, json: jsonOut, pretty: 4
        dsl.archiveArtifacts artifacts: pipelineConfig.jobResultsFile

        dsl.stash name: pipelineConfig.STASH_NAME_POST_TEST

        log.info("${logPrefix} Set Pipeline Status")
        dsl.currentBuild.result = (currentState.testResults) ? 'SUCCESS' : 'FAILURE'
        log.info("${logPrefix} **** dsl.currentBuild.result=${dsl.currentBuild.result}")

        updateJobDescription()

    }

    Map getDeployJobResults() {
        String logPrefix="getDeployJobResults():"
        log.debug("${logPrefix} started")

        Map config = pipelineConfig.deployConfig + pipelineConfig.buildStatusConfig
        config.deployJobEnvName = config.get("deployJobEnvName", pipelineConfig.deployJobEnvName)
        Map buildResults = [:]

        config.componentList.each { Map component ->
            Map componentConfig = config.findAll { it.key != 'componentList' } + component

            if (componentConfig?.jobPrefix) componentConfig.deployJobName = "${componentConfig.jobPrefix}${componentConfig.deployJobName}"
            if (!componentConfig.hierarchicalDeployJobs) componentConfig.deployJobName += "${componentConfig.deployJobEnvName}"

            if (componentConfig.hierarchicalDeployJobs) {
                componentConfig.jobBaseUri += "/${componentConfig.deployJobEnvName.toUpperCase()}/job/${componentConfig.deployJobName}"
            } else {
                componentConfig.jobBaseUri += "/${componentConfig.deployJobName}/job/${componentConfig.branch}"
            }

            log.info("${logPrefix} componentConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentConfig)}")

            Integer buildNumber = jenkinsApiUtils.getBuildNumber(componentConfig.jobBaseUri)
            Map buildInfo = jenkinsApiUtils.getBuildResults(buildNumber, componentConfig)
//            componentConfig.version = buildResults[componentConfig.name].actions.findResult { it.parameters }[0].value
//            componentConfig.version = buildInfo.actions.findResult { it.parameters }.findResult { it.name=="ArtifactVersion" ? it.value : null }
            componentConfig.version = buildInfo.actions.findResult { it.parameters }.findResult { it.name==componentConfig.jobVersionParamName ? it.value : null }
            buildInfo.componentVersion = componentConfig.version
            buildInfo.componentName = componentConfig.name

            log.info("${logPrefix} componentConfig.version=${componentConfig.version}")

            componentConfig.deployUrl = buildInfo.url

            if (pipelineConfig.getLatestArtifactVersion) {
                buildInfo.latestArtifactVersionInfo = artifactApiUtils.getLatestArtifactVersion(componentConfig)
            }

            if (componentConfig.hierarchicalDeployJobs) {
                componentConfig.deployArtifactFile="DeployInfo.json"
                componentConfig.deployResultsFile = "DeployInfo.${componentConfig.name}.${buildNumber}.json"
                if (jenkinsApiUtils.getJobArtifactFromBuildUrl(componentConfig.deployUrl, componentConfig.deployArtifactFile, componentConfig.deployResultsFile)==200) {
                    log.debug("${logPrefix} ${componentConfig.deployResultsFile} retrieved")
                    Map deployResults = dsl.readJSON file: componentConfig.deployResultsFile
                    log.debug("${logPrefix} deployResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(deployResults)}")
                    buildInfo.deployResults = deployResults
                    dsl.archiveArtifacts(artifacts: componentConfig.deployResultsFile, onlyIfSuccessful: false)
                } else {
                    log.warn("${logPrefix} artifact [${componentConfig.deployArtifactFile}] not found for ${componentConfig.deployUrl}")
                }
            }

            if (componentConfig.getDeployBuildResults) {
                componentConfig.buildResultsFile = "BuildInfo.${componentConfig.name}.${buildNumber}.json"
                def jsonOut = dsl.readJSON text: JsonOutput.toJson(buildInfo)
                log.debug("${logPrefix} jsonOut=${jsonOut}")
                dsl.writeJSON file: componentConfig.buildResultsFile, json: jsonOut, pretty: 4
                dsl.archiveArtifacts(artifacts: componentConfig.buildResultsFile, onlyIfSuccessful: false)
            }

            buildResults[componentConfig.name] = buildInfo

        }
        log.debug("${logPrefix} buildResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(buildResults)}")
        return buildResults
    }

    List getAllBuildDiffs() {
        String logPrefix="getAllBuildDiffs():"
        log.info("${logPrefix} started")

        Map config = pipelineConfig.deployConfig + pipelineConfig.buildStatusConfig
        config.deployJobEnvName = config.get("deployJobEnvName", pipelineConfig.deployJobEnvName)
        List componentDiffResults = []

        config.componentList.each { Map component ->
            Map componentConfig = config.findAll { it.key != 'componentList' } + component

            componentConfig.deployResultsFileName=componentConfig.name
            log.info("${logPrefix} componentConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentConfig)}")

            componentConfig.buildResultsBefore = currentState.componentDeployJobSnapshots.before[componentConfig.name]
            componentConfig.buildResultsAfter = currentState.componentDeployJobSnapshots.after[componentConfig.name]

            log.debug("${logPrefix} componentConfig.buildResultsBefore=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentConfig.buildResultsBefore)}")
            log.debug("${logPrefix} componentConfig.buildResultsAfter=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentConfig.buildResultsAfter)}")

            Map buildDiffs
            try {
                buildDiffs = getBuildDiffs(componentConfig)
            } catch (Exception err) {
                log.error("${logPrefix} job exception occurred [${err}]")
            }
            log.info("${logPrefix} [${componentConfig.name}] buildDiffs=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(buildDiffs)}")

            if (!buildDiffs.isEmpty()) {
                componentDiffResults.add(buildDiffs)
            }
        }
        log.info("${logPrefix} componentDiffResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentDiffResults)}")
        return componentDiffResults
    }

    Map getBuildDiffs(Map config) {
        String logPrefix = "getBuildDiffs():"
        Map deployJobDiffs = [:]

        Map buildResultsBefore = config.buildResultsBefore
        Map buildResultsAfter = config.buildResultsAfter

        log.info("${logPrefix} compare build results json")

//        Boolean isDiff = jsonUtils.isJsonDiff(buildResultsBefore, buildResultsAfter, true)
        Boolean isDiff = (buildResultsBefore.number != buildResultsAfter.number)

        log.info("${logPrefix} isDiff = ${isDiff}")

        if (!isDiff) {
            return deployJobDiffs
        }
        log.info("${logPrefix} config=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")
        log.info("${logPrefix} buildResultsBefore=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(buildResultsBefore)}")
        log.info("${logPrefix} buildResultsAfter=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(buildResultsAfter)}")

        deployJobDiffs.isDiff = isDiff
        deployJobDiffs.component = config.name
        deployJobDiffs.name = config.name

        deployJobDiffs.jobBaseUri = config.jobBaseUri
        deployJobDiffs.buildNumberBefore = buildResultsBefore.number
        deployJobDiffs.buildNumberAfter = buildResultsAfter.number

//        diffs = jsonUtils.getJsonDiffs(buildResultsBefore, buildResultsAfter, true)
////    deployJobDiffs.diffs = diffs
//
//        Map diffMap = diffs.collectEntries{ Map diff ->
//            [(diff.label):diff.findAll { it.key != 'label' }]
//        }
//        log.debug("${logPrefix} diffs=${diffs}")
        Map diffMap = jsonUtils.getJsonDiffMap(buildResultsBefore, buildResultsAfter, true)
        deployJobDiffs.diffs = diffMap

        if (config.getDeployBuildResults && isDiff) {
            deployJobDiffs.buildDiffResultsFile = "${config.name}.diffs.json"
            def jsonOut = dsl.readJSON text: JsonOutput.toJson(deployJobDiffs)
            dsl.writeJSON file: deployJobDiffs.buildDiffResultsFile, json: jsonOut, pretty: 4
            dsl.archiveArtifacts artifacts: '*.json', onlyIfSuccessful: false
        }

        log.info("${logPrefix} deployJobDiffs=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(deployJobDiffs)}")

        return deployJobDiffs
    }

    Map getDefaultSettings() {
        // get default job config settings
        return PipelineDefaults.getDefaultSettings(dsl)
    }

    Map loadPipelineConfig(Map params, String configFile=null) {
        String logPrefix="loadPipelineConfig():"
        log.debug("${logPrefix} starting...")

        // set job config settings
        Map defaultSettings = getDefaultSettings()
        Map config=defaultSettings.pipeline

        log.debug("${logPrefix} config(1)=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")

        // handle config yml
        // handle config file
        if (configFile != null && dsl.fileExists(configFile)) {
            Map configSettings = dsl.readYaml file: "${configFile}"
            config = com.dettonville.api.pipeline.utils.MapMerge.merge(config, configSettings.pipeline)
        } else if (configFile != null) {
            log.debug("${logPrefix} pipeline config file ${configFile} not present, using defaults...")
        } else {
            log.debug("${logPrefix} pipeline config file not specified, using defaults...")
        }
        if (params?.yml) {
            Map ymlConfig = dsl.readYaml text: params.yml
            log.debug("${logPrefix} ymlConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(ymlConfig)}")
            config = com.dettonville.api.pipeline.utils.MapMerge.merge(config, ymlConfig.pipeline)
        }

        log.debug("${logPrefix} params=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(params)}")

        // copy immutable params maps to mutable config map
//        params.each { key, value ->
//            key=Utilities.decapitalize(key)
//            if (value!="") {
//                config[key]=value
//            }
//        }
        config = com.dettonville.api.pipeline.utils.MapMerge.merge(config, params)
        log.debug("${logPrefix} config(2)=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")

//        config=encodeToUTF8(config)

        log.setLevel(config.logLevel)

        if (config.debugMvn || config.debugPipeline) {
            if (config.debugMvn) config.mvnLogOptions="-B"
            config.mvnLogOptions+=" -X"
            config.mvnLogOptions+=" -Dverbose=true"
        }

        // browser platform info
        if (["multiplatform","multi-platform"].contains(config.browserstackBrowser.toLowerCase())) {
            List platformList = []
            config?.testGroups.each() { Map testGroup ->
//            platformList += (testGroup?.name) ? testGroup.name : "{${JsonUtils.printToJsonString(testGroup)}}"
                platformList += (testGroup?.name) ? testGroup.name : "{${testGroup}}"
            }
            config.browserPlatform = "Multi -> [${platformList.join(", ")}]"
        } else {
            config.browserstackBrowserVersion = getBrowserstackBrowserVersion(config)
//        config.browserPlatform = "${config.browserstackWebOS}[${config.browserstackWebOSVersion}]/${config.browserstackBrowser}"
//        config.browserPlatform += "[${config.browserstackBrowserVersion}]"
            config.browserPlatform = "${config.browserstackBrowser}[v: ${config.browserstackBrowserVersion} OS: ${config.browserstackWebOS}-${config.browserstackWebOSVersion}]"
        }

        config.parallelRunCount = getYamlInt(config, "parallelRunCount")
        config.waitingTime = getYamlInt(config, "waitingTime")
        config.groupWaitTime = getYamlInt(config, "groupWaitTime")

        //
        // essential/minimal params
        //
        config.scmUrl = getGitUrl()
        config.scmBranch = getGitBranchName()
        config.scmPomVersion = getAthPomVersion()

        log.debug("${logPrefix} JOB_NAME = ${dsl.env.JOB_NAME}")
//    config.application = config.get('application', dsl.env.JOB_NAME.replaceAll('%2F', '/').replaceAll('/', '-').replaceAll(' ', '-').toUpperCase())
        log.debug("${logPrefix} config.application = ${config.application}")

        List jobParts = dsl.env.JOB_NAME.split("/")
        jobParts.remove(0)
//    jobParts.remove(jobParts.size() - 1)

//    config.browserstackProject = "${config.jenkinsProjectName}_${config.appEnvironment}"
//    config.browserstackProject = config.browserstackProject ?: config.application
        config.browserstackProject = config.browserstackProject ?: config.jobName.toUpperCase()

//    config.browserstackName = jobParts.collect{ it.toLowerCase().replaceAll(' ', '').replaceAll('-', '').replaceAll('_', '') }.join('-')
        config.browserstackName = config.jobName.toUpperCase()
        log.debug("${logPrefix} config.browserstackName = ${config.browserstackName}")

        config.runBsAgentMethod=(config.runBsAgentMethod=="PER_THREAD") ? "PER_RUN" : config.runBsAgentMethod
        config.bsAgentBaseDir=(config.runBsAgentMethod=="PER_RUN") ? "tmp/${config.jenkinsProjectName}" : "/var/tmp/${config.jenkinsProjectName}"
        config.bsAgentBinDir="${config.bsAgentBaseDir}/bin"

        config.bsAgentLogDir=
                (config.runBsAgentMethod=="PER_JOB_RUN") ? "${config.bsAgentBaseDir}/runs/${config.jobName}-${dsl.env.BUILD_NUMBER}"
                        : (config.runBsAgentMethod=="PER_JOB") ? "${config.bsAgentBaseDir}/jobs/${config.jobName}"
                        : (config.runBsAgentMethod=="PER_NODE") ? "${config.bsAgentBaseDir}/nodes"
                        : (config.runBsAgentMethod=="PER_RUN") ? "${config.bsAgentBaseDir}/runs"  // else PER_RUN
                        : "tmp"  // else ??

        this.credIdList = getCredentialIdList(config)
        jenkinsApiUtils.setJenkinsApiCredId(config.jenkinsApiCredId)
        artifactApiUtils.setArtifactoryApiCredId(config.artifactoryApiCredId)

        if (config.debugPipeline) {
            log.setLevel(com.dettonville.api.pipeline.utils.logging.LogLevel.DEBUG)
            log.debug("${logPrefix} **********************")
            log.debug("${logPrefix} pipeline env variables:")
            dsl.sh('printenv | sort')
            log.debug("${logPrefix} **********************")
        }

        config.STASH_NAME_INIT="${config.application}_${dsl.env.BUILD_NUMBER}"
        config.STASH_NAME="${config.STASH_NAME_INIT}_CLEAN"
        config.STASH_NAME_POST_TEST="${config.STASH_NAME_INIT}_POST"
        config.STASH_NAME_REPORTS="${config.STASH_NAME_INIT}_RPT"

        log.debug("${logPrefix} final config=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")

        return config
    }

//boolean runJob(Map config) {
    void runJob(Map config) {

        String logPrefix="runJob():"
        config.supportedJobParams=['changedEmailList','alwaysEmailList','failedEmailList']

        // This will copy all files packaged in STASH_NAME to agent workspace root directory.
        // To copy to another agent directory, see [https://github.com/jenkinsci/pipeline-examples]
        log.info("${logPrefix} started")

        boolean result = false
        List paramList=[]

        config.each { key, value ->
            if (key in config.supportedJobParams) {
                paramList.add([$class: 'StringParameterValue', name: key, value: value])
            }
        }

        if (config.get('job',null)==null) {
            log.error("${logPrefix} job not specified")
            return result
        }

        try {
            log.info("${logPrefix} starting job ${config.job}")

            // ref: http://jenkins-ci.361315.n4.nabble.com/How-to-get-build-results-from-a-build-job-in-a-pipeline-td4897887.html
//            def jobBuild = build job: config.job, parameters: paramList, wait: config.wait, propagate: false
            def jobBuild = build job: config.job, parameters: paramList, wait: false
        } catch (Exception err) {
            log.error("${logPrefix} job exception occurred [${err}]")
        }

    }

    String getGitUrl() {
        String logPrefix="getGitUrl():"
        String scmUrl
        try {
            // ref: https://stackoverflow.com/questions/38254968/how-do-i-get-the-scm-url-inside-a-jenkins-pipeline-or-multibranch-pipeline
            scmUrl = dsl.sh(returnStdout: true, script: 'git config remote.origin.url || true').trim()
            log.info("${logPrefix} scmUrl=${scmUrl}")
        } catch (Exception err) {
            log.debug("${logPrefix} exception occurred [${err}]")
        }
        return scmUrl
    }

    String getGitBranchName() {
        String logPrefix="getGitBranchName():"
//    return scm.branches[0].name
        String scmBranch
        try {
            scmBranch = dsl.sh(returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD || true').trim()

            // ref: https://stackoverflow.com/questions/6059336/how-to-find-the-current-git-branch-in-detached-head-state
            // ref: https://stackoverflow.com/questions/6245570/how-to-get-the-current-branch-name-in-git/19585361
            if (scmBranch=="HEAD") {
                scmBranch = dsl.sh(returnStdout: true, script: 'git show -s --pretty=%D HEAD | tr -s "," "\n" | sed "s/^ //" | grep -v -e HEAD | sed "s/origin\\///" || true').trim()
                log.info("${logPrefix} scmBranch=${scmBranch}")
            }
        } catch (Exception err) {
            log.debug("${logPrefix} exception occurred [${err}]")
        }
        return scmBranch
    }

    String getAthPomVersion() {
        String logPrefix="getAthPomVersion():"
        String athVersion
        try {
            athVersion = (readFile('pom.xml') =~ '<version>(.+)</version>')[0][1]
            log.info("${logPrefix} athVersion=${athVersion}")
        } catch (Exception err) {
            log.debug("${logPrefix} exception occurred [${err}]")
        }
        return athVersion
    }

    def getYamlInt(Map config, String key) {
        def value
        if (config.containsKey(key)) {
            try {
                value = config[key].toInteger()
            } catch (Exception err) {
                value = config[key]
            }
        }
        return value
    }

    String getJenkinsAgentLabel(String jenkinsLabel) {
        // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
        return "${-> dsl.println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
    }

//// encode to UTF8
//Map encodeToUTF8(Map params) {
//    Map config[:]
//    params.each { key, value ->
////        log.debug("params[${key}]=${value}")
//        key=Utilities.decapitalize(key)
//        if (value !="") {
////            config[key]=value
//            log.info("${logPrefix} value.getClass().toString()=${value.getClass().toString()}")
//
//            byte[] ptext = value.toString().getBytes(ISO_8859_1)
//            config[key] = new String(ptext, UTF_8)
//
////            if (value.getClass().toString()=="String") {
////                byte[] ptext = value.getBytes(ISO_8859_1);
////                config[key] = new String(ptext, UTF_8);
////            } else {
////                config[key]=value
////            }
//        }
//    }
//    return config
//}


/**
 * Checkout Automation Code
 **/
    void checkoutAutomationCode(Map config) {

        // wipe the workspace so we are building completely clean
//        dsl.deleteDir()
        dsl.cleanWs()

        dsl.checkout scm: [
                $class                           : 'GitSCM',
                branches                         : [[name: config.athGitBranch]],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: config.checkoutDir], [$class: 'CheckoutOption']],
                submoduleCfg                     : [],
                userRemoteConfigs: [[credentialsId: config.jenkinsRepoCredId, url: config.athGitRepo]]
        ]

    }

    List getCredentialIdList(Map config) {
        String logPrefix="getCredentialIdList():"
        List credIdList = []

        log.debug("${logPrefix} started}")

        if (! dsl.fileExists(config.checkoutDir)) {
            log.info("${logPrefix} checkoutDir [${config.checkoutDir}] not found, done")
            return []
        }

        dsl.dir(config.checkoutDir) {
            if (config.debugPipeline) {
                dsl.sh 'find . -name environment.json -type f -printf "%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n" | sort -k 3,3'
            }

            Map envConfigs = loadJavaEnvConfigs(config.appEnvironment)
            if (envConfigs.size()>0) {
                log.debug("${logPrefix} envConfigs=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(envConfigs)}")
            }

            credIdList = credentialParser.getCredentialIdListFromJson(envConfigs)

        }

        credIdList.add(dsl.usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER'))
        credIdList.add(dsl.string(credentialsId: config.jenkinsRallyCredId, variable: 'RALLY_KEY'))
        log.info("${logPrefix} credIdList=${credIdList}")

        return credIdList
    }

    Map loadJavaEnvConfigs(String appEnvironment) {

        String envFilePath = "./src/test/resources/config/environments/${appEnvironment}"
        String envFile = "${envFilePath}/environment.json"

        String logPrefix = "loadJavaEnvConfigs(appEnvironment=${appEnvironment}):"
        log.info("${logPrefix} started}")

//    log.info("${logPrefix} find results from ${envFilePath}/:")
//    dsl.sh "find ${envFilePath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p\\n\" | sort -k 3,3"

        Map envConfigs=[:]

        if (!dsl.fileExists(envFile)) {
            return envConfigs
        }

        envConfigs = dsl.readJSON file: envFile

        if (envConfigs?.extendEnvironment) {
            Map envParentConfigs = loadJavaEnvConfigs(envConfigs.extendEnvironment)
            envConfigs = MapMerge.merge(envParentConfigs, envConfigs.findAll { !["extendEnvironment"].contains(it.key) })
        }

        log.debug("${logPrefix} envConfigs=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(envConfigs)}")

        return envConfigs
    }

    Map runTests(Map config=pipelineConfig.clone()) {
        String logPrefix="runTests():"
        log.info("${logPrefix} started")
        log.debug("${logPrefix} config=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")

        List testResults = []
        if (config.useTestGroups && config?.testGroups) {
            Map parallelGroups = [:]
            config.testGroups.eachWithIndex { it, i ->

                log.info("${logPrefix} i=${i} it=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(it)}")

                // set group to default and overlay any group settings
                Map groupConfig = config.findAll { it.key != 'testGroups' } + it

                groupConfig.groupId = createGroupId(config, i)
                int groupNum = i+1

                log.debug("${logPrefix} i=${i} groupConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(groupConfig)}")

                if (groupConfig?.testGroups) {
                    testResults.add(runTests(groupConfig))
                }
                if (groupConfig?.testCases) {
                    parallelGroups["split-${groupConfig.groupId}"] = {
                        if (config.useStaggeredParallelStart && config?.groupWaitTime) {
                            int waitTime = (groupNum - 1) * config.groupWaitTime
                            log.info("${logPrefix} waiting ${waitTime} seconds to start...")
                            dsl.sleep(time: waitTime, unit: 'SECONDS')
                        }
                        testResults.add(runTestCases(groupConfig))
                    }
                }
            }
            if (parallelGroups.size()>0) {
                log.info("${logPrefix} parallelGroups=${parallelGroups}")
                dsl.parallel parallelGroups
            }
        } else {
            log.info("${logPrefix} Running single parallel run - batch mode = [${config.useBatchMode}]")
            testResults.add(runTestParallel(config))
        }
        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        boolean result = (testResults.size()>0) ? testResults.inject { a, b -> a && b } : true
        log.info("${logPrefix} finished: result = ${result}")

        currentState.result = result
        log.info("${logPrefix} currentState=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentState.findAll { it.key != 'componentDeployJobSnapshots' })}")

        currentState.testResults = result
        return currentState
    }

    String createGroupId(Map config, i) {
        return (config?.groupId) ? "${config.groupId}.${i}" : "group${i}"
    }

    boolean runTestCases(Map config) {

        String logPrefix="runTestCases():"
        log.debug("${logPrefix} config=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")

        List testResults = []
        Map parallelTestCases = [:]
        config.testCases.eachWithIndex { it, i ->

            log.info("${logPrefix} i=${i} it=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(it)}")

            // set group to default and overlay any group settings
            Map testConfig = config.findAll { it.key != 'testCases' } + it
            testConfig.testCaseId="${config.groupId}-testcase${i}"

            log.debug("${logPrefix} i=${i} testConfig=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(testConfig)}")

            int caseNum = i+1
            parallelTestCases["split-${testConfig.testCaseId}"] = {
                if (config.useStaggeredParallelStart && config?.groupWaitTime) {
                    int waitTime = (caseNum - 1) * config.groupWaitTime
                    log.info("${logPrefix} waiting ${waitTime} seconds to start...")
                    dsl.sleep(time: waitTime, unit: 'SECONDS')
                }
                testResults.add(runTestParallel(testConfig))
            }
        }

        log.info("${logPrefix} parallelTestCases=${parallelTestCases}")
        dsl.parallel parallelTestCases

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        boolean result = (testResults.size()>0) ? testResults.inject { a, b -> a && b } : true

        log.info("${logPrefix} finished: result = ${result}")
        return result
    }

    boolean runTestParallel(Map config) {

        String logPrefix = "runTestParallel():"
        log.info("${logPrefix} started")
        boolean result = false
        def agentLabelRunTests = getJenkinsAgentLabel(config.jenkinsRunTestsLabel)

        if (!config.useSingleTestNode) {
            result = runTestParallelRun(config)
        } else {
            dsl.node(agentLabelRunTests as String) {  // Evaluate the node label later
                dsl.cleanWs()
                dsl.unstash name: config.STASH_NAME

                result = runTestParallelRun(config)
                dsl.cleanWs()
            }
        }
        return result
    }

    boolean runTestParallelRun(Map config) {

        String logPrefix="runTestParallelRun():"
        Map parallelTests = [:]
        def agentLabelRunTests = getJenkinsAgentLabel(config.jenkinsRunTestsLabel)

        log.info("${logPrefix} config.parallelRunCount=${config.parallelRunCount}")
        List testResults = []
        for (int i = 1; i  <= config.parallelRunCount; i++) {
            Map parallelRunConfig=config.clone()

            parallelRunConfig.parallelRunNumber=i

            log.info("${logPrefix} parallelRunConfig.parallelRunNumber=${parallelRunConfig.parallelRunNumber}")

            parallelRunConfig.testCaseLabel=getTestCaseLabel(parallelRunConfig)
            log.debug("${logPrefix} parallelRunConfig.testCaseLabel=${parallelRunConfig.testCaseLabel}")

            // ref: https://github.com/jenkinsci/pipeline-plugin/blob/master/TUTORIAL.md#multiple-threads
            parallelTests["split-${parallelRunConfig.testCaseLabel}"] = {
                if (config.useStaggeredParallelStart) {
                    int waitTime = (parallelRunConfig.parallelRunNumber - 1) * config.waitingTime
                    log.info("${logPrefix} waiting ${waitTime} seconds to start...")
                    dsl.sleep(time: waitTime, unit: 'SECONDS')
                }

                boolean result = false
                if (config.useSingleTestNode) {
                    result = runAcceptanceTestCase(parallelRunConfig)
                } else {
                    dsl.node(agentLabelRunTests as String) {  // Evaluate the node label later
                        result = runAcceptanceTestCase(parallelRunConfig)
                    }
                }
                log.info("${logPrefix} result=${result}")
                testResults.add(result)
            }
        }

        dsl.parallel parallelTests

        boolean overallResult = (testResults.size()>0) ? testResults.inject { a, b -> a && b } : true

        return overallResult
    }


    String getTestCaseLabel(Map config) {
//    return (config?.testCaseId) ? "${config.testCaseId}-run${config.parallelRunNumber}" : "run${config.parallelRunNumber}"
        return (config?.testCaseId) ? "${config.testCaseId}-run${config.parallelRunNumber.toString().padLeft(2,'0')}" : "run${config.parallelRunNumber.toString().padLeft(2,'0')}"
    }

    String createBrowserstackLocalIdentifier(Map config) {
        String logPrefix="createBrowserstackLocalIdentifier():"
        String browserstackLocalIdentifier
        if (config.runBsAgentMethod == "PER_RUN") {
//        browserstackLocalIdentifier="${config.jenkinsProjectName}-${UUID.randomUUID()}"
            browserstackLocalIdentifier = "${config.jenkinsProjectName}-${config.jobAcronymName}-${dsl.env.BUILD_NUMBER.toString().padLeft(5,'0')}-${config.testCaseLabel}"
        } else if (config.runBsAgentMethod == "PER_JOB_RUN") {
            browserstackLocalIdentifier="jobrun-${config.jobName}-${dsl.env.BUILD_NUMBER}-${config.nodeId}"
        } else if (config.runBsAgentMethod == "PER_JOB") {
//        browserstackLocalIdentifier="${dsl.env.NODE_NAME}-${config.jobName}-${config.browserstackLocalIdentifier}"
//        browserstackLocalIdentifier="${nodeId}-${config.jobName}-${config.browserstackLocalIdentifier}"
            browserstackLocalIdentifier="job-${config.jobName}-${config.nodeId}"
        } else {
            // PER_NODE
            browserstackLocalIdentifier="node-${config.jenkinsProjectName}-${config.nodeId}"
        }

        browserstackLocalIdentifier=browserstackLocalIdentifier.toLowerCase()
        log.info("${logPrefix} -> created browserstackLocalIdentifier=${browserstackLocalIdentifier}")
        return browserstackLocalIdentifier
    }

    boolean runAcceptanceTestCase(Map config) {
        String logPrefix="runAcceptanceTestCase():"

        boolean result = false

        if (config.startBrowserstackLocalAgent) {
            config.browserstackLocalIdentifier=createBrowserstackLocalIdentifier(config)
            log.info("${logPrefix} *** ASSIGNED browserstackLocalIdentifier=${config.browserstackLocalIdentifier}")
        }

        dsl.dir(config.testCaseLabel) {
            dsl.cleanWs()

            // This will copy all files packaged in STASH_NAME to agent workspace root directory.
            // To copy to another agent directory, see [https://github.com/jenkinsci/pipeline-examples]
            dsl.unstash name: config.STASH_NAME

            if (config.startBrowserstackLocalAgent) {
                withBsLocalAgent config, {
                    result = runAcceptanceTest(config)
                }
            } else {
                result = runAcceptanceTest(config)
            }

            dsl.cleanWs()

        }

        return result
    }

    def getTestResults(Map config) {

        String logPrefix="getTestResults():"

        if (config.useTestGroups && config?.testGroups) {
            config.testGroups.eachWithIndex { it, i ->

                // set group to default and overlay any group settings
                Map groupConfig = config.findAll { it.key != 'testGroups' } + it
                groupConfig.groupId = createGroupId(config, i)

                if (it?.testGroups) {
                    getTestResults(groupConfig)
                }

                if (groupConfig?.testCases) {
                    groupConfig.testCases.eachWithIndex { it2, i2 ->

                        // set group to default and overlay any group settings
                        Map testConfig = groupConfig + it2
                        testConfig.testCaseId="${testConfig.groupId}-testcase${i2}"
                        log.debug("${logPrefix} testConfig.testCaseId=${testConfig.testCaseId}")

                        getParallelRunTestResults(testConfig)
                    }
                }
            }
        } else {
            getParallelRunTestResults(config)
        }
    }

    def getParallelRunTestResults(Map config) {
        String logPrefix="getParallelRunTestResults():"

        if (config.useSimulationMode) {
            log.debug("${logPrefix} ***** RUNNING SIMULATED MODE - skipping ******")
            return
        }

        log.debug("${logPrefix} join parallel test results")
        // ref: https://stackoverflow.com/questions/47268418/how-to-aggregate-test-results-in-jenkins-parallel-pipeline
        for (int i = 1; i <= config.parallelRunCount; i++) {
            config.parallelRunNumber=i

            String testCaseLabel = getTestCaseLabel(config)
            String parallelRunDir = testCaseLabel

            log.debug("${logPrefix} unstash parallelRun ${testCaseLabel}")
            dsl.dir(parallelRunDir) {
                dsl.unstash name: testCaseLabel
            }
            log.debug("${logPrefix} copy parallelRun target data into final without overwrite (cp -npr)")
            if (config.debugPipeline && !config.useSimulationMode) {
                log.debug("${logPrefix} find results from source:")
                runFind("${parallelRunDir}/target")
            }

            dsl.sh "cp -npr ${parallelRunDir}/target ."
            dsl.sh "rm -fr ${parallelRunDir}"

            if (config.debugPipeline && !config.useSimulationMode) {
                log.debug("${logPrefix} find results for target:")
                runFind()
            }

        }

        if (config.debugPipeline && !config.useSimulationMode) {
            log.debug("getParallelRunTestResults(): find final target results:")
            runFind()
        }
    }

    boolean runAcceptanceTest(Map config) {

//        String logPrefix="[${config.testCaseLabel}-${config.nodeId}] runAcceptanceTest():"
        String logPrefix="runAcceptanceTest():"
        log.info("${logPrefix} config.parallelRunNumber=[${config.parallelRunNumber}] started on NODE [${dsl.env.NODE_NAME}] in workspace=[${dsl.env.WORKSPACE}]")

        Map currentRunState = [:]
        currentRunState.result = false
        Date timeStart
        Date timeEnd
        long timeStartMilliseconds
        long timeEndMilliseconds

        initCurrentRunState(config, currentState)

        if (config.startBrowserstackLocalAgent) {
            currentRunState.browserstackLocalIdentifier = config.browserstackLocalIdentifier
        }

        if (config.runBsDiagnostics && config.useBrowserstackLocalAgent && !config.startBrowserstackLocalAgent) {
            config.bsAgentLogDir = "logs"
            config.bsDiagLogFile = "${config.bsAgentLogDir}/bs-diagnostics-${config.testCaseLabel}.log"
            dsl.sh "mkdir -p ${config.bsAgentLogDir}"

            log.info("${logPrefix} checking Bs Agent process status")
            runBsAgentPsCheck(config, true, true)
        }

        log.debug("${logPrefix} pwd=[${dsl.pwd()}]")

        if (config.debugPipeline && !config.useSimulationMode) {
            log.debug("${logPrefix} Pre-test find:")
            if (config.runSingleMvnCmdMode) {
                runFind(".")
            } else {
                runFind()
            }
        }

        boolean result = true
        String summaryFile = "summary.txt"

        log.debug("${logPrefix} getting mvn command")
        String mvnCmd = prepareMvnIntegrationTestCommand(config)
        String mvnCmdMasked = mvnCmd.replaceAll(/(\w+):\/\/(\w+):(\w+)@(\w+)/, '$1://***:***@$4')
        currentRunState.mvnCmd = mvnCmdMasked
        currentRunState.useSimulationMode = config.useSimulationMode

        timeStart = new Date()
        timeStartMilliseconds = System.currentTimeMillis()

        if (config.useSimulationMode) {
            log.info("${logPrefix} **** USING SIMULATION MODE - following command not actually run *****")
            log.info("${logPrefix} mvnCmd=${mvnCmd}")
            dsl.dir("target/site/serenity") {
                dsl.sh("touch ${summaryFile} || true")
                dsl.archiveArtifacts artifacts: summaryFile
            }
            dsl.stash name: pipelineConfig.STASH_NAME_REPORTS, includes: "target/**/summary.txt"
        } else {
            try {
                dsl.withCredentials(credIdList) {
                    log.info("${logPrefix} starting mvnCmd")
                    log.debug("${logPrefix} mvnCmd=${mvnCmd}")
                    int retstat = dsl.sh(script: "${mvnCmd}", returnStatus: true)
                    result = (retstat) ? false : true
                }
            } catch (Exception err) {
                log.error("${logPrefix} mvn exception occurred [${err}]")
                result = false
            }
        }

        timeEnd = new Date()

        currentRunState.timeStart = timeStart.toString()
        currentRunState.timeEnd = timeEnd.toString()

        timeEndMilliseconds = System.currentTimeMillis()
        long milliseconds = timeEndMilliseconds - timeStartMilliseconds

        currentRunState.duration = com.dettonville.api.pipeline.utils.Utilities.getDurationString(milliseconds)
        currentRunState.result = result

        if (config.startBrowserstackLocalAgent) {
//            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runs[config.testCaseLabel] = currentRunState
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runs[config.testCaseLabel] = currentRunState.findAll { it.key != 'mvnCmd' }
        }
        currentState.runs[config.testCaseLabel] = currentState.runs[config.testCaseLabel] + currentRunState

        log.info("${logPrefix} currentRunState=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentRunState)}")

        log.info("${logPrefix} config.parallelRunNumber=[${config.parallelRunNumber}] finished with result = [${result}]")

        if (!config.useSimulationMode) {
            log.debug("${logPrefix} stash name: ${config.testCaseLabel}, includes: 'target/site/**, target/jbehave/**'")
            dsl.stash name: config.testCaseLabel, includes: 'target/site/**, target/jbehave/**'
        }

        if (config.debugPipeline && !config.useSimulationMode) {
            log.debug("${logPrefix} Post-test find:")
            runFind(".", 1)
            runFind()
        }

        if (config.runSingleMvnCmdMode && config.runSerenityRpt) {
            log.info("${logPrefix} archiving serenity results")

            if (!config.useSimulationMode) {
                dsl.junit 'target/site/serenity/*.xml'
            }

            dsl.dir("target/site/serenity") {
                if (dsl.fileExists(summaryFile)) {
                    dsl.archiveArtifacts artifacts: summaryFile
                    log.info("${summaryFile} archived")
                } else {
                    log.warn("${summaryFile} not found")
                }
            }

            if (dsl.fileExists("target/site/serenity/summary.txt")) {
                dsl.stash name: pipelineConfig.STASH_NAME_REPORTS, includes: "target/**/summary.txt"
            }

            dsl.figlet 'Publish Reports'
            if (pipelineConfig.publishSerenityRpt) {
                dsl.publishHTML(target: [allowMissing         : true,
                                         alwaysLinkToLastBuild: true,
                                         keepAll              : true,
                                         reportDir            : 'target/site/serenity',
                                         reportFiles          : 'index.html',
                                         reportName           : pipelineConfig.serenityRptName])
            }

        }

        if (config.publishJbehaveRpt) {
            dsl.publishHTML(target: [allowMissing         : true,
                                     alwaysLinkToLastBuild: true,
                                     keepAll              : true,
                                     reportDir            : 'target/jbehave/view',
                                     reportFiles          : 'reports.html',
                                     reportName           : "${config.jbehaveRptName}-${config.testCaseLabel}"])
        }


        if (config.runBsDiagnostics && config.useBrowserstackLocalAgent && !config.startBrowserstackLocalAgent) {
            log.info("${logPrefix} checking Bs Agent process status")
            runBSCurlTest(config)
            runBsAgentPsCheck(config, true, true)
            archiveBsAgentLogs(config)
        }

        if (config.failFast && !result) {
            dsl.currentBuild.result = 'FAILURE'
//        throw err
        }

        return result
    }


    void initCurrentRunState(Map config, Map currentState) {
        if (config.startBrowserstackLocalAgent) {
            if (!currentState.containsKey("nodes")) {
                log.warn("${logPrefix} unable to find currentState.nodes which should have been created by the bsAgent start method")
                currentState.nodes = [:]
            }
            if (!currentState.nodes.containsKey(dsl.env.NODE_NAME)) {
                log.warn("${logPrefix} unable to find currentState.nodes[${dsl.env.NODE_NAME}] which should have been created by the bsAgent start method")
                currentState.nodes[dsl.env.NODE_NAME] = [:]
                currentState.nodes[dsl.env.NODE_NAME].runs = [:]
            }
            if (!currentState.nodes[dsl.env.NODE_NAME].containsKey("bsLocalAgents")) {
                log.warn("${logPrefix} unable to find currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents which should have been created by the bsAgent start method")
                currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents = [:]
            }
            if (!currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents.containsKey(config.browserstackLocalIdentifier)) {
                log.warn("${logPrefix} unable to find currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}] which should have been created by the bsAgent start method")
                currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier] = [:]
            }
            if (!currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("runs")) {
                currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runs = [:]
            }
        }

        if (!currentState.containsKey("runs")) {
            currentState.runs = [:]
        }
        if (!currentState.runs.containsKey(config.testCaseLabel)) {
            currentState.runs[config.testCaseLabel] = [:]
            currentState.runs[config.testCaseLabel].node = dsl.env.NODE_NAME
            if (config.startBrowserstackLocalAgent) {
                currentState.runs[config.testCaseLabel].browserstackLocalIdentifier = config.browserstackLocalIdentifier
            }
        }

    }

    void runFind(String dir="target", Integer maxDepth=null) {
        try {
            String maxDepthArg = (maxDepth) ? "-maxdepth ${maxDepth}" : ""
//        dsl.sh "find ${dir} -name *.\\*ml -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
//        dsl.sh "find ${dir} -name *.\\*ml -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p\\n\" | sort -k 3,3"
            dsl.sh "pwd"
            dsl.sh "find ${dir} ${maxDepthArg} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p\\n\" | sort -k 3,3"
        } catch (Exception err) {
            log.warn("runFind(${dir}): find exception occurred [${err}]")
        }
    }

    void getResourceFile(String fileName) {
//        def fileContent = dsl.libraryResource fileName
        String fileContent = dsl.libraryResource fileName
        // create a file with fileName
        dsl.writeFile file: "./${fileName}", text: fileContent
    }

    void createEmailableReports(Map config) {
        String debugFlag=(config.debugPipeline) ? "-x" : ""
        if (config.debugPipeline && !config.useSimulationMode) {
            log.debug("find results:")
            runFind()
        }

//    try {
//        dsl.sh "docker pull ${config.pyUtilsImage}"
//    } catch (Exception err) {
//        log.warn("createEmailableReports(): exception: [${err}]")
//    }

        def pyscript="standalone_html.py"

        try {
            dsl.dir('target/site/serenity') {
                def base_url = "${BUILD_URL}Serenity_Reports/"

                def script="premailer-serenity-docker.sh"
                getResourceFile(script)

                def work_dir = dsl.pwd()

                dsl.sh "docker run --rm -v \"${work_dir}\":/data ${config.pageResImage} pageres /data/index.html 1024x768 --filename=\"${config.snapFile}\""

                if (config.sendSerenityRpt) {
                    getResourceFile(pyscript)
                    dsl.sh "docker run --rm -v \"${work_dir}\":/app ${config.pyUtilsImage} python ${pyscript} index.html emailable-0.html"
                    dsl.sh "bash ${debugFlag} ${script} ${config.pyUtilsImage} ${base_url} emailable-0.html ${config.serenityRpt}"
                }
            }

            if (config.sendJbehaveRpt) {
                dsl.dir('target/jbehave/view') {
                    def base_url = "${BUILD_URL}JBehave_Reports/"
                    def script = "premailer-jbehave-docker.sh"
                    getResourceFile(script)
                    getResourceFile(pyscript)

                    dsl.sh "bash ${debugFlag} ${script} ${config.pyUtilsImage} ${pyscript} ${base_url} ${config.jbehaveRpt}"
                }
            }

            if (config.debugPipeline && !config.useSimulationMode) {
                if (config.sendJbehaveRpt || config.sendSerenityRpt) {
                    dsl.sh 'find target -name emailable.\\*.html -type f -printf "%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n"'
                }
                dsl.sh 'find . -name summary.txt -type f -printf "%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n"'
            }

        } catch (Exception err) {
            log.warn("createEmailableReports(): exception: [${err}]")
        }

    }

    def getBSAgent(Map config=this.pipelineConfig) {
        String logPrefix="getBSAgent():"
//        if (config.runBsAgentMethod!='PER_RUN') {
//            logPrefix="[${config.testCaseLabel}-${config.nodeId}] ${logPrefix}"
//        }

        log.debug("${logPrefix} starting")

        if (!dsl.fileExists("${config.bsAgentBinDir}/BrowserStackLocal")) {
            log.info("${logPrefix} binary not found, fetching...")
        } else {
            String bsAgentVersion = dsl.sh(script: "${config.bsAgentBinDir}/BrowserStackLocal --version", returnStdout: true).trim()
            if (!bsAgentVersion.contains(config.bsAgentVersion)) {
                log.info("${logPrefix} binary version ${bsAgentVersion} does not match ${config.bsAgentVersion}, fetching...")
            } else {
                log.info("${logPrefix} agent binary with version ${config.bsAgentVersion} already exists at ${config.bsAgentBinDir}")
                return
            }
        }

        log.info("${logPrefix} getting agent binary")
        dsl.sh "mkdir -p ${config.bsAgentBinDir}"

        log.info("${logPrefix} Fetching Browserstack agent")
        dsl.dir('deploy_configs') {
            dsl.checkout scm: [
                    $class: 'GitSCM',
                    branches: [[name: "master"]],
                    userRemoteConfigs: [[credentialsId: config.jenkinsRepoCredId, url: config.bsAgentDistGitRepo]]
            ]
        }
        String archivePath="deploy_configs/resources/BrowserStackLocal-${config.bsAgentBinType}.zip"

//        dsl.sh 'find deploy_configs -type f'

        dsl.sh "cp -r ${archivePath} ${config.bsAgentBinDir}"

        dsl.sh "unzip -o ${config.bsAgentBinDir}/BrowserStackLocal-${config.bsAgentBinType}.zip -d ${config.bsAgentBinDir}"
        dsl.sh "chmod +x ${config.bsAgentBinDir}/BrowserStackLocal"

        log.info("${logPrefix} Browserstack agent deployed to ${config.bsAgentBinDir}")

        dsl.sh "rm -fr deploy_configs"

        return
    }


//
// ref: https://janmolak.com/jenkins-2-0-pipelines-and-browserstack-bd5a4ed3010d
//
    def withBsLocalAgent(Map config, def actions) {

//        String logPrefix = "[${config.testCaseLabel}-${config.nodeId}] withBsLocalAgent():"
        String logPrefix = "withBsLocalAgent():"
        log.info("${logPrefix} config.browserstackLocalIdentifier=${config.browserstackLocalIdentifier}")
        log.debug("${logPrefix} ***** config=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(config)}")
        log.info("${logPrefix} ***** config.forceBrowserstackProxy=${config.forceBrowserstackProxy}")
        log.info("${logPrefix} ***** config.forceBrowserstackLocalProxy=${config.forceBrowserstackLocalProxy}")

        config.bsPidFile = "${config.bsAgentLogDir}/bsagent-${config.browserstackLocalIdentifier}.pid"
        config.bsLogFile = "${config.bsAgentLogDir}/bsagent-${config.browserstackLocalIdentifier}.log"
//    config.bsDiagLogFile = "${config.bsAgentLogDir}/bs-diagnostics-${config.testCaseLabel}-${config.nodeId}.log"
        config.bsDiagLogFile = "${config.bsAgentLogDir}/bs-diagnostics-${config.browserstackLocalIdentifier}.log"

        if (config.useSimulationMode) {
            try {
                actions()
            } catch (Exception err) {
                log.error("${logPrefix} actions(): exception occurred [${err}]")
                dsl.sh "cat ${config.bsLogFile}"
            }
            return
        }

        if (!currentState.containsKey("nodes")) {
//            log.debug("${logPrefix} initializing currentState.nodes map")
            currentState.nodes = [:]
        }
        if (!currentState.nodes.containsKey(dsl.env.NODE_NAME)) {
//            log.debug("${logPrefix} initializing currentState.nodes[${dsl.env.NODE_NAME}] map")
            currentState.nodes[dsl.env.NODE_NAME] = [:]
        }
        if (!currentState.nodes[dsl.env.NODE_NAME].containsKey("bsLocalAgents")) {
//            log.debug("${logPrefix} initializing currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents map")
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents = [:]
        }
        if (!currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents.containsKey(config.browserstackLocalIdentifier)) {
//            log.debug("${logPrefix} initializing currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}] map")
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier] = [:]
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning = false
        }

        Map currentAgentState = currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]
        log.debug("${logPrefix} started with currentAgentState[node=[${dsl.env.NODE_NAME}], browserstackLocalIdentifier=${config.browserstackLocalIdentifier}]=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentAgentState)}")
        if (currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning == false) {
            log.info("${logPrefix} browserstacklocal agent for ${config.browserstackLocalIdentifier} not running, starting")

            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount = 1

            getBSAgent(config)

            startBSAgent(config)

//        log.info("${logPrefix} starting -> currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}]=${currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]}")
            log.debug("${logPrefix} started AGENT -> currentState.nodes=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentState.nodes)}")

//        log.info("${logPrefix} run find after starting BS agent")
//        dsl.sh "find /var/tmp/${config.jenkinsProjectName} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
            log.info("${logPrefix} browserstacklocal agent for ${config.browserstackLocalIdentifier} started with pid = [${currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid}]")
        } else {
            if (!currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("bsAgentPid")) {
                log.info("${logPrefix} Bs Agent process status assigned but not started yet for ${config.browserstackLocalIdentifier}")

                int bsAgentWaitTime = 5
                int bsAgentMaxWaits = 10
                for (int i = 1; i  <= bsAgentMaxWaits; i++) {
                    log.info("${logPrefix} waiting ${bsAgentWaitTime} seconds for agent to start...")
                    dsl.sleep(time: bsAgentWaitTime, unit: 'SECONDS')
                    if (currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("bsAgentPid")) {
                        log.info("${logPrefix} discovered Bs Agent process started with pid=[${currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid}]")
                        break
                    } else {
                        log.info("${logPrefix} Bs Agent process status not yet started after sleep #${i} for ${config.browserstackLocalIdentifier}")
                    }
                }
                if (!currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("bsAgentPid")) {
                    String message = "${logPrefix} Bs Agent process status assigned but never started within ${bsAgentWaitTime * bsAgentMaxWaits} seconds for ${config.browserstackLocalIdentifier}"
                    log.info("${message}")
                    throw message
                }
            }
            log.info("${logPrefix} checking Bs Agent process status for pid=[${currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid}]")
            runBsAgentPsCheck(config, false, true)
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount += 1
        }

        if (config.debugPipeline) {
            log.info("${logPrefix} run find before running actions()")
            dsl.sh "find ${config.bsAgentLogDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
        }

        try {
            actions()
        } catch (Exception err) {
            log.error("${logPrefix} actions(): exception occurred [${err}]")
            log.info("${logPrefix} checking Bs Agent process status")
            runBsAgentPsCheck(config, true, true)

//        dsl.sh "cat ${config.bsLogFile}"
            dsl.sh "tail -30 ${config.bsLogFile}"

        } finally {
            if (config.debugPipeline) {
                log.debug("${logPrefix} post action find")
                dsl.sh "find ${config.bsAgentLogDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
            }

            if (config.runBSCurlTest || config.runBsDiagnostics) {
                runBSCurlTest(config)
            }

            if (config.runBsDiagnostics) {
                log.info("${logPrefix} Including process info in diagnostics")
                runBsAgentPsCheck(config, true, true)
            }

            if (currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount == 1) {
                if (["PER_RUN", "PER_JOB_RUN"].contains(config.runBsAgentMethod)) {
                    log.info("${logPrefix} last run complete, stopping agent")
                    stopRunningBsAgent(config)
                } else if (config.forceShutdownBsAgent) {
                    log.info("${logPrefix} last run complete, forceably stopping agent")
                    stopRunningBsAgent(config)
                } else {
                    archiveBsAgentLogs(config)
                }
            }

            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount -= 1
            log.debug("${logPrefix} finished -> currentState=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentState)}")

        }
    }

    boolean startBSAgent(Map config) {
//        String logPrefix="[${config.testCaseLabel}-${config.nodeId}] startBSAgent():"
        String logPrefix="startBSAgent():"
        log.info("${logPrefix} starting agent for config.browserstackLocalIdentifier=${config.browserstackLocalIdentifier}")

//        log.debug("${logPrefix} started -> currentState=${JsonUtils.printToJsonString(currentState)}")
        log.debug("${logPrefix} started -> currentState.nodes=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentState.nodes)}")

        if (config.debugPipeline) {
            log.debug("${logPrefix} run find before running actions() on ${config.bsAgentBaseDir}")
            dsl.sh "find ${config.bsAgentBaseDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
        }

        if (config.cleanupOrphanedBsAgents) {
            cleanupOrphanedBsAgents(config)
        }

        String pid
        if (currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]?.bsAgentPid) {
            pid = currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid
            log.debug("${logPrefix} runningAgents pid=[${pid}]")
        } else if (dsl.fileExists(config.bsPidFile)) {
            log.debug("${logPrefix} pid file exists, check if actually running or if its stale")
            pid = dsl.sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid = pid
        }

        if (!currentState.nodes.containsKey(dsl.env.NODE_NAME)) {
            currentState.nodes[dsl.env.NODE_NAME]=[:]
        }

        if (pid!=null) {
            runBsAgentPsCheck(config)
            log.info("${logPrefix} pid file exists and already running -> setting pid on runningAgent map")
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning = true
            return true
        }

        log.debug("${logPrefix} initializing bs agent log directory ${config.bsAgentLogDir}")
        dsl.sh "mkdir -p ${config.bsAgentLogDir}"

        if (config.runBSCurlTest || config.runBsDiagnostics) {
            log.debug("${logPrefix} initializing directory and diagnostics logfile")
            dsl.sh script: "touch ${config.bsDiagLogFile}"

            log.debug("${logPrefix} checking Bs Agent process status across all running agents")
            runBsAgentPsCheck(config, true, true)
        }

        boolean result

        // Prepare the BrowserStackLocal client
        dsl.withCredentials([dsl.usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {

            // Start browserstacklocal agent
//        String browserStackCmd = "BUILD_ID=dontKillMe nohup"
            String browserStackCmdPrefix = "set -o pipefail; PROJECT=${config.jenkinsProjectName} BUILD_ID=dontKillMe nohup ${config.bsAgentBinDir}/"
            String browserStackCmd = "BrowserStackLocal --key ${dsl.env.BS_KEY} "

            // leave all ENV specific settings to runATHEnv wrapper - not to be done here
            if (config.useBrowserstackProxy) {
                if (config.browserstackProxyHost) {
                    browserStackCmd += " --proxy-host ${config.browserstackProxyHost}"
                }
                if (config.browserstackProxyPort) {
                    browserStackCmd += " --proxy-port ${config.browserstackProxyPort}"
                }
                if (config.forceBrowserstackProxy) {
                    browserStackCmd += " --force-proxy"
                }
            }
            if (config.useBrowserstackLocalProxy) {
                browserStackCmd += " --local-proxy-host ${config.browserstackProxyHost} --local-proxy-port ${config.browserstackProxyPort}"
                if (config.forceBrowserstackLocalProxy) {
                    browserStackCmd += " --force-local"
                }
            }

            // ref: https://www.browserstack.com/local-testing
            if (config.browserstackUseIdentifier) {
                browserStackCmd += " --local-identifier ${config.browserstackLocalIdentifier} "
            }

            browserStackCmd += " --verbose 3"
            //browserStackCmd += " --verbose"
//        String browserStackCmdMasked = browserStackCmd.replaceAll(/(\w+) --key (\w+) (\w+)/,'$1 --key *** $3')
            String browserStackCmdMasked = browserStackCmd.replaceAll("${dsl.env.BS_KEY}","***")

            browserStackCmd = "${browserStackCmdPrefix}${browserStackCmd} > ${config.bsLogFile} 2>&1 & echo \$! > ${config.bsPidFile}"

            int retstat = dsl.sh(script: "${browserStackCmd}", returnStatus: true)
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].browserStackCmd = browserStackCmdMasked

            pid = dsl.sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid = pid
            log.info("${logPrefix} process pid=[${pid}] return status = [${retstat}]")

            result = (retstat) ? false : true

            if (result) {
                currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning = true
            } else {
                currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning = false

                log.error("${logPrefix} process start failed for pid=[${pid}]")
                if (dsl.fileExists(config.bsLogFile)) {
                    dsl.sh "tail -30 ${config.bsLogFile}"
                }

                log.error("${logPrefix} checking if running BS process actually exists...")

                log.info("${logPrefix} checking Bs Agent process status across running agent for pid")
                runBsAgentPsCheck(config, false, true)

            }
        }
        return result

    }

    def stopRunningBsAgent(Map config) {
//        String logPrefix = "[${config.testCaseLabel}-${config.nodeId}] stopRunningBsAgent():"
        String logPrefix = "stopRunningBsAgent():"

        log.info("${logPrefix} starting")

        log.debug("${logPrefix} currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}]= ${currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]}")

        String pid
        if (currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]?.bsAgentPid) {
            pid = currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid
            log.info("${logPrefix} runningAgents pid=[${pid}]")
        } else if (config?.bsPidFile) {
            log.info("${logPrefix} pid not found in running agents map, sourcing pid from ${config.bsPidFile}")
            pid = dsl.sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
            log.info("${logPrefix} ${config.bsPidFile} pid=[${pid}]")
        }

        if (config.debugPipeline) {
            log.info("${logPrefix} run find before archive/cleanup")
            dsl.sh "find ${config.bsAgentLogDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
        }

        log.info("${logPrefix} show all BS agent running processes")
        runBsAgentPsCheck(config, true)

//    try {
//        cleanupOrphanedBsAgents(config)
//    } catch (Exception err) {
//        log.info("${logPrefix} exception when cleaning up orphaned BS agents [${err}]")
//    }

        log.info("${logPrefix} stopping browserstacklocal agent execution for ${config.browserstackLocalIdentifier}")
        // Stop the connection
        try {
//        dsl.sh "kill -9 `cat ${config.bsPidFile}`"
            dsl.sh "kill -9 ${pid}"
            currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning = false
        } catch (Exception err) {
            log.error("${logPrefix} kill: browserstack cleanup exception occurred [${err}]")
            dsl.sh "tail -30 ${config.bsLogFile}"
        }

        archiveBsAgentLogs(config)

        if (["PER_RUN", "PER_JOB_RUN"].contains(config.runBsAgentMethod)
                || config.forceShutdownBsAgent)
        {
            log.info("${logPrefix} cleaning up agent bindir and any residue/artifacts for ${config.bsAgentLogDir}")
            dsl.sh "rm -fr ${config.bsAgentLogDir}"
        }

        if (config.forceCleanupBsBaseDir) {
            log.info("${logPrefix} cleaning browserstacklocal agent root dir ${config.bsAgentBaseDir}")
            dsl.sh "rm -fr ${config.bsAgentBaseDir}"
        }
    }

    def runBsAgentPsCheck(Map config, boolean showAllBsAgents = false, boolean writeToBsDiagLog = false) {
//        String logPrefix = "[${config.testCaseLabel}-${config.nodeId}] runBsAgentPsCheck():"
        String logPrefix = "runBsAgentPsCheck():"
        String processInfo
        String processCmd

        log.debug("${logPrefix} started")
        String pid
        if (!showAllBsAgents) {
            log.debug("${logPrefix} currentState.nodes[${dsl.env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}] = ${currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]}")
            if (currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]?.bsAgentPid) {
                pid = currentState.nodes[dsl.env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid
                log.debug("${logPrefix} runningAgents pid=[${pid}]")
            } else if (config?.bsPidFile) {
                log.info("${logPrefix} pid not found in running agents map, sourcing pid from ${config.bsPidFile}")
                pid = dsl.sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
                log.info("${logPrefix} ${config.bsPidFile} pid=[${pid}]")
            }

            log.debug("${logPrefix} checking process status for pid=[${pid}]")
            processCmd = "ps -ef | grep ${pid} | grep -v grep || true"
        } else {
            processCmd = "ps axo pid=,stat=,ppid=,user=,lstart=,command= | grep -v -e grep -e java | grep -i browserstacklocal || true"
        }

        try {
            processInfo = dsl.sh(script: processCmd, returnStdout: true).trim()
//            log.info("${logPrefix} Browserstack local agent already running with pid=[${pid}] processInfo = [${processInfo}]")
        } catch (Exception err) {
            log.warn("${logPrefix} browserstacklocal process does not exists")
            if (dsl.fileExists(config.bsPidFile)) {
                log.warn("${logPrefix} cleaning up pid file ${config.bsPidFile}")
                dsl.sh "rm ${config.bsPidFile}"
            }
        }

        if (processInfo && processInfo!="") {
            dsl.withCredentials([dsl.usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {
                processInfo = processInfo.replaceAll("${dsl.env.BS_KEY}", "***")
            }
        }
        log.debug("${logPrefix} processInfo=[${processInfo}]")

        if (writeToBsDiagLog) {
            dsl.sh script: "echo \"${processCmd}\" >> ${config.bsDiagLogFile}", returnStatus: true
//        dsl.sh script: "echo ${processCmd} >> ${config.bsDiagLogFile}"
            dsl.sh script: "echo Results: >> ${config.bsDiagLogFile}"
            dsl.sh script: "echo \"######\" >> ${config.bsDiagLogFile}"
            dsl.sh script: "echo \"${processInfo}\" >> ${config.bsDiagLogFile}"
            dsl.sh script: "echo \"\" >> ${config.bsDiagLogFile}"
        }

        return processInfo
    }

    def archiveBsAgentLogs(Map config) {
//        String logPrefix = "[${config.testCaseLabel}-${config.nodeId}] archiveBsAgentLogs():"
        String logPrefix = "archiveBsAgentLogs():"

        log.info("${logPrefix} archiving agent log")
        dsl.dir(config.bsAgentLogDir) {
            def logFiles = dsl.findFiles glob: '**/*.log'
            //            def logFiles = dsl.findFiles glob: "**/${config.bsLogFile}"
            if (logFiles.length > 0) {
                //                    dsl.archiveArtifacts artifacts: "${config.bsAgentLogDir}/\\*.log"
                //                dsl.archiveArtifacts artifacts: "*.log"
                //                dsl.archiveArtifacts artifacts: "${config.bsLogFile}"
                //                dsl.archiveArtifacts artifacts: "\\*.log"
                dsl.archiveArtifacts artifacts: "*.log"
                //                dsl.sh "rm \\*.log"
            }
        }
    }

    def cleanupOrphanedBsAgents(Map config) {
        String logPrefix="cleanupOrphanedBsAgents():"
        log.debug("${logPrefix} starting")

        String debugFlag=(config.debugPipeline) ? "-x" : ""
        String script="scripts/cleanup-zombie-process.sh"
        getResourceFile(script)
//    dsl.sh 'find scripts -type f'

        log.debug("${logPrefix} running script ${script} jenkins ${config.jenkinsProjectName} browserstacklocal")
//        dsl.sh "bash ${debugFlag} ${script} jenkins ${config.jenkinsProjectName} browserstacklocal"
        dsl.sh "bash ${script} jenkins ${config.jenkinsProjectName} browserstacklocal"

    }

    def runBSCurlTest(Map config) {
        String logPrefix="runBSCurlTest():"

        dsl.withCredentials([dsl.usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {

            String curlTestCmd = "curl --connect-timeout ${config.curlBSTimeout}"
            String scheme = "https"
            if (config.useBrowserstackLocalProxy) {
                curlTestCmd += " -x ${config.browserstackProxyHost}:${config.browserstackProxyPort}"
//                scheme = "http"
            }
            String curlTestCmdMasked = "${curlTestCmd} -L ${scheme}://****:****@${config.browserstackHubUrl}/wd/hub/status"
            curlTestCmd += " -L ${scheme}://${dsl.env.BS_USER}:${dsl.env.BS_KEY}@${config.browserstackHubUrl}/wd/hub/status"

            try {
                dsl.sh script: "echo ${curlTestCmdMasked} >> ${config.bsDiagLogFile}", returnStatus: true
                dsl.sh script: "echo Results: >> ${config.bsDiagLogFile}", returnStatus: true
                dsl.sh script: "echo \"######\" >> ${config.bsDiagLogFile}", returnStatus: true

                log.info("${logPrefix} browserstack curl test results:")
                dsl.sh script: "set -o pipefail; ${curlTestCmd} 2>&1 | tee -a ${config.bsDiagLogFile}", returnStdout: true
            } catch (Exception err) {
                log.warn("${logPrefix} browserstack curl test exception occurred: [${err}]")
            }
            dsl.sh script: "echo \"\n\n\" >> ${config.bsDiagLogFile}", returnStatus: true

        }
    }

/**
 * Build the Maven Integration Test command line that will be used by automation execution
 **/
    String prepareMvnIntegrationTestCommand(Map config) {
        String logPrefix="prepareMvnIntegrationTestCommand():"
        log.debug("${logPrefix} started")

        String mvnCmd = "${dsl.env.M3}/bin/mvn"

        if (pipelineConfig.runSingleMvnCmdMode && pipelineConfig.parallelRunCount==1) {
            mvnCmd += " clean"
        }
        mvnCmd += " integration-test"

        if (config.useLocalMvnRepo) {
            // ref: https://www.jfrog.com/jira/browse/HAP-896
            // ref: https://stackoverflow.com/questions/47224781/share-local-maven-repository-to-agents-in-jenkins-pipeline
            mvnCmd += " -Dmaven.repo.local=${config.mvnLocalRepoDir}"
        }

        // https://stackoverflow.com/questions/21638697/disable-maven-download-progress-indication
        mvnCmd += " ${config.mvnLogOptions}"
        mvnCmd += " -Denv=${config.appEnvironment}"

        if (config.useExecEnvJenkins) mvnCmd += " -Dexec.env=jenkins"
        if (config.useDryRun) mvnCmd += " -DdryRun"

        if (config.webPlatform.contains("browserstack")) {

//        log.debug("${logPrefix} 1: mvnCmd=${mvnCmd}")
            String scheme = "https"
//            if (config.useBrowserstackProxy) {
//                scheme = "http"
//            }

            mvnCmd+=" -Dbrowserstack.project='${config.browserstackProject}'"
            mvnCmd+=" -Dbrowserstack.build='${config.browserstackBuild}'"
            mvnCmd+=" -Dbrowserstack.name='${config.browserstackName}'"

//        log.debug("${logPrefix} 2: mvnCmd=${mvnCmd}")

            dsl.withCredentials([dsl.usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {
                mvnCmd+=" -Dbrowserstack.hub.url=${scheme}://${dsl.env.BS_USER}:${dsl.env.BS_KEY}@${config.browserstackHubUrl}/wd/hub"
            }

            boolean useMobile = (['iphone','android'].contains(config.browserstackBrowser.toLowerCase())) ? true : false

            if (config.webPlatform.contains("browserstack") && !useMobile) {
//                mvnCmd+=" -Dbrowserstack.resolution=${config.browserstackResolution}"
//                mvnCmd+=" -Dbrowserstack.idleTimeout=${config.browserstackIdleTimeout}"
//                mvnCmd+=" -Dbrowserstack.accept.ssl.certs=${config.browserstackAcceptSSLCerts}"
//                mvnCmd+=" -Dbrowserstack.acceptInsecureCerts=${config.browserstackAcceptInsecureCerts}"
//                mvnCmd+=" -Dbrowserstack.${config.browserstackBrowser.toLowerCase()}.ignore.cert=${config.browserstackChromeIgnoreCert}"
                mvnCmd+=" -Dbrowserstack.web.os='${config.browserstackWebOS}'"
                mvnCmd+=" -Dbrowserstack.web.os.version='${config.browserstackWebOSVersion}'"
                mvnCmd+=" -Dbrowserstack.web.localfileupload=${config.browserstackWebLocalfileupload}"
                mvnCmd+=" -Dbrowserstack.debug=${config.browserstackDebug}"
                mvnCmd+=" -Dbrowserstack.networkLogs=${config.browserstackNetworkLogs}"

                switch (config.browserstackBrowser.toLowerCase()) {
                    case "firefox":
                        mvnCmd += " -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX "
                        mvnCmd += " -Dbrowserstack.firefox.version=${config.browserstackFirefoxVersion}"
                        break
                    case "chrome":
//                        mvnCmd+=" -Dbrowserstack.chrome.ignore.cert=${config.browserstackChromeIgnoreCert}"
                        mvnCmd += " -Dplatform=chrome -Ddefault.web.execution.platform=BROWSERSTACK_CHROME "
                        mvnCmd += " -Dbrowserstack.chrome.version=${config.browserstackChromeVersion}"
                        break
                    case "safari":
                        mvnCmd += " -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI "
                        mvnCmd += " -Dbrowserstack.safari.version=${config.browserstackSafariVersion}"
                        break
                    case "edge":
                        mvnCmd += " -Dplatform=edge -Ddefault.web.execution.platform=BROWSERSTACK_EDGE "
                        mvnCmd += " -Dbrowserstack.edge.version=${config.browserstackEdgeVersion}"
                        break
                    case "ie":
                        mvnCmd += " -Dplatform=edge -Ddefault.web.execution.platform=BROWSERSTACK_IE "
                        mvnCmd += " -Dbrowserstack.edge.version=${config.browserstackIEVersion}"
                        break
                    default: log.warn("unknown browser: ${config.browserstackBrowser}")
                }

//            log.debug("${logPrefix} 3: mvnCmd=${mvnCmd}")

            } else if (useMobile) {

//            " -Dbrowserstack.web.localfileupload=${config.browserstackWebLocalfileupload}" +
//                mvnCmd+=" -Dbrowserstack.accept.ssl.certs=${config.browserstackAcceptSSLCerts}"

                switch (config.browserstackBrowser.toLowerCase()) {
                    case "iphone":
                        config.browserstackWebOSVersion="IOS"
                        mvnCmd += " -Ddefault.mobile.execution.platform=BROWSERSTACK_IPHONE "
                        mvnCmd += " -Dbrowser=iphone -Dplatform=mac "
//                    mvnCmd += " -Dbrowserstack.iphone.device='${config.browserstackMobileDevice}'"
//                    mvnCmd += " -Dbrowserstack.real.iphone=${config.browserstackRealMobile} "
                        break
                    case "android":
                        config.browserstackWebOSVersion="Android"
                        mvnCmd += " -Ddefault.mobile.execution.platform=BROWSERSTACK_ANDROID "
//                    mvnCmd += " -Dbrowserstack.android.device='${config.browserstackMobileDevice}'"
//                    mvnCmd += " -Dbrowserstack.real.android=${config.browserstackRealMobile} "
                        break
                    default: log.warn("unknown mobile browser: ${config.browserstackBrowser}")
                }

//            log.debug("${logPrefix} 4: mvnCmd=${mvnCmd}")
            }

            if (config.useBrowserstackLocalAgent || config.startBrowserstackLocalAgent) {
                mvnCmd += " -Dbrowserstack.local=${config.useBrowserstackLocalAgent}"

                if (config.startBrowserstackLocalAgent && config.browserstackUseIdentifier) {
                    mvnCmd += " -Dbrowserstack.localIdentifier=${config.browserstackLocalIdentifier}"
                }
            }

//        log.debug("${logPrefix} 5: mvnCmd=${mvnCmd}")

            if (config.useBrowserstackProxy) {
                // leave all ENV specific settings to runATHEnv wrapper - not to be done here
                mvnCmd += " -Dbrowserstack.proxy.host=${config.browserstackProxyHost}"
                mvnCmd += " -Dbrowserstack.proxy.port=${config.browserstackProxyPort}"
            } else if (config.useEmptyBrowserstackProxy) {
                mvnCmd += " -Dbrowserstack.proxy.host="
                //mvnCmd+=" -Dbrowserstack.proxy.port="
            }

            // very important for new BS local agent startup method!
            if (config.useBrowserstackLocalAgent
                    && !config.startBrowserstackLocalAgent)
            {
                // leave all ENV specific settings to runATHEnv wrapper - not to be done here
//                mvnCmd += " -DbrowserstackLocal.binpath=${config.bsAgentBinDir}"
                mvnCmd += " -DbrowserstackLocal.useproxy=${config.useBrowserstackLocalProxy}"

//                if (config.useBrowserstackLocalProxy) {
//                    mvnCmd += " -DbrowserstackLocal.proxy.host=${config.browserstackProxyHost}"
//                    mvnCmd += " -DbrowserstackLocal.proxy.port=${config.browserstackProxyPort}"
//                }
            }

//        log.debug("${logPrefix} 6: mvnCmd=${mvnCmd}")

        } else {
            mvnCmd += " -Ddefault.web.execution.platform=${config.webPlatform}"
        }

        if (config?.storyName) mvnCmd += " -DstoryName=${config.storyName}"
        if (config?.metaFilterTags) mvnCmd += " -Dmeta.filter='${config.metaFilterTags}'"
        if (config?.jbehaveExecutionThreads) mvnCmd += " -Djbehave.execution.threads=${config.jbehaveExecutionThreads}"

//    log.debug("${logPrefix} 7: mvnCmd=${mvnCmd}")

        if (config?.parallelRunNumber) {
            if (config.useBatchMode) {
                mvnCmd += " -Dbatch.count=${config.parallelRunCount} -Dbatch.number=${config.parallelRunNumber}"
            } else {
                mvnCmd += " -DparallelStoryRun=${config.parallelRunNumber}"
            }
        }

//    log.debug("${logPrefix} 8: mvnCmd=${mvnCmd}")

        // enable serenity reporting
        // ref: https://fusion.dettonville.int/stash/projects/QE/repos/mtaf-jbehave-tools/browse/src/main/java/com/dettonville/testing/mtaf/jbehave/serenity/SerenitySupport.java?at=refs%2Fheads%2Fmaster
        if (config.runSerenityRpt) mvnCmd += " -DserenityReport=true"
        //skipping the unit test
        mvnCmd += " -DskipTests=${config.skipTests}"
        mvnCmd += " -Dmaven.test.failure.ignore=${config.failureIgnore}"

        if (config.useRallyIntegration) {
            mvnCmd += " '-Djbehave.reporters=RALLY,LOGGING'"
            mvnCmd += " -Denable.rally.proxy=${config.enableRallyProxy}"
            mvnCmd += " -DtestSetName=${config.testSetName}"
            mvnCmd += " -DtestSetId=${config.testSetId}"

            mvnCmd += " -Drally.url=${config.rallyUrl}"

            dsl.withCredentials([dsl.string(credentialsId: config.jenkinsRallyCredId, variable: 'RALLY_KEY')]) {
                mvnCmd += " -Drally.key=${dsl.env.RALLY_KEY}"
            }
            mvnCmd += " -DrallyBuild=${config.rallyBuild}"
            mvnCmd += " -DrallyIntegration=${config.useRallyIntegration}"
            mvnCmd += " -DrallyScreenShotMode=${config.rallyScreenShotMode}"
            mvnCmd += " -DprojectName='${config.rallyProjectName}'"
        }

//    log.debug("${logPrefix} 9: mvnCmd=${mvnCmd}")

        List contextTags = getContextTags(config)
        List injectedTags = getInjectedTags(config)

        if (contextTags.size()>0) {
            mvnCmd += " -Dcontext=${contextTags.join('')}"
        }

        if (injectedTags.size()>0) {
            mvnCmd += " -Dinjected.tags=configuration:${injectedTags.join(':')}"
//        mvnCmd += " -Dinjected.tags=configuration:${injectedTags.join('_')}"
        }

        mvnCmd+=" -e"

        log.debug("${logPrefix} final command: mvnCmd=${mvnCmd}")

        return mvnCmd
    }

    String getBrowserstackBrowserVersion(Map config) {
        switch (config.browserstackBrowser.toLowerCase()) {
            case "firefox":
                return config.browserstackFirefoxVersion
                break
            case "chrome":
                return config.browserstackChromeVersion
                break
            case "safari":
                return config.browserstackSafariVersion
                break
            case "edge":
                return config.browserstackEdgeVersion
                break
            case "ie":
                return config.browserstackIEVersion
                break
            case "iphone":
//            return config.browserstackMobileDevice
                break
            case "android":
//            return config.browserstackMobileDevice
                break
            default:
                log.warn("getBrowserstackBrowserVersion(): unknown browser: ${config.browserstackBrowser}")
                return null
        }

    }

    // get context tags from following test run features
    // OS (Windows/MacOS/IOS)
    // OS version (High Sierra/10/8)
    // Browser (Chrome/Safari/iphone/android)
    // Browser version (11/15/68)
    List getContextTags(Map config) {

        config.browserstackBrowserVersion = getBrowserstackBrowserVersion(config)

        config.batchAndTestTruth = (config.useBatchMode) ? "T" : "F"
        config.batchAndTestTruth += (config?.testGroups) ? "T" : "F"

        switch (config.batchAndTestTruth) {
            case "TT":
                return getAllTestContextFeatures(config)
                break
            case "TF":
                return [config.browserstackBrowser]
                break
            case "FT":
                return getAllTestContextFeatures(config)
                break
            case "FF":
                return getAllTestContextFeatures(config)
                break
            default:
                log.warn("getContextTags(): unknown context state: batchAndTestTruth=${config.batchAndTestTruth}")
                return null
        }
    }

    List getAllTestContextFeatures(Map config) {

        List contextTags = []
        contextTags.add(config.browserstackWebOS)
        contextTags.add(config.browserstackWebOSVersion.replaceAll(' ', '-'))
        contextTags.add(config.browserstackBrowser)

        if (config?.browserstackBrowserVersion) contextTags.add(config.browserstackBrowserVersion)
        if (!config.useBatchMode) contextTags.add("Run${config.parallelRunNumber}")

        return contextTags
    }

    List getInjectedTags(Map config) {
        List injectedTags=[]

        config.browserstackBrowserVersion = getBrowserstackBrowserVersion(config)

        if (!config.useBatchMode || !config?.testGroups) {
            if (config?.componentName) injectedTags.add(config.componentName)
            if (config?.componentVersion) injectedTags.add("release-${config.componentVersion}")
        }
        if (config.useBatchMode && config?.testGroups) {
            injectedTags.add(config.browserstackWebOS)
            injectedTags.add(config.browserstackWebOSVersion.replaceAll(' ', '-'))
        }
        injectedTags.add(config.browserstackBrowser)
        if (config?.browserstackBrowserVersion) injectedTags.add(config.browserstackBrowserVersion)
        if (!config.useBatchMode) injectedTags.add("Run${config.parallelRunNumber}")

        return injectedTags
    }

    void runPostJobHandler(String postJobEventType) {
        String logPrefix = "runPostJobHandler():"
        log.info("${logPrefix} **** post[${postJobEventType}] started")

        if (postJobEventType=="always") {
            updateJobDescription()
        }

        log.debug("${logPrefix} **** post[${postJobEventType}] currentBuild.result=${dsl.currentBuild.result} - sending notification to event subscribed recipients")
        sendReports(postJobEventType)

        if (postJobEventType=="always") {
            log.info("${logPrefix} **** post[${postJobEventType}] cleaning workspace")
            dsl.cleanWs()
        }

        return
    }

    void updateJobDescription() {
        String duration = "${dsl.currentBuild.durationString.replace(' and counting', '')}"
        if (!pipelineConfig) {
            dsl.currentBuild.description = "Test Duration: ${duration}"
            return
        }
        dsl.currentBuild.description = "Test Duration: ${duration}<br>AppEnvironment: ${pipelineConfig.appEnvironment}<br>MetaFilterTags: ${pipelineConfig.metaFilterTags}<br>Browser: ${pipelineConfig.browserstackBrowser}<br>ParallelRunCount: ${pipelineConfig.parallelRunCount}"
        if (pipelineConfig?.testSuiteName) {
            dsl.currentBuild.description = "Test Duration: ${duration}<br>AppEnvironment: ${pipelineConfig.appEnvironment}<br>Test Suite: ${pipelineConfig.testSuiteName}<br>Browser: ${pipelineConfig.browserstackBrowser}<br>ParallelRunCount: ${pipelineConfig.parallelRunCount}"
        }
        if (pipelineConfig?.testGroups) {
            dsl.currentBuild.description += "<br>TestGroups: ${pipelineConfig.testGroups.size()}"
        }
//        if (pipelineConfig?.enableBranchParam) {
//            dsl.currentBuild.description += "<br>ATH Branch: ${pipelineConfig.athGitBranch}"
//        }
        dsl.currentBuild.description += "<br>ATH Branch: ${pipelineConfig.athGitBranch}"
    }

    void sendReports(String postJobEventType) {
        String logPrefix = "sendReports(${postJobEventType}):"

        dsl.unstash name: pipelineConfig.STASH_NAME_REPORTS
        if ( pipelineConfig.createEmailableReports ) {
            if (pipelineConfig.sendJbehaveRpt) {
                dsl.dir("target/jbehave/view") {
                    emailUtils.sendEmailTestReport(pipelineConfig, pipelineConfig.jbehaveRpt, pipelineConfig.jbehaveRptName, postJobEventType, currentState)
                }
            }
            dsl.dir("target/site/serenity/") {
                if (pipelineConfig.sendSerenityRpt) {
                    emailUtils.sendEmailTestReport(pipelineConfig, pipelineConfig.serenityRpt, pipelineConfig.serenityRptName, postJobEventType, currentState)
                }
                if (pipelineConfig.sendSerenitySnapshot) {
//                def baseUrl = "${BUILD_URL}${pipelineConfig.serenityRptName}/"
//                emailUtils.sendEmailRptSnapshot(pipelineConfig, emailDist, "${pipelineConfig.snapFile}.png", pipelineConfig.serenityRptName, baseUrl, notifyAction)
                    emailUtils.sendEmailTestReport(pipelineConfig, "${pipelineConfig.snapFile}.png", pipelineConfig.serenityRptName, postJobEventType, currentState)
                }
            }
        } else {
            dsl.dir("target/site/serenity/") {
                if (pipelineConfig.publishSerenityRpt) {
                    emailUtils.sendEmailTestReport(pipelineConfig, null, pipelineConfig.serenityRptName, postJobEventType, currentState)
                } else {
                    String summaryFile = "summary.txt"
                    if (dsl.fileExists(summaryFile)) {
                        emailUtils.sendEmailTestReport(pipelineConfig, summaryFile, "Test Summary", postJobEventType, currentState)
                    } else {
                        emailUtils.sendEmailTestReport(pipelineConfig, null, null, postJobEventType, currentState)
                    }
                }
            }
        }
        if (postJobEventType=="always") {
            log.info("${logPrefix} **** Sending build status email")
            emailUtils.sendEmailNotification(pipelineConfig, postJobEventType)
        }
    }

    Map updateHistoricalTestResults(Map config) {
        String logPrefix = "updateHistoricalTestResults():"
        log.info("${logPrefix} starting")

        String testResultsFile = config.testResultsHistory

//        def priorBuildInfo = dsl.currentBuild.getPreviousBuild()
//    Integer priorBuildNumber = priorBuildInfo.number
        Integer buildNumber = dsl.currentBuild.number

        log.info("${logPrefix} get current test results")
        Map currentTestResults = jenkinsApiUtils.getTestResults(buildNumber)
        log.debug("${logPrefix} currentTestResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(currentTestResults)}")

        log.info("${logPrefix} get prior results aggregate ${testResultsFile}")
        Map testResults
//    if (getJobArtifact(testResultsFile, priorBuildNumber)==200) {
        if (jenkinsApiUtils.getJobArtifact(testResultsFile)==200) {
            log.info("${logPrefix} ${testResultsFile} retrieved")
            testResults = dsl.readJSON file: testResultsFile
            log.debug("${logPrefix} testResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(testResults)}")
        } else {
            log.info("${logPrefix} prior aggregate results does not exists, creating new one")
            testResults=[:]
            testResults.history=[]
        }

        log.info("${logPrefix} appending current test results to aggregate results")
        if (currentTestResults) {
            testResults.history.add(currentTestResults)
        }

        if (testResults.history.size()>config.maxTestResultsHistory) {
            int startIdx = testResults.history.size()-config.maxTestResultsHistory

            //   testResults.history = testResults.history[startIdx..-1]
            //
            for (int i = 1; i  <= startIdx; i++) {
                testResults.history.remove(0)
            }
            log.info("${logPrefix} history reduced to last ${testResults.history.size()} results")
            log.debug("${logPrefix} truncated testResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(testResults)}")
        }

        def jsonOut = dsl.readJSON text: JsonOutput.toJson(testResults)
        dsl.writeJSON file: testResultsFile, json: jsonOut, pretty: 2

        log.info("${logPrefix} aggregate results saved to ${testResultsFile}")

        dsl.archiveArtifacts artifacts: testResultsFile
        log.info("${logPrefix} ${testResultsFile} archived")

        return testResults

    }

}
