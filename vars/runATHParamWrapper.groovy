#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    config.get('enabledParamList', [])
    config.get('enableDevParams', (config.enabledParamList.isEmpty()) ? true : false)
    config.enableBranchParam = config.enabledParamList.contains("athGitBranch") ? true : config.get('enableBranchParam', false)
    config.get('athGitRepo', "https://gitrepository.dettonville.int/stash/scm/api/infra-test.git")

    List paramList = []

    List appEnvironmentList = ["STAGE_EXTERNAL",
                               "STAGE",
                               "DEV",
                               "DEV_CLOUD",
                               "PROD_EXTERNAL",
                               "PROD_COPRO",
                               "PROD_STL",
                               "PROD_KSC",
                               "MTF",
                               "Stage1",
                               "Stage2",
                               "Stage3",
                               "ITF"]

    Map paramMap = [
            appEnvironment      : choice(choices: appEnvironmentList.join('\n'), description: "Choose App Environment", name: 'AppEnvironment'),
            browserstackBrowser : choice(choices: "Chrome\nSafari\nFirefox\nEdge\nIE\nIphone\nAndroid\nAPI\nMultiPlatform", description: "Choose Web Platform", name: 'BrowserstackBrowser'),
            parallelRunCount    : string(defaultValue: "1", description: "Specify number of maven jobs to run in parallel\nE.g., 1, 4, 8,...'n'", name: 'ParallelRunCount'),
            metaFilterTags      : string(defaultValue: "+smoke", description: "Specify meta filter tags\nE.g., '-smoke', '+404'", name: 'MetaFilterTags'),
            alwaysEmailList     : string(defaultValue: "", description: "Specify always email dist", name: 'AlwaysEmailList'),
            useDryRun           : booleanParam(defaultValue: false, description: "Dry Run?", name: 'UseDryRun'),
            useSimulationMode   : booleanParam(defaultValue: false, description: "Use Simulation Mode?", name: 'UseSimulationMode'),
            debugMvn            : booleanParam(defaultValue: false, description: "Debug Maven?", name: 'DebugMvn'),
            debugPipeline       : booleanParam(defaultValue: false, description: "Debug Pipeline?", name: 'DebugPipeline'),
            logLevel            : choice(choices: "INFO\nDEBUG\nWARN\nERROR", description: "Choose Log Level", name: 'LogLevel'),
            jenkinsRunTestsLabel: string(defaultValue: "", description: "Specify the Jenkins Test node label\nIf empty - the env is set to default based on env\nE.g., '', 'DEVCLD-LIN7', 'QA-LINUX', 'PROD-LINUX',...'any'", name: 'JenkinsRunTestsLabel')
    ]

    if (config.enableDevParams) {
        config.enabledParamList = paramMap.keySet() as List
    }

//    node ("QA-LINUX || PROD-LINUX") {
    node {

        if (config.enableBranchParam) {
            String defaultBranch = "develop"
            List branchList = Utilities.getRepoBranchList(this, config.athGitRepo, defaultBranch)
            paramList.addAll([choice(choices: branchList.join('\n'), description: "Choose Branch", name: 'AthGitBranch')])
        }
    }

    paramMap.each { String key, def param ->
        if (config.enabledParamList.contains(key)) {
            paramList.addAll([param])
        }
    }

    properties([
            parameters(paramList),
            disableConcurrentBuilds()
    ])

    params.each { key, value ->
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key] = value
        }
    }

    config.athGitBranch = config.athGitBranch ?: env.BRANCH_NAME

    if (config.containsKey("enabledParamList")) {
        config.remove("enabledParamList")
    }

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runATHEnv(config)

}

