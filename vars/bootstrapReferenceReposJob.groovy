#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="bootstrapReferenceReposJob():"

    List paramList = []

    Map paramMap = [
        jenkinsNodeLabel: string(defaultValue: "controller", description: "Specify the Jenkins node/label", name: 'JenkinsNodeLabel')
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

//     // Cron job configurations – configured to run every 5 minutes
//     cron_cfg="H/5 * * * *"
    // Cron job configurations – configured to run every day at 23:00 PM
    cron_cfg="H 23 * * *"

    properties([
        parameters(paramList),
        disableConcurrentBuilds(),
        pipelineTriggers([cron("${cron_cfg}")])
    ])

    params.each { key, value ->
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key] = value
        }
    }

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    bootstrapReferenceRepos(config)

    log.info("${logPrefix} finished")

}

