#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.BuildApiUtils
import com.dettonville.api.pipeline.utils.JsonUtils
import groovy.json.JsonOutput

// ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/lastCompletedBuild/api/json?pretty=true
// ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/721/api/json?pretty=true

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

BuildApiUtils buildApiUtils = new BuildApiUtils(this)
JsonUtils jsonUtils = new JsonUtils(this)

//log.info("jsonTxt.getClass()= ${jsonTxt.getClass()}")

//String jobBaseUri = "jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main"

String globalConfigsYmlStr = '''
---
appEnvironments:
    DEV: 
        jenkinsRunTestsLabel: "QA-LINUX"
        athGitBranch: "develop"
        enableRallyProxy: false
        useBrowserstackProxy: true
        useBrowserstackLocalProxy: false
#        changedEmailList: "dcapi_qa@dettonville.com"
        alwaysEmailList: "dcapi_qa@dettonville.com"
        deployJobEnvName: "Dev"
    
    DEV_CLOUD: 
        jenkinsRunTestsLabel: "DEVCLD-LIN7"
        athGitBranch: "develop"
        useBrowserstackProxy: false
        useBrowserstackLocalProxy: false
#        browserstackProxyHost: "ech-10-170-129-105.dettonville.int"
#        browserstackProxyPort: "1080"
#        browserstackProxyHost: "ech-10-170-129-41.dettonville.int"
#        browserstackProxyPort: "80"
#        changedEmailList: "dcapi_qa@dettonville.com"
#        alwaysEmailList: "dcapi_qa@dettonville.com"
        alwaysEmailList: "dcapi_qa@dettonville.com, ljohnson@dettonville.com, SIT-engineer@dettonville.com, conor.dixon@dettonville.com, kedar.mohanty@dettonville.com, prashanth.krishnappa@dettonville.com, sandeep.singh@dettonville.com, jakub.kurtiak@dettonville.com"
        deployJobEnvName: "DevCloud"
    
    STAGE:
        jenkinsRunTestsLabel: "QA-LINUX"
        athGitBranch: "develop"
        useExecEnvJenkins: true
        useBrowserstackProxy: true
        useBrowserstackLocalProxy: false
#        changedEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com"
#        changedEmailList: "dcapi_qa@dettonville.com"
#        alwaysEmailList: "dcapi_qa@dettonville.com"
        alwaysEmailList: "dcapi_qa@dettonville.com, ljohnson@dettonville.com, SIT-engineer@dettonville.com, conor.dixon@dettonville.com, kedar.mohanty@dettonville.com, prashanth.krishnappa@dettonville.com, sandeep.singh@dettonville.com, jakub.kurtiak@dettonville.com"
        deployJobEnvName: "Stage"
    
    STAGE_EXTERNAL:
        jenkinsRunTestsLabel: "DEVCLD-LIN7"
        athGitBranch: "develop"
        useBrowserstackProxy: false
        useBrowserstackLocalProxy: false
        enableRallyProxy: false
#        changedEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com"
#        changedEmailList: "dcapi_qa@dettonville.com"
#        alwaysEmailList: "dcapi_qa@dettonville.com"
        alwaysEmailList: "dcapi_qa@dettonville.com, ljohnson@dettonville.com, SIT-engineer@dettonville.com, conor.dixon@dettonville.com, kedar.mohanty@dettonville.com, prashanth.krishnappa@dettonville.com, sandeep.singh@dettonville.com, jakub.kurtiak@dettonville.com"
        deployJobEnvName: "Stage"

deployConfig:
    jobBaseUri: "jenkins/job/DCAPI/job/DeploymentJobs/job"
    componentList:
        - name: INFRA-Frontend
          deployJobName: DeployFrontend
          branch: main
        - name: INFRA-DevPortal
          deployJobName: DeployDevPortal
          branch: main
        - name: INFRA-OpenAPINotifier
          deployJobName: DeployOpenAPINotifier
          branch: main
buildStatusConfig:
    prettyPrint: true
    getResultsFile: true
'''

Map config = [:]
config.appEnvironment="STAGE"

