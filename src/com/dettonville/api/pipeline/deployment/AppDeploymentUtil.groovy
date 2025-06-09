package com.dettonville.api.pipeline.deployment

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.MapMerge

import com.dettonville.api.pipeline.utils.ArtifactApiUtils
import com.dettonville.api.pipeline.utils.JenkinsApiUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.JsonUtils

import com.dettonville.api.pipeline.deployment.EmailUtils

import groovy.json.JsonOutput

import java.time.*

class AppDeploymentUtil implements Serializable {
    private static final long serialVersionUID = 1L

    Logger log = new Logger(this)
    def dsl

    JenkinsApiUtils jenkinsApiUtils
    ArtifactApiUtils artifactApiUtils
    JsonUtils jsonUtils
    EmailUtils emailUtils

    Map pipelineConfig
    Map deployJobParamMap = [
        artifactVersion: [$class: 'StringParameterValue', name: 'ArtifactVersion', value: ""],
        alwaysEmailList: [$class: 'StringParameterValue', name: 'AlwaysEmailList', value: ""],
        appComponentBranch: [$class: 'StringParameterValue', name: 'AppComponentBranch', value: ""],
        runPostDeployTests: [$class: 'BooleanParameterValue', name: 'RunPostDeployTests', value: false],
        useSimulationMode: [$class: 'BooleanParameterValue', name: 'UseSimulationMode', value: false],
        debugPipeline: [$class: 'BooleanParameterValue', name: 'DebugPipeline', value: false],
        debugReleaseScript: [$class: 'BooleanParameterValue', name: 'DebugReleaseScript', value: false],
        logLevel: [$class: 'StringParameterValue', name: 'LogLevel', value: ""]
    ]

    Map testJobParamMap = [
        alwaysEmailList: [$class: 'StringParameterValue', name: 'AlwaysEmailList', value: ""],
        changedEmailList: [$class: 'StringParameterValue', name: 'ChangedEmailList', value: ""]
    ]

    boolean deploymentResults = false

    Map currentState = [:]

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    AppDeploymentUtil(def dsl) {
        this.dsl = dsl

        Logger.init(this.dsl, LogLevel.INFO)
        this.jenkinsApiUtils = new JenkinsApiUtils(dsl)
        this.artifactApiUtils = new ArtifactApiUtils(dsl)
        this.emailUtils = new EmailUtils(dsl)

        log.info("Loading Default Configs")
        deploymentResults = false

    }

    def runAll(Map params) {

        dsl.stage("Initialize Pipeline") {
            log.info("Initailizing pipeline settings")
            runPreDeploymentSteps(params)
        }

        dsl.stage("Deploy Application Component(s)") {
            runAppDeployment()
        }

        dsl.stage("Run Post-Deployment Tests") {
            runPostDeploymentSteps()
        }

    }

    Map runPreDeploymentSteps(Map params) {
        log.debug("started")

        initPipeline(params)

        return this.pipelineConfig
    }


    Map initPipeline(Map params) {
        dsl.cleanWs()

        this.pipelineConfig=loadPipelineConfig(params)
        log.debug("initial pipelineConfig=${JsonUtils.printToJsonString(pipelineConfig)}")

        log.debug("NODE_NAME = ${dsl.env.NODE_NAME}")
        initCurrentRunState()

        initJobCause()

        updateJobDescription()

        return this.pipelineConfig
    }

    Map getDefaultSettings() {
        // get default job config settings
        return PipelineDefaults.getDefaultSettings(dsl)
    }

