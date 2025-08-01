#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.JsonUtils

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    def ymlConfigString = """
---
appDeployStrategies:
    DAILY_JOBS:
        cronCfg: "H 2 * * *"
        alwaysEmailList: "lee.johnson@dettonville.com"

appEnvironments:
    DEV: 
        artifactVersion: "1.63.0-SNAPSHOT"
        appTestEnvironment: DEV
        alwaysEmailList: "lee.johnson@dettonville.com"
        araEnvSpecFile: "DCAPI/env_specs/dev_env_spec.json"

## following section used for demo purposes:
#        appComponents:
#            Frontend:
#                artifactVersion: "1.62.0-SNAPSHOT"
    
    STAGE:
        artifactVersion: "1.63.0-SNAPSHOT"
        appTestEnvironment: STAGE_EXTERNAL
        alwaysEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com, dcapi_qa@dettonville.com"
        araEnvSpecFile: "DCAPI/env_specs/stage_env_spec.json"
    
    PROD_STL:
        artifactVersion: "1.62.0"
        appTestEnvironment: PROD_STL
        alwaysEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com, dcapi_qa@dettonville.com"
        araEnvSpecFile: "DCAPI/env_specs/prod_nyc_env_spec.json"
    
    PROD_KSC:
        artifactVersion: "1.62.0"
        appTestEnvironment: PROD_KSC
        alwaysEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com, dcapi_qa@dettonville.com"
        araEnvSpecFile: "DCAPI/env_specs/prod_jpn_env_spec.json"


"""

    String ymlDeployConfigFileUrl = "curl https://fusion.dettonville.int/stash/projects/API/repos/deployment_configs/raw/deployment_configs/dcapi/appDeployConfig.yml?at=refs%2Fheads%2Fmain"
    String ymlConfigString = sh(script: ymlDeployConfigFileUrl, returnStdout: true)
    log.info("browserstacklocal process already running: [\n${processInfoList}]")


    Map ymlConfigMap = readYaml text: ymlConfigString
    Map envSettings = ymlConfigMap.appEnvironments.get(config.appEnvironment, [:])

//    Map appEnvComponentSettings = ymlConfigMap.appEnvComponentSettings ?: [:]
//    if (appEnvComponentSettings.containsKey(config.appEnvironment) &&
//            appEnvComponentSettings[config.appEnvironment].containsKey(config.appComponentSet))
//    {
//        log.debug("0) any env+component specific settings. E.g., artifactVersion")
//        Map componentSettings = appEnvComponentSettings[config.appEnvironment].get(config.appComponentSet, [:])
//        config=MapMerge.merge(componentSettings, config)
//        log.debug("0) config=${JsonUtils.printToJsonString(config)}")
//    }

    if (!config.isGroupJob) {
        log.debug("1) apply appenv settings")
        config = MapMerge.merge(envSettings, config)
//        config = MapMerge.merge(config, envSettings)
        log.debug("1) config=${JsonUtils.printToJsonString(config)}")
    }

    if (config?.appDeployStrategy) {
        log.debug("2) apply deploy suite settings")
        Map appDeployStrategySettings = ymlConfigMap.appDeployStrategies.get(config.appDeployStrategy.toUpperCase(), [:])
        config=MapMerge.merge(appDeployStrategySettings, config)
        log.debug("2) config=${JsonUtils.printToJsonString(config)}")
    }

    if (config?.cronCfg) {
        properties([pipelineTriggers([cron("${config.cronCfg}")])])
    }

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAppDeployment(config)

}

