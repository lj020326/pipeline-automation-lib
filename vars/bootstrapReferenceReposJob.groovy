#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    List paramList = []

    Map paramMap = [
        jenkinsNodeLabel: string(defaultValue: "controller", description: "Specify the Jenkins node/label", name: 'JenkinsNodeLabel')
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

//     // Cron job configurations – configured to run every 5 minutes
//     String cron_cfg="H/5 * * * *"
    // Cron job configurations – configured to run every day at 23:00 PM
    String cron_cfg="H 23 * * *"

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

    log.info("config=${JsonUtils.printToJsonString(config)}")

    bootstrapReferenceRepos(config)

    log.info("finished")

}

