#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
//import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger


def call(Map inConfig=[:]) {

//     Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this, LogLevel.INFO)

    String logPrefix="runTerraformJob():"

    log.info("${logPrefix} Loading Default Configs")

    List paramList = [
        booleanParam(name: 'ACTION_PLAN', defaultValue: true, description: 'Run terraform plan action'),
        booleanParam(name: 'ACTION_APPLY', defaultValue: true, description: 'Run terraform apply action'),
        booleanParam(name: 'ACTION_SHOW', defaultValue: true, description: 'Run terraform show action'),
        booleanParam(name: 'ACTION_PREVIEW_DESTROY', defaultValue: false, description: 'Run terraform preview destroy action'),
        booleanParam(name: 'ACTION_DESTROY', defaultValue: false, description: 'Run terraform destroy action'),
        choice(
                choices: ['dev', 'test', 'prod'],
                description: 'deployment environment',
                name: 'ENVIRONMENT')
    ]

    properties([
        parameters(paramList),
        disableConcurrentBuilds()
    ])

    Map config = MapMerge.merge(inConfig, params)

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runTerraform(config)
}
