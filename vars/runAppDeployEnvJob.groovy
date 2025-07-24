#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map inConfig=[:]) {

    Map config = inConfig.clone()

    config.get('jobMode', true)

    if (config.jobMode) {
        List jobParts = JOB_NAME.split("/")
        log.info("jobParts=${jobParts}")

        config.jobBaseFolderLevel = config.jobBaseFolderLevel ?: 2

        int startIdx=config.jobBaseFolderLevel+1
        int endIdx=jobParts.size()-1

        config.appEnvironment=jobParts[config.jobBaseFolderLevel].toUpperCase()
        config.appComponentSet=jobParts[-1]

        //    String[] testSuiteParts = jobParts[startIdx..<jobParts.size()-1]
        List appDeployStrategyParts=[]
        for (int i = startIdx; i < endIdx; i++) {
            appDeployStrategyParts.add(jobParts[i])
        }

        //    echo "${logPrefix} appDeployStrategyParts=${appDeployStrategyParts}"
        config.appDeployStrategy=appDeployStrategyParts.join("/")

        log.debug("config(1)=${JsonUtils.printToJsonString(config)}")
    }

    if (config.appComponentSet.contains("SELECTED")) {
        config = getSelectableDeploymentJobParams(config)
    } else {
        config = getDeploymentJobParams(config)
    }

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAppDeployEnv(config)

}

Map getDeploymentJobParams(Map inConfig) {

    List enabledParamListDefault = ['artifactVersion','runPostDeployTests','useSimulationMode','debugReleaseScript']
//    List enabledParamListDefault = ['artifactVersion','runPostDeployTests']

    Map config = inConfig.clone()
    config.isGroupJob = false

    config.get('enabledParamList', enabledParamListDefault)
    config.get('enableDevParams', (config.enabledParamList.isEmpty()) ? true : false)

    String configYmlString = """
---
deployGroups:
    ALL:
        - Frontend
        - DevPortal
        - OpenAPINotifier

    BACKENDS:
        - DevPortal
        - OpenAPINotifier

appComponents:
    Frontend:
        appComponentRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/devzone-frontend.git"

    DevPortal:
        appComponentRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/devportal.git"

    OpenAPINotifier:
        appComponentRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/encryption-key-services.git"


"""

    List paramList = []

//    node ("QA-LINUX || PROD-LINUX") {
    node {

        Map ymlConfigMap = readYaml text: configYmlString

        Map appComponents = ymlConfigMap.appComponents
        Map deployGroups = ymlConfigMap.deployGroups

        config.appComponents = appComponents

        List appComponentSetList = []
        appComponentSetList.addAll(deployGroups.keySet() as List)
        appComponentSetList.addAll(appComponents.keySet() as List)

        List appEnvironmentList = ["DEV","STAGE","PROD_STL", "PROD_KSC"]

        List LOGLEVELS = ["INFO","DEBUG","WARN","ERROR"]

        String artifactVersionDefault = config.getOrDefault("artifactVersion", "")
        String alwaysEmailListDefault = config.getOrDefault("alwaysEmailList", "")
        boolean runPostDeployTestsDefault = config.getOrDefault("runPostDeployTests", true)
        boolean useSimulationModeDefault = config.getOrDefault("useSimulationMode", false)
        boolean debugPipelineDefault = config.getOrDefault("debugPipeline", false)
        boolean debugReleaseScriptDefault = config.getOrDefault("debugReleaseScript", false)

        String artifactVersionDescription = "Recommended to leave this blank, as it will then use the configuration setting for each env in "
        artifactVersionDescription += " <a href=\"https://fusion.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATHEnv.groovy\">runAppDeployEnv yml</a>"

        Map paramMap = [
                appEnvironment: choice(choices: appEnvironmentList.join('\n'), description: "Choose App Environment", name: 'AppEnvironment'),
                appComponentSet: choice(choices: appComponentSetList.join('\n'), description: "Choose App Component Set to Deploy", name: 'AppComponentSet'),
                artifactVersion: string(defaultValue: artifactVersionDefault, description: artifactVersionDescription, name: 'ArtifactVersion'),
                alwaysEmailList: string(defaultValue: alwaysEmailListDefault, description: "Specify always email dist", name: 'AlwaysEmailList'),
                runPostDeployTests: booleanParam(defaultValue: runPostDeployTestsDefault, name: 'RunPostDeployTests'),
                useSimulationMode: booleanParam(defaultValue: useSimulationModeDefault, description: "Use Simulation Mode?", name: 'UseSimulationMode'),
                debugPipeline: booleanParam(defaultValue: debugPipelineDefault, description: "Debug Pipeline?", name: 'DebugPipeline'),
                debugReleaseScript: booleanParam(defaultValue: debugReleaseScriptDefault, description: "Debug ARA Release Script?", name: 'DebugReleaseScript'),
                logLevel: choice(choices: LOGLEVELS.join('\n'), description: "Choose Log Level", name: 'LogLevel')
        ]

        log.debug("paramMap=${JsonUtils.printToJsonString(paramMap)}")

        if (config.enableDevParams) {
            config.enabledParamList = paramMap.keySet() as List
        }

        if (config.jobMode) {

            config.deployGroup = deployGroups.getOrDefault(config.appComponentSet, [config.appComponentSet])

            // if jobMode then config.appComponentSet has been derived from jobParts in the JOB_NAME
            if (config.deployGroup.size() == 1 &&
                appComponents.containsKey(config.appComponentSet))
            {
                Map componentSettings = appComponents[config.appComponentSet]
                componentSettings.id = config.appComponentSet

                if (config.enabledParamList.contains("AppComponentBranch")) {
                    String defaultBranch = (config.appEnvironment == "PROD") ? "main" : "develop"
                    List branchList = Utilities.getRepoBranchList(this, componentSettings.appComponentRepoUrl, defaultBranch)
                    paramList.add(choice(choices: branchList.join('\n'), description: "Choose Application Component Branch", name: 'AppComponentBranch'))
                }
            }
        }

        config.enabledParamList.each { String paramName ->
            log.debug("paramName=${paramName}")
            if (paramMap.containsKey(paramName)) {
                paramList.add(paramMap[paramName])
            } else {
                log.warn("paramName=${paramName} not found")
            }
        }

    }

    properties([
            parameters(paramList),
            disableConcurrentBuilds()
    ])

    log.debug("params=${JsonUtils.printToJsonString(params)}")

    params.each { String key, def value ->
        if (value!="") {
            key=Utilities.decapitalize(key)
            config[key] = value
        }
    }

    if (config.containsKey("enabledParamList")) {
        config.remove("enabledParamList")
    }

    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config

}