    Map loadPipelineConfig(Map params) {
        log.debug("starting...")

        // set job config settings
        Map defaultSettings = getDefaultSettings()
        Map config=defaultSettings.pipeline

        log.info("params=${JsonUtils.printToJsonString(params)}")
        // copy immutable params maps to mutable config map
        config = MapMerge.merge(config, params)

        log.debug("config after applying params=${JsonUtils.printToJsonString(config)}")

        log.setLevel(config.logLevel)

        jenkinsApiUtils.setJenkinsApiCredId(config.jenkinsApiCredId)

        if (config.debugPipeline) {
//            log.setLevel(LogLevel.DEBUG)
            log.debug("**********************")
            log.debug("pipeline env variables:")
            dsl.sh('printenv | sort')
            log.debug("**********************")
        }

        config.get('deployGroup',[])
        config.deployGroupMap = [:]

        config.deployGroup.each { String componentId ->
            Map componentConfig = config.appComponents[componentId]
            config.deployGroupMap[componentId] = componentConfig
        }

        if (!config.appComponents.containsKey(config.appComponentSet)) {
            config.isGroupJob = true
        }

        if (!config.isGroupJob) {
            log.info("merging single component config to pipeline root config")
            String componentId = config.deployGroup[0]
            Map componentConfig = config.appComponents[componentId]
            componentConfig.componentId = componentId
            config = MapMerge.merge(config, componentConfig)
            config.isSingleComponentDeploy = true
            config.remove("appComponents")
        }

        if (!config?.deployJobBaseUri) {
            List jobParts = dsl.env.JOB_NAME.split("/")
            List deployJobBaseParts=[]
            for (int i = 0; i < config.jobBaseFolderLevel; i++) {
                deployJobBaseParts.add(jobParts[i])
            }
            //    log.info("appDeployStrategyParts=${appDeployStrategyParts}")
            config.deployJobBaseUri=deployJobBaseParts.join("/")
        }

        log.info("final config=${JsonUtils.printToJsonString(config)}")

        return config
    }

    void runPostDeploymentSteps() {

        if (pipelineConfig.runPostDeployTests) {
            log.info("Running Tests")
            runTests()
        } else {
            log.info("Skipping Post Deployment Tests")
            currentState.testStatus = "SKIPPED"
        }

        setPipelineStatus()

        def logFiles = dsl.findFiles glob: '**/*.log'
        if (logFiles.length > 0) {
//            dsl.archiveArtifacts artifacts: "\\*.log"
            dsl.archiveArtifacts artifacts: "*.log"
        }

        dsl.cleanWs()
    }

    void runAppDeployment(Map params) {
        dsl.cleanWs()

//        if (pipelineConfig.deployGroup.size()>1) {
        if (pipelineConfig.isGroupJob) {
            log.info("Deploying ${pipelineConfig.deployGroup.size()} Components")
            runAppGroupDeployment()
        } else if (pipelineConfig.deployGroup.size()==1) {
            log.info("Deploy app component [${pipelineConfig.componentId}] using ARA release")

            def agentLabelRunAraDeploy = getJenkinsAgentLabel(pipelineConfig.jenkinsRunAraDeployLabel)
            dsl.node(agentLabelRunAraDeploy as String) {  // Evaluate the node label later
                dsl.cleanWs()
                runAraRelease()
                dsl.cleanWs()
            }

        } else {
            log.warn("No components defined in config.deployGroup")
            log.info("Skipping Post Deployment Tests")
            currentState.deployStatus = "SKIPPED"
        }

    }

    void setPipelineStatus() {

        log.info("finished: currentState.deployResults=${currentState.deployResults}")
        log.info("finished: currentState.testResults=${currentState.testResults}")

        if (pipelineConfig.useSimulationMode) currentState.useSimulationMode = pipelineConfig.useSimulationMode
        currentState.result = currentState.deployResults && currentState.testResults
        currentState.duration = "${dsl.currentBuild.durationString.replace(' and counting', '')}"

        log.info("finished: currentState.result = ${currentState.result}")
        log.info("finished: currentState=${JsonUtils.printToJsonString(currentState)}")

        log.info("save currentState to ${pipelineConfig.jobResultsFile}")

        def jsonOut = dsl.readJSON text: JsonOutput.toJson(currentState)
        dsl.writeJSON file: pipelineConfig.jobResultsFile, json: jsonOut, pretty: 4
        dsl.archiveArtifacts artifacts: pipelineConfig.jobResultsFile

        log.info("Set Pipeline Status")

        dsl.currentBuild.result = (currentState.result) ? 'SUCCESS' : 'FAILURE'
        log.info("**** dsl.currentBuild.result=${dsl.currentBuild.result}")

        updateJobDescription()
    }

