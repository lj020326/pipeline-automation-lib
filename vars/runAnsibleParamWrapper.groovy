#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

//     Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this, LogLevel.INFO)

    String logPrefix="runAnsibleParamWrapper():"

    List paramList = []

    Map paramMap = [
        ansibleLimitHosts : string(
            defaultValue: "",
            description: "Limit playbook to specified inventory hosts\nE.g., 'app_adm','app_tableau','host01', 'host01,host02'",
            name: 'AnsibleLimitHosts'),
        ansibleDebugFlag : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
        ansibleGalaxyForceOpt : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Force Mode?", name: 'AnsibleGalaxyForceOpt'),
        useCheckDiffMode : booleanParam(defaultValue: false, description: "Use Check+Diff Mode (Dry Run with Diffs)?", name: 'UseCheckDiffMode'),
        skipUntagged : booleanParam(defaultValue: false, description: "Skip Untagged plays?", name: 'SkipUntagged')
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

    config.environment = config.get('environment',"${env.JOB_NAME.split('/')[-2]}")

    if (config.skipUntagged) {
        ansibleTagsDefault = "${env.JOB_BASE_NAME}"
    } else {
        // ref: https://stackoverflow.com/questions/62213910/run-only-tasks-with-a-certain-tag-or-untagged
        ansibleTagsDefault = "untagged,${env.JOB_BASE_NAME}"
    }
    config.ansibleTags = config.get('ansibleTags',"${ansibleTagsDefault}")

//     config.skipDefaultCheckout = true
//     config.gitBranch = 'master'
    config.gitBranch = config.get('gitBranch','main')
    config.gitRepoUrl = config.get('gitRepoUrl','git@bitbucket.org:lj020326/ansible-datacenter.git')
    config.gitCredId = config.get('gitCredId','bitbucket-ssh-jenkins')

//     config.ansibleCollectionsRequirements = config.get('ansibleCollectionsRequirements','./collections/requirements.molecule.yml')
//     config.ansibleRolesRequirements = config.get('ansibleRolesRequirements','./roles/requirements.yml')

//     List ansibleSecretVarsList=[
//         usernamePassword(credentialsId: 'ansible-ssh-password-linux', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//         string(credentialsId: 'awx-oauth-token', variable: 'TOWER_OAUTH_TOKEN')
//     ]

    config.ansibleVarFiles = config.get('ansibleVarFiles', [])
    if (config.containsKey('ansibleVault')) {
        config.ansibleVarFiles += "${config.ansibleVault}"
    }

    config.ansibleInventory = config.get('ansibleInventory',"./inventory/${config.environment}/hosts.yml")

    log.info("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePlaybook(config)

    log.info("${logPrefix} finished")

}

