#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    List paramList = []

    Map paramMap = [
        ansibleLimitHosts  : string(defaultValue: "", description: "Limit playbook to specified inventory hosts\nE.g., 'host01', 'host01,host02'", name: 'AnsibleLimitHosts'),
        ansibleTags  : string(defaultValue: "", description: "Run playbook with tags\nE.g., 'bootstrap-linux', etc", name: 'AnsibleTags'),
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

    // config.get('ansibleInventory', './inventory')
//    config.get('ansibleInventory','hosts.ini')
    config.get('ansibleInventory','hosts.yml')
    config.get('ansiblePlaybook','site.yml')
    config.gitPerformCheckout = false

    log.info("env.BRANCH_NAME=${env.BRANCH_NAME}")

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePipeline(config)

}