    /**
     * Initiate a deployment via ARA
     * @param envSpecFile path to the environment spec file in Biz Ops Git repo. Contains environment instance specific information.
     * @param releaseSpecFile path to the release spec file in Biz Ops Git repo. Contains release specific version and component information.
     * @param workflow The name of the release workflow to execute.
     * @param version The version number of the application to be deployed.
     */
    void runAraRelease() {
        String araEnvSpecFile = pipelineConfig.araEnvSpecFile
        String araReleaseSpecFile = pipelineConfig.araReleaseSpecFile
        String araWorkflow = pipelineConfig.araWorkflow
        String artifactVersion = pipelineConfig.artifactVersion
        log.info("started")
        boolean result = false

        log.debug("araEnvSpecFile=${araEnvSpecFile}")
        log.debug("araReleaseSpecFile=${araReleaseSpecFile}")
        log.debug("araWorkflow=${araWorkflow}")
        log.debug("artifactVersion=${artifactVersion}")

        Map currentRunState = [:]
        currentRunState.id = pipelineConfig.componentId
        currentRunState.appComponentBranch = pipelineConfig.appComponentBranch
        currentRunState.result = true

        currentRunState.araEnvSpecFile=araEnvSpecFile
        currentRunState.araReleaseSpecFile=araReleaseSpecFile
        currentRunState.araWorkflow=araWorkflow
        currentRunState.artifactVersion=artifactVersion

        if (pipelineConfig.isSingleComponentDeploy) {
            log.info("Getting Deploy Artifact Revision")
            pipelineConfig.componentVersion = pipelineConfig.artifactVersion
            Map latestArtifactVersionInfo = artifactApiUtils.getLatestArtifactVersion(pipelineConfig)
            currentRunState.latestArtifactVersionInfo = latestArtifactVersionInfo
            currentRunState.fileVersion = latestArtifactVersionInfo.fileVersion
            currentRunState.artifactUrl = latestArtifactVersionInfo.artifactUrl
        }

        Date timeStart
        Date timeEnd
        long timeStartMilliseconds
        long timeEndMilliseconds

        timeStart = new Date()
        timeStartMilliseconds = System.currentTimeMillis()

//        String releaseCliUrl = dsl.env.ARA_CLI
        String releaseCliUrl = pipelineConfig.araClientUrl

        log.info("Downloading ARA CLI from ${releaseCliUrl}")
        dsl.sh "/usr/bin/curl '${releaseCliUrl}' | tar -x"
        if (!dsl.fileExists('release.sh')) {
            dsl.error "${logPrefix} Release CLI script not found.  Please ensure that the script exists at ${releaseCliUrl}"
        }

        String environmentSpec
        String releaseSpec

        log.info("Cloning ARA spec files from DFS Biz Ops repository")

        dsl.dir('ara-spec-files') {
            dsl.checkout scm: [
                    $class           : 'GitSCM',
                    branches         : [[name: 'master']],
                    userRemoteConfigs: [[credentialsId: pipelineConfig.jenkinsRepoCredId, url: pipelineConfig.araReleaseSpecUrl]]
            ]

            if (pipelineConfig.debugPipeline) {
                List araEnvSpecFileDirList = araEnvSpecFile.split("/")
                araEnvSpecFileDirList.remove(araEnvSpecFileDirList.size() - 1);
                String araEnvSpecFileDir = araEnvSpecFileDirList.join("/")

                log.info("find results for dir [${araEnvSpecFileDir}]:")
                runFind(araEnvSpecFileDir)
            }

            log.debug("Reading env file ${araEnvSpecFile}")
            environmentSpec = dsl.readFile araEnvSpecFile

            log.debug("Reading spec file ${araReleaseSpecFile}")
            releaseSpec = dsl.readFile file: "${araReleaseSpecFile}"

            log.debug("Replacing application version in the release spec with ${artifactVersion}")
            releaseSpec = releaseSpec.replaceAll("(?m)^.*\"version\".*\$", "\"version\": \"${artifactVersion}\", ")
        }

        result = true

        if (!releaseSpec) {
            dsl.error "${logPrefix} Release Spec script not found for ${araReleaseSpecFile}."
        }

        String releaseParams = """{
              \"env_spec\": ${environmentSpec},
              \"rel_spec\": ${releaseSpec}
          }"""

        log.info("Initiating release")

        String releaseCmd = ""
        if (pipelineConfig.debugReleaseScript) {
            releaseCmd += "bash -x "
        }

        releaseCmd += "./release.sh"
        releaseCmd += " --api ${pipelineConfig.araApiURL}"
        releaseCmd += " --action '${araWorkflow}'"
        releaseCmd += " --params '${releaseParams}'"

        currentRunState.releaseCmd = releaseCmd
        if (pipelineConfig.useSimulationMode) {
            log.info("**** USING SIMULATION MODE - following command not actually run *****")
            log.info("releaseCmd=${releaseCmd}")
            currentRunState.useSimulationMode=pipelineConfig.useSimulationMode
        } else {
            log.info("releaseCmd=${releaseCmd}")
            dsl.withCredentials([[$class: 'UsernamePasswordMultiBinding',
                                  credentialsId   : pipelineConfig.jenkinsAraCredId,
                                  usernameVariable: 'ARA_USER',
                                  passwordVariable: 'ARA_PASSWORD']])
            {
                try {
//                    dsl.sh "${releaseCmd}"
//                    result = true
                    int retstat = dsl.sh(script: "${releaseCmd}", returnStatus: true)
                    result = (retstat) ? false : true
                } catch (err) {
                    log.error("The deployment failed: ${err}")
                    result = false
                }
            }
        }

        timeEnd = new Date()
        currentRunState.timeStart = timeStart.toString()
        currentRunState.timeEnd = timeEnd.toString()
        timeEndMilliseconds = System.currentTimeMillis()

        long milliseconds = timeEndMilliseconds - timeStartMilliseconds
        currentRunState.duration = Utilities.getDurationString(milliseconds)

        currentState.deployResults = result
        currentState.deployStatus = (result) ? "SUCCESS" : "FAILED"

        currentState.deployJobs[pipelineConfig.componentId] = currentRunState

        def jsonOut = dsl.readJSON text: JsonOutput.toJson(currentRunState)
        dsl.writeJSON file: pipelineConfig.deployResultsFile, json: jsonOut, pretty: 4
        dsl.archiveArtifacts artifacts: pipelineConfig.deployResultsFile

        log.debug("finished: result = ${result}")

        return result
    }

