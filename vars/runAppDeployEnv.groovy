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

    String ymlConfigDefaultsString = """
---
appDeployStrategies:
    DAILY_JOBS:
        cronCfg: "H 2 * * *"
        alwaysEmailList: "lee.johnson@dettonville.com"

appEnvironments:

"""

//    String ymlDeployConfigFileUrl = "https://fusion.dettonville.int/stash/projects/API/repos/deployment_configs/raw/deployment_configs/dcapi/appDeployConfig.yml?at=refs%2Fheads%2Fmain"
    String ymlDeployConfigFileUrl = "https://gitrepository.dettonville.int/stash/projects/API/repos/deployment_configs/raw/deployment_configs/dcapi/appDeployConfig.yml?at=refs%2Fheads%2Fmain"
    String fetchYmlConfigCmd = "curl -s ${ymlDeployConfigFileUrl}"

    String ymlConfigString = ""
    node ('QA-LINUX || PROD-LINUX') {
        log.info("fetching yml config from [${ymlDeployConfigFileUrl}]")
        ymlConfigString = sh(script: fetchYmlConfigCmd, returnStdout: true)
    }

    Map ymlConfigDefaultsMap = readYaml text: ymlConfigDefaultsString
    Map appEnvironments = ymlConfigDefaultsMap.get("appEnvironments", [:])
    appEnvironments = appEnvironments ?: [:]
    log.debug("appEnvironments=${appEnvironments}")
    Map envDefaultsSettings = appEnvironments.get(config.appEnvironment, [:])

    Map ymlConfigMap = readYaml text: ymlConfigString
    log.info("fetched yml config=${JsonUtils.printToJsonString(ymlConfigMap)}")

    Map envSettings = ymlConfigMap.appEnvironments.get(config.appEnvironment, [:])

    log.debug("1) apply appenv settings")
    config = MapMerge.merge(envSettings, config)
    log.debug("1) config=${JsonUtils.printToJsonString(config)}")

    log.debug("2) apply appenv defaults")
    config = MapMerge.merge(envDefaultsSettings, config)
    log.debug("2) config=${JsonUtils.printToJsonString(config)}")

    if (config?.appDeployStrategy) {
        log.debug("3) apply deploy strategy settings")
//        Map appDeployStrategySettings = ymlConfigMap.appDeployStrategies.get(config.appDeployStrategy.toUpperCase(), [:])
        Map appDeployStrategyies = ymlConfigMap.appDeployStrategies
        appDeployStrategyies = appDeployStrategyies ?: [:]
        Map appDeployStrategySettings = appDeployStrategyies.get(config.appDeployStrategy.toUpperCase(), [:])
        config=MapMerge.merge(appDeployStrategySettings, config)
        log.debug("3) config=${JsonUtils.printToJsonString(config)}")
    }

    if (config?.cronCfg) {
        properties([pipelineTriggers([cron("${config.cronCfg}")])])
    }

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAppDeployment(config)

}

