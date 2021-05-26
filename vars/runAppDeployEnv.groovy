#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.MapMerge

import com.dettonville.api.pipeline.utils.JsonUtils


def call(Map config=[:]) {
    String logPrefix="runAppDeployEnv():"

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String ymlConfigDefaultsString = """
---
appDeployStrategies:
    DAILY_JOBS:
        cronCfg: "H 2 * * *"
        alwaysEmailList: "ljohnson@dettonville.org, conor.dixon@dettonville.org"

appEnvironments:

"""

//    String ymlDeployConfigFileUrl = "https://fusion.dettonville.int/stash/projects/API/repos/deployment_configs/raw/deployment_configs/dcapi/appDeployConfig.yml?at=refs%2Fheads%2Fmaster"
    String ymlDeployConfigFileUrl = "https://gitrepository.dettonville.int/stash/projects/API/repos/deployment_configs/raw/deployment_configs/dcapi/appDeployConfig.yml?at=refs%2Fheads%2Fmaster"
    String fetchYmlConfigCmd = "curl -s ${ymlDeployConfigFileUrl}"

    String ymlConfigString = ""
    node ('QA-LINUX || PROD-LINUX') {
        log.info("${logPrefix} fetching yml config from [${ymlDeployConfigFileUrl}]")
        ymlConfigString = sh(script: fetchYmlConfigCmd, returnStdout: true)
    }

    Map ymlConfigDefaultsMap = readYaml text: ymlConfigDefaultsString
    Map appEnvironments = ymlConfigDefaultsMap.get("appEnvironments", [:])
    appEnvironments = appEnvironments ?: [:]
    log.debug("${logPrefix} appEnvironments=${appEnvironments}")
    Map envDefaultsSettings = appEnvironments.get(config.appEnvironment, [:])

    Map ymlConfigMap = readYaml text: ymlConfigString
    log.info("${logPrefix} fetched yml config=${JsonUtils.printToJsonString(ymlConfigMap)}")

    Map envSettings = ymlConfigMap.appEnvironments.get(config.appEnvironment, [:])

    log.debug("${logPrefix} 1) apply appenv settings")
    config = MapMerge.merge(envSettings, config)
    log.debug("${logPrefix} 1) config=${JsonUtils.printToJsonString(config)}")

    log.debug("${logPrefix} 2) apply appenv defaults")
    config = MapMerge.merge(envDefaultsSettings, config)
    log.debug("${logPrefix} 2) config=${JsonUtils.printToJsonString(config)}")

    if (config?.appDeployStrategy) {
        log.debug("${logPrefix} 3) apply deploy strategy settings")
//        Map appDeployStrategySettings = ymlConfigMap.appDeployStrategies.get(config.appDeployStrategy.toUpperCase(), [:])
        Map appDeployStrategyies = ymlConfigMap.appDeployStrategies
        appDeployStrategyies = appDeployStrategyies ?: [:]
        Map appDeployStrategySettings = appDeployStrategyies.get(config.appDeployStrategy.toUpperCase(), [:])
        config=MapMerge.merge(appDeployStrategySettings, config)
        log.debug("${logPrefix} 3) config=${JsonUtils.printToJsonString(config)}")
    }

    if (config?.cronCfg) {
        properties([pipelineTriggers([cron("${config.cronCfg}")])])
    }

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runAppDeployment(config)

}

