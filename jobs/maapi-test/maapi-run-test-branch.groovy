#!/usr/bin/env groovy

@Library("pipeline-automation-lib")_

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils

Map config=[:]

Logger.init(this, LogLevel.INFO)
//Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

config.jobBaseFolderLevel = 4
config.enableBranchSettings = true
config.enabledParamList = ['athGitBranch','alwaysEmailList','useDryRun','useSimulationMode']

config.appEnvironment=JOB_NAME.split("/")[config.jobBaseFolderLevel].toUpperCase()

//List externalAppEnvList = ["STAGE_EXTERNAL","PROD_EXTERNAL"]
//
//if (externalAppEnvList.contains(config.appEnvironment)) {
//    config.useBrowserstackLocalProxy=false
//    config.useExecEnvJenkins=false
//}

config.alwaysEmailList="ljohnson@dettonville.org"

log.info("config=${JsonUtils.printToJsonString(config)}")

//runATHEnv(config)
runATHEnvJob(config)
