#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="runAnsibleDevJob():"

    List paramList = []

    Map paramMap = [
        ansibleDebugFlag   : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
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


    List ansibleEnvVarsList=[
        "DEVEL_IMAGE_NAME=media.johnson.int:5000/awx"
    ]
    config.ansibleEnvVarsList = ansibleEnvVarsList

    // config.ansibleInventory = config.get('ansibleInventory', './inventory')
//     config.ansibleInventory = config.get('ansibleInventory','hosts.ini')
    config.ansiblePlaybook = config.get('ansiblePlaybook','tools/ansible/dockerfile.yml')
    config.gitPerformCheckout = false

    log.info("${logPrefix} env.BRANCH_NAME=${env.BRANCH_NAME}")

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePlaybook(config)

    make docker-compose-build

}