    def runAppGroupDeployment() {
        log.info("started")

        List deployResults = []
        Map parallelJobs = [:]

        pipelineConfig.deployGroupMap.eachWithIndex { String componentId, Map component, i ->
            component.id = componentId
            component.name = "${pipelineConfig.jenkinsProjectName}-${componentId}"

            Map jobConfig = pipelineConfig.findAll { !["deployGroupMap","appComponents"].contains(it.key) } + component
            jobConfig.jobId = createJobId(jobConfig, i)

            jobConfig.jobName = "${jobConfig.deployJobBaseUri}/${jobConfig.appEnvironment}/${jobConfig.deployJobName}"

            log.debug("jobConfig=${JsonUtils.printToJsonString(jobConfig)}")

            parallelJobs["split-${jobConfig.jobId}"] = {
                deployResults.add(runDeployJob(jobConfig))
            }

        }

        if (parallelJobs.size()>0) {
            log.info("parallelJobs=${parallelJobs}")
            dsl.parallel parallelJobs
        } else {
            log.warn("no component jobs found!")
        }

        log.debug("deployResults=${deployResults}")

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        boolean result = (deployResults.size()>0) ? deployResults.inject { a, b -> a && b } : true

        currentState.deployResults = result
        currentState.deployStatus = (result) ? "SUCCESS" : "FAILED"

        log.info("finished: result = ${result}")
        return result
    }

    void initCurrentRunState() {
        if (!currentState.containsKey("deployJobs")) {
            currentState.deployJobs = [:]
        }
        if (!currentState.containsKey("testJobs")) {
            currentState.testJobs = [:]
        }

        currentState.deployResults = true
        currentState.testResults = true
    }

    String createJobId(Map config, i) {
        return (config?.jobId) ? "${config.jobId}.${(i+1).toString().padLeft(2,'0')}" : "job-${(i+1).toString().padLeft(2,'0')}"
    }