// set job config settings
Map globalConfigs = readYaml text: globalConfigsYmlStr

Map envSettings = globalConfigs.appEnvironments.get(config.appEnvironment, [:])
Map deployConfig = globalConfigs.deployConfig
Map buildStatusConfig = globalConfigs.buildStatusConfig

// set env configs
config = envSettings + config

// set deploy configs
config = deployConfig + config

// set deploy job configs
config = buildStatusConfig + config

log.info("config=${JsonUtils.printToJsonString(config)}")

Map deployJobDiffResults = config.clone()
List componentDiffResults = []

node ('QA-LINUX || PROD-LINUX') {

    deployConfig.componentList.each { Map component ->
        Map componentConfig = config.findAll { it.key != 'componentList' } + component
//        componentConfig.jobBaseUri = "${config.jobBaseUri}/${component.name}/job/${component.branch}"
        componentConfig.deployJobName += "${componentConfig.deployJobEnvName}"
        componentConfig.jobBaseUri += "/${componentConfig.deployJobName}/job/${componentConfig.branch}"
        componentConfig.buildResultsFileName=componentConfig.deployJobName
        log.debug("componentConfig=${JsonUtils.printToJsonString(componentConfig)}")
        componentDiffResults.add(getBuildDiffs(buildApiUtils, jsonUtils, componentConfig))
    }

    deployJobDiffResults.componentDiffResults = componentDiffResults
    log.info("deployJobDiffResults=${JsonUtils.printToJsonString(deployJobDiffResults)}")

    if (config.getResultsFile) {

        deployJobDiffResults.buildDiffResultsFile = "deployJobs.diffs.json"
        def jsonOut = readJSON text: JsonOutput.toJson(deployJobDiffResults)
        writeJSON file: deployJobDiffResults.buildDiffResultsFile, json: jsonOut, pretty: 4

        archiveArtifacts artifacts: '*.json', onlyIfSuccessful: true
    }
    cleanWs()
}

Map getBuildDiffs(BuildApiUtils buildApiUtils, JsonUtils jsonUtils, Map config) {
    String logPrefix = "${scriptName}->getBuildDiffs():"
    List diffs = []

//    Map buildResults1 = buildApiUtils.getBuildResults(jobBaseUri, "721")
//    Map buildResults2 = buildApiUtils.getBuildResults(jobBaseUri, "722")

    Integer buildNumber1 = buildApiUtils.getBuildNumber(config.jobBaseUri)
    Integer buildNumber2 = buildNumber1 - 1

    Map buildResults1 = buildApiUtils.getBuildResults(buildNumber1, config)
    Map buildResults2 = buildApiUtils.getBuildResults(buildNumber2, config)

    log.info("build results json")

    Boolean isDiff = jsonUtils.isJsonDiff(buildResults1, buildResults2, true)

    log.info("isDiff = ${isDiff}")

//    Map deployJobDiffs = config.clone()
    Map deployJobDiffs = [:]
    deployJobDiffs.isDiff = isDiff
    deployJobDiffs.component = config.name
    deployJobDiffs.jobBaseUri = config.jobBaseUri
    deployJobDiffs.buildNumber1 = buildNumber1
    deployJobDiffs.buildNumber2 = buildNumber2

    diffs = jsonUtils.getJsonDiffs(buildResults1, buildResults2, true)
//    deployJobDiffs.diffs = diffs

    Map diffMap = diffs.collectEntries{ Map diff ->
        [(diff.label):diff.findAll { it.key != 'label' }]
    }
    deployJobDiffs.diffs = diffMap

    log.debug("diffs=${diffs}")

    if (config.getResultsFile && isDiff) {
        deployJobDiffs.buildDiffResultsFile = "${config.name}.diffs.json"
        def jsonOut = readJSON text: JsonOutput.toJson(deployJobDiffs)
        writeJSON file: deployJobDiffs.buildDiffResultsFile, json: jsonOut, pretty: 4
    }

    log.info("deployJobDiffs=${JsonUtils.printToJsonString(deployJobDiffs)}")

    return deployJobDiffs
}

