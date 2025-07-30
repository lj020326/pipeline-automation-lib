#!/usr/bin/env groovy

@Library("pipelineAutomationLib")_

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils

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

config.alwaysEmailList="ljohnson@dettonville.com"

log.info("config=${JsonUtils.printToJsonString(config)}")

//runATHEnv(config)
runATHEnvJob(config)