    boolean runDeployJob(Map config) {

        // This will copy all files packaged in STASH_NAME to agent workspace root directory.
        // To copy to another agent directory, see [https://github.com/jenkinsci/pipeline-examples]
        log.debug("started")

        boolean result = false
        List paramList=[]

        log.debug("Setting child job post deployments to false")
        config.runPostDeployTests = false

        deployJobParamMap.each { String key, Map param ->
            if (config.containsKey(key)) {
                param.value = config[key]
                paramList.add(param)
            }
        }

        log.debug("paramList=${paramList}")

        Map currentRunState = [:]
        currentRunState.id = config.id
        currentRunState.name = config.name
        currentRunState.deployJobName = config.deployJobName
        currentRunState.appComponentBranch = config.appComponentBranch
        currentRunState.jobName = config.jobName
        currentRunState.result = true

        log.debug("starting job ${config.jobName}")

        Date timeStart
        Date timeEnd
        long timeStartMilliseconds
        long timeEndMilliseconds

        timeStart = new Date()
        timeStartMilliseconds = System.currentTimeMillis()

        try {

            log.info("config.jobName=[${config.jobName} paramList=${paramList}")

            // ref: http://jenkins-ci.361315.n4.nabble.com/How-to-get-build-results-from-a-build-job-in-a-pipeline-td4897887.html
            // https://javadoc.jenkins.io/plugin/workflow-support/org/jenkinsci/plugins/workflow/support/steps/build/RunWrapper.html
            def jobBuild = dsl.build job: config.jobName, parameters: paramList, wait: true, propagate: !config.continueIfFailed

            log.info("jobBuild=${jobBuild}")

            String jobResult = jobBuild.getResult()
            currentRunState.jobRunUrl = jobBuild.getAbsoluteUrl()
            currentRunState.jobResult = jobResult

            log.info("Build ${config.jobName} returned result: ${jobResult}")

            if (jobResult == 'SUCCESS') {
                result = true
            } else {
                result = false
                if (config.failFast) {
                    dsl.currentBuild.result = 'FAILURE'
                    log.error("${logPrefix}: job failed with result: ${jobResult}")
                    dsl.error("${logPrefix}: job failed with result: ${jobResult}")
                }
            }

        } catch (Exception err) {
            log.error("job exception occurred [${err}]")
            result = false
            if (config.failFast) {
                dsl.currentBuild.result = 'FAILURE'
                throw err
            }
        }

        timeEnd = new Date()
        currentRunState.timeStart = timeStart.toString()
        currentRunState.timeEnd = timeEnd.toString()
        timeEndMilliseconds = System.currentTimeMillis()

        if (timeStartMilliseconds) {
            long milliseconds = timeEndMilliseconds - timeStartMilliseconds
            currentRunState.duration = Utilities.getDurationString(milliseconds)
        }

        log.info("currentRunState=${JsonUtils.printToJsonString(currentRunState)}")

        currentRunState.result = result
        currentState.deployJobs[config.id] = currentRunState

        if (config.failFast && !result) {
            dsl.currentBuild.result = 'FAILURE'
        }

        log.info("finished with result = ${result}")

        return result
    }

    boolean runTests() {
        log.info("started")

        List testResults = []

        pipelineConfig.postDeployTests.eachWithIndex { Map component, i ->

            log.info("component=${JsonUtils.printToJsonString(component)}")

            Map jobConfig = pipelineConfig.findAll { it.key != 'postDeployTests' } + component
            jobConfig.jobId = createJobId(jobConfig, i)

            jobConfig.jobName = "${jobConfig.testJobBaseUri}/${jobConfig.appTestEnvironment}/${jobConfig.target}"

            log.debug("jobConfig=${JsonUtils.printToJsonString(jobConfig)}")
            log.info("jobName=${jobConfig.jobName}")

            testResults.add(runTestJob(jobConfig))
        }

        // ref: https://stackoverflow.com/questions/18380667/join-list-of-boolean-elements-groovy
        boolean result = (testResults.size()>0) ? testResults.inject { a, b -> a && b } : true

        currentState.testResults = result
        currentState.testStatus = (result) ? "SUCCESS" : "FAILED"

        log.debug("finished: result = ${result}")

        return result
    }

