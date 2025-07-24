#!/usr/bin/env groovy
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
//import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map inConfig=[:]) {

    log.info("Loading Default Configs")

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
    log.info("jobParts=${jobParts}")

    config.jobBaseFolderLevel = config.jobBaseFolderLevel ?: 3

    int startIdx=config.jobBaseFolderLevel+1
    int endIdx=jobParts.size()-1

    config.environment=jobParts[config.jobBaseFolderLevel].toUpperCase()

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runTerraform(config)
}
