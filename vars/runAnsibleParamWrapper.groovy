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
        ansibleLimitHosts : string(
            defaultValue: "",
            description: "Limit playbook to specified inventory hosts\nE.g., 'app_adm','app_tableau','host01', 'host01,host02'",
            name: 'AnsibleLimitHosts'),
        ansibleDebugFlag : choice(choices: "\n-v\n-vv\n-vvv\n-vvvv", description: "Choose Ansible Debug Level", name: 'AnsibleDebugFlag'),
        ansibleGalaxyForceOpt : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Force Mode?", name: 'AnsibleGalaxyForceOpt'),
        ansibleGalaxyUpgradeOpt : booleanParam(defaultValue: false, description: "Use Ansible Galaxy Upgrade?", name: 'AnsibleGalaxyUpgradeOpt'),
        useCheckDiffMode : booleanParam(defaultValue: false, description: "Use Check+Diff Mode (Dry Run with Diffs)?", name: 'UseCheckDiffMode'),
        skipUntagged : booleanParam(defaultValue: false, description: "Skip Untagged plays?", name: 'SkipUntagged'),
        skipAlwaysTag : booleanParam(defaultValue: false, description: "Skip 'always' tagged plays?", name: 'SkipAlwaysTag'),
        debugPipeline : booleanParam(defaultValue: false, description: "Debug pipeline mode?", name: 'DebugPipeline')
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

    config.get('environment',"${env.JOB_NAME.split('/')[-3]}")
    config.get('ansibleInstallation',"ansible-venv")

    def ansibleTags = "untagged,${env.JOB_BASE_NAME}"
    if (config.skipUntagged) {
        ansibleTags = "${env.JOB_BASE_NAME}"
    }
    config.get('ansibleTags',"${ansibleTags}")

    if (config.skipAlwaysTag) {
        config.get('ansibleSkipTags',"always")
    }

    config.get('ansiblePipelineConfigFile',".jenkins.ansible.yml")
//     config.get('ansibleInventory',"./inventory/${config.environment}/hosts.yml")
    config.get('ansibleInventory',"./inventory/${config.environment}")

    List ansibleEnvVarsListDefault = [
        "ANSIBLE_COLLECTIONS_PATH=~/.ansible/collections:/usr/share/ansible/collections:./requirements_collections:./collections"
    ]
    config.get('ansibleEnvVarsList',ansibleEnvVarsListDefault)

    config.get('ansibleVarFiles', [])
    if (config.ansibleVault) {
        config.ansibleVarFiles += ["${config.ansibleVault}"]
    }

//     config.skipDefaultCheckout = true
//     config.gitBranch = 'master'
    config.get('gitBranch','main')
    config.get('gitRepoUrl','ssh://git@repo.example.org:2222/ansible/ansible-datacenter.git')
    config.get('gitCredentialsId','git-ssh-jenkins')

    config.get("gitRemoteRepoType","bitbucket")
    config.get("gitRemoteBuildKey", 'Ansible playbook run')
	config.get("gitRemoteBuildName", 'Ansible playbook run')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

//     config.get('ansibleCollectionsRequirements','./collections/requirements.molecule.yml')
//     config.get('ansibleRolesRequirements','./roles/requirements.yml')

//     List ansibleSecretVarsList=[
//         usernamePassword(credentialsId: 'ansible-ssh-password-linux', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//         string(credentialsId: 'awx-oauth-token', variable: 'TOWER_OAUTH_TOKEN')
//     ]

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAnsiblePipeline(config)

    log.info("finished")

}