    boolean runTestJob(Map config) {
        log.info("started")

        boolean result = false
        List paramList=[]

        testJobParamMap.each { String key, Map param ->
            if (config.containsKey(key)) {
                param.value = config[key]
                paramList.add(param)
            }
        }
        log.info("paramList=${paramList}")

        Map currentRunState = [:]
        currentRunState.target = config.target
        currentRunState.jobName = config.jobName
        currentRunState.result = true

        log.info("starting job ${config.jobName}")

        if (config.useSimulationMode) {
            log.info("***** RUNNING SIMULATED MODE - skipping ******")
            currentRunState.useSimulationMode=pipelineConfig.useSimulationMode
            currentState.testJobs[config.target] = currentRunState
            return true
        }

        Date timeStart
        Date timeEnd
        long timeStartMilliseconds
        long timeEndMilliseconds

        timeStart = new Date()
        timeStartMilliseconds = System.currentTimeMillis()

        try {

            // ref: http://jenkins-ci.361315.n4.nabble.com/How-to-get-build-results-from-a-build-job-in-a-pipeline-td4897887.html
            // https://javadoc.jenkins.io/plugin/workflow-support/org/jenkinsci/plugins/workflow/support/steps/build/RunWrapper.html
            def jobBuild = dsl.build job: config.jobName, parameters: paramList, wait: true, propagate: !config.continueIfFailed

            def jobResult = jobBuild.getResult()
            currentRunState.jobRunUrl = jobBuild.getAbsoluteUrl()
            currentRunState.jobResult = jobResult

            log.info("Build ${config.jobName} returned result: ${jobResult}")

            if (jobResult == 'SUCCESS') {
                result = true
            } else {
                result = false
                if (config.failFast) {
                    dsl.currentBuild.result = 'FAILURE'
                    log.error("${logPrefix}: job failed with result: ${jobResult}")
                    dsl.error("${logPrefix}: job failed with result: ${jobResult}")
                }
            }
        } catch (Exception err) {
            log.error("job exception occurred [${err}]")

            result = false
            if (config.failFast) {
                dsl.currentBuild.result = 'FAILURE'
                throw err
            }
        }

        timeEnd = new Date()
        currentRunState.timeStart = timeStart.toString()
        currentRunState.timeEnd = timeEnd.toString()
        timeEndMilliseconds = System.currentTimeMillis()

        long milliseconds = timeEndMilliseconds - timeStartMilliseconds
        currentRunState.duration = Utilities.getDurationString(milliseconds)
        currentRunState.result = result

        log.info("currentRunState=${JsonUtils.printToJsonString(currentRunState)}")

        currentState.testJobs[config.target] = currentRunState

        if (config.failFast && !result) {
            dsl.currentBuild.result = 'FAILURE'
        }

        log.info("finished with result = ${result}")

        return result
    }


    def initJobCause() {
        log.debug("initialize job cause info")

        pipelineConfig.jobCauseMap = [:]

        if (pipelineConfig.getJobCause) {
            log.info("getting job cause info")
            try {
                Map jobCauseMap = jenkinsApiUtils.getCurrentJobCauseInfo()
                log.info("jobCauseMap=${JsonUtils.printToJsonString(jobCauseMap)}")
                pipelineConfig.jobCauseMap = jobCauseMap
                pipelineConfig.jobCause = (pipelineConfig.jobCauseMap?.shortDescription) ? pipelineConfig.jobCauseMap.shortDescription : ""
                log.info("pipelineConfig.jobCause=${pipelineConfig.jobCause}")

            } catch (Exception err) {
                log.error("exception occurred getting job cause info: [${err}]")
            }
        }
    }

    void runFind(String dir=".", Integer maxDepth=null) {
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

    String getJenkinsAgentLabel(String jenkinsLabel) {
        // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
        return "${-> dsl.println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
    }

    void runPostJobHandler(String postJobEventType) {
        log.info("started")

        if (postJobEventType=="always") {
            updateJobDescription()
        }

        log.debug("currentBuild.result=${dsl.currentBuild.result} - sending notification to event subscribed recipients")
        emailUtils.sendEmailNotification(pipelineConfig, postJobEventType)

        if (postJobEventType=="always") {
            log.info("cleaning workspace")
            try {
                dsl.cleanWs()
            } catch (Exception err) {
                log.warn("cleanWs() exception occurred [${err}]")
            }
        }

        return
    }

    void updateJobDescription() {
        String duration = "${dsl.currentBuild.durationString.replace(' and counting', '')}"
        String jobDescription = "Deployment Status: ${currentState.deployStatus}"
        jobDescription += "<br>Test Status: ${currentState.testStatus}"
        if (pipelineConfig) {
            jobDescription += "<br>AppEnvironment: ${pipelineConfig.appTestEnvironment}"
        }
        dsl.currentBuild.description = jobDescription
    }

}
