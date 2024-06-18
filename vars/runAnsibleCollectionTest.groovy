#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="runAnsibleCollectionTest():"

    config.testTagsParam = config.get('testTagsParam',[])
    config.testType = config.get('testType','module')

    List paramList = []

    Map paramMap = [
        testCaseIdList     : string(
            defaultValue: "",
            description: "Limit test to specified comma-delimited test cases\nE.g., '01','02,05'",
            name: 'TestCaseIdList'),
        ansibleTags        : choice(choices: config.testTagsParam.join('\n'), description: "Choose Test Tag", name: 'AnsibleTags'),
        ansibleLimitHosts  : string(
            defaultValue: "",
            description: "Limit playbook to specified inventory hosts\nE.g., 'testgroup_lnx','host01,host02'",
            name: 'AnsibleLimitHosts'),
        ansibleDebugFlag   : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
        ansibleGalaxyForceOpt  : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Force Mode?", name: 'AnsibleGalaxyForceOpt'),
        ansibleGalaxyUpgradeOpt : booleanParam(defaultValue: true, description: "Use Ansible Galaxy Upgrade?", name: 'AnsibleGalaxyUpgradeOpt'),
        useCheckDiffMode   : booleanParam(defaultValue: false, description: "Use Check+Diff Mode (Dry Run with Diffs)?", name: 'UseCheckDiffMode'),
        initializeJobMode  : booleanParam(defaultValue: false, description: "Initialize Job Mode?", name: 'InitializeJobMode')
    ]

    paramMap.each { String key, def param ->
        if (config.testType == 'role') {
            if (key != 'testCaseIdList') {
                paramList.addAll([param])
            }
        } else {
            paramList.addAll([param])
        }
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

    if (config.useCheckDiffMode) {
        config.ansibleCheckMode=true
        config.ansibleDiffMode=true
    }

    config.ansibleInstallation = config.get('ansibleInstallation',"ansible-venv")

    config.ansiblePlaybook = config.get('ansiblePlaybook',"${env.JOB_NAME.split('/')[-2]}.yml")

    //     ref: https://stackoverflow.com/questions/62213910/run-only-tasks-with-a-certain-tag-or-untagged
    // config.ansibleTags = config.get('ansibleTags',"untagged,${env.JOB_NAME.split('/')[-2]}")

    Map ansibleExtraVars = config.get('ansibleExtraVars',[:])
    if (config.testCaseIdList) {
        ansibleExtraVars.test_case_id_list_string = config.testCaseIdList
    }
    config.ansibleExtraVars = ansibleExtraVars
    config.isTestPipeline = true

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    if (!config.initializeJobMode) {
        runAnsiblePlaybook(config)
    }

    log.info("${logPrefix} finished")

}