Map getSelectableDeploymentJobParams(Map inConfig) {

    Map config = inConfig.clone()
    config.isGroupJob = true

    List LOGLEVELS = ["INFO","DEBUG","WARN","ERROR"]

    List deployAppParamList = ['Frontend','DevPortal','OpenAPINotifier']

    String artifactVersionDefault = config.getOrDefault("artifactVersion", "")
    String alwaysEmailListDefault = config.getOrDefault("alwaysEmailList", "")
    boolean runPostDeployTestsDefault = config.getOrDefault("runPostDeployTests", true)
    boolean useSimulationModeDefault = config.getOrDefault("useSimulationMode", false)
    boolean debugPipelineDefault = config.getOrDefault("debugPipeline", false)
    boolean debugReleaseScriptDefault = config.getOrDefault("debugReleaseScript", false)


    String artifactVersionDescription = "Recommended to leave this blank, as it will then use the configuration setting for each env in "
    artifactVersionDescription += " <a href=\"https://fusion.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATHEnv.groovy\">runAppDeployEnv yml</a>"

    Map paramMap = [
            artifactVersion: string(defaultValue: artifactVersionDefault, description: artifactVersionDescription, name: 'ArtifactVersion'),
            alwaysEmailList: string(defaultValue: alwaysEmailListDefault, description: "Specify always email dist", name: 'AlwaysEmailList'),
            runPostDeployTests: booleanParam(defaultValue: runPostDeployTestsDefault, name: 'RunPostDeployTests'),
            useSimulationMode: booleanParam(defaultValue: useSimulationModeDefault, description: "Use Simulation Mode?", name: 'UseSimulationMode'),
            debugPipeline: booleanParam(defaultValue: debugPipelineDefault, description: "Debug Pipeline?", name: 'DebugPipeline'),
            debugReleaseScript: booleanParam(defaultValue: debugReleaseScriptDefault, description: "Debug ARA Release Script?", name: 'DebugReleaseScript'),
            logLevel: choice(choices: LOGLEVELS.join('\n'), description: "Choose Log Level", name: 'LogLevel')
    ]

    List paramList = []

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    deployAppParamList.each { componentId ->
        paramList.addAll([booleanParam(defaultValue: false, description: "Deploy ${componentId}?", name: "Deploy${componentId}")])
    }

    properties([
            parameters(paramList),
            disableConcurrentBuilds()
    ])

    List deployGroup = []

    deployAppParamList.each { componentId ->
        if (params["Deploy${componentId}"]) deployGroup.add(componentId)
    }

    log.debug("params=${JsonUtils.printToJsonString(params)}")

    paramMap.each { Map entry ->
        String key=Utilities.capitalize(entry.key)
        def value = params[key]
        if (value!="") {
            log.debug("added key=${key} value=${value}")
            config[entry.key] = value
        }
    }

    config.deployGroup = deployGroup

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config

}

