#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="runAnsibleDevJob():"

    config.gitRepoUrl = config.get('gitRepoUrl', "https://github.com/lj020326/ansible-configvars-examples.git")

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

    // config.ansibleInventory = config.get('ansibleInventory', './inventory')
    config.ansibleInventory = 'hosts'
    config.ansiblePlaybook = 'site.yml'

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePlaybook(config)

}

