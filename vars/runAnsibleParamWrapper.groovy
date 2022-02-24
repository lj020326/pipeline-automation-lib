#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="runAnsibleParamWrapper():"

    List paramList = []

//    List inventoryList = ["admin01",
//                       "media01",
//                       "nas02"]

    Map paramMap = [
        ansibleLimitHosts  : string(defaultValue: "", description: "Limit playbook to specified inventory hosts\nE.g., 'host01', 'host01,host02'", name: 'AnsibleLimitHosts'),
        ansibleDebugFlag   : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
//        ansibleLimitHosts      : choice(choices: inventoryList.join('\n'), description: "Choose Inventory Host", name: 'AnsibleLimitHosts'),
//        debugPipeline      : booleanParam(defaultValue: false, description: "Debug Pipeline?", name: 'DebugPipeline'),
//        logLevel           : choice(choices: "INFO\nDEBUG\nWARN\nERROR", description: "Choose Log Level", name: 'LogLevel')
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList),
        disableConcurrentBuilds()
    ])

    params.each { key, value ->
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key] = value
        }
    }

//    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")
    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePlaybook(config)

}

