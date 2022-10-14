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

    Map paramMap = [
        ansibleLimitHosts  : string(defaultValue: "", description: "Limit playbook to specified inventory hosts\nE.g., 'host01', 'host01,host02'", name: 'AnsibleLimitHosts'),
        ansibleDebugFlag   : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
        ansibleGalaxyForceOpt  : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Force Mode?", name: 'AnsibleGalaxyForceOpt'),
        useCheckDiffMode   : booleanParam(defaultValue: false, description: "Use Check+Diff Mode (Dry Run with Diffs)?", name: 'UseCheckDiffMode')
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

    if (config.useCheckDiffMode) {
        config.ansibleCheckMode=true
        config.ansibleDiffMode=true
    }

//     config.skipDefaultCheckout = true
    config.gitBranch = 'master'
    config.gitRepoUrl = 'git@bitbucket.org:lj020326/ansible-datacenter.git'
    config.gitCredId = 'bitbucket-ssh-lj020326'

    config.ansibleCollectionsRequirements = 'collections/requirements.yml'
    config.ansibleRolesRequirements = './roles/requirements.yml'
//     config.ansibleTags = "${env.JOB_BASE_NAME}"
    // ref: https://stackoverflow.com/questions/62213910/run-only-tasks-with-a-certain-tag-or-untagged
    config.ansibleTags = "untagged,${env.JOB_BASE_NAME}"
    config.environment = "${env.JOB_NAME.split('/')[-2]}"

    config.towerHost = "https://awx.admin.dettonville.int"

    List ansibleEnvVarsList=[
        "TOWER_HOST=${config.towerHost}",
        "CONTROLLER_VERIFY_SSL=no",
        "LANG=en_US.UTF-8"
    ]
    config.ansibleEnvVarsList = ansibleEnvVarsList

    List ansibleSecretVarsList=[
        usernamePassword(credentialsId: 'dcapi-ansible-ssh-password', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
        string(credentialsId: 'awx-oauth-token', variable: 'TOWER_OAUTH_TOKEN')
    ]
    config.ansibleSecretVarsList = ansibleSecretVarsList

//     config.ansibleInventory = './inventory'
//     config.ansibleInventory = './inventory/hosts.ini'
//    config.ansibleInventory = "./inventory/${config.environment}/hosts.ini"
    config.ansibleInventory = "./inventory/${config.environment}/hosts.yml"

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePlaybook(config)

}

