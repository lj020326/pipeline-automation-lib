#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
//import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger


def call(Map inConfig=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="runTerraformJob():"

    log.info("${logPrefix} Loading Default Configs")

    List paramList = [
        booleanParam(name: 'ACTION_PLAN', defaultValue: true, description: 'Run terraform plan action'),
        booleanParam(name: 'ACTION_APPLY', defaultValue: true, description: 'Run terraform apply action'),
        booleanParam(name: 'ACTION_SHOW', defaultValue: true, description: 'Run terraform show action'),
        booleanParam(name: 'ACTION_PREVIEW_DESTROY', defaultValue: false, description: 'Run terraform preview destroy action'),
        booleanParam(name: 'ACTION_DESTROY', defaultValue: false, description: 'Run terraform destroy action'),
    ]

    properties([
        parameters(paramList),
        disableConcurrentBuilds()
    ])

    Map config = MapMerge.merge(inConfig, params)

    List jobParts = JOB_NAME.split("/")
    log.info("${logPrefix} jobParts=${jobParts}")

    config.jobBaseFolderLevel = config.jobBaseFolderLevel ?: 3

    int startIdx=config.jobBaseFolderLevel+1
    int endIdx=jobParts.size()-1

    config.environment=jobParts[config.jobBaseFolderLevel].toUpperCase()

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runTerraform(config)
}
