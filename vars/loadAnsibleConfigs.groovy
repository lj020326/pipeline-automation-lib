#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

//@NonCPS
Map call(Map params) {
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    config.get('logLevel', "INFO")
    config.get('debugPipeline', false)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    config.get('jenkinsNodeLabel',"ansible")

    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')
    config.get('tmpDirMaxFileCount', 100)


//    config.emailDist = config.emailDist ?: "lee.johnson@dettonville.com"
    config.get('emailDist',"lee.johnson@dettonville.com")
    // config.alwaysEmailDist = config.alwaysEmailDist ?: "lee.johnson@dettonville.com"
    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

    config.get('skipDefaultCheckout', false)

    config.get("gitRemoteRepoType", "bitbucket")
    config.get("gitRemoteBuildKey", 'Ansible playbook run')
	config.get("gitRemoteBuildName", 'Ansible playbook run')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

    config.get('gitPerformCheckout', !config.get('skipDefaultCheckout',false))

    config.get('varFilesRelativeToPlaybookDir', false)
    config.get('inventoryPathRelativeToPlaybookDir', false)
    config.get('requirementsPathsRelativeToPlaybookDir', false)

//     config.get('ansiblePlaybookDir', 'ansible-linux')
//     config.get('ansiblePlaybookDir',"ansible/${env.JOB_NAME.split('/')[-2]}")

    config.get('ansibleCollectionsRequirements', 'collections/requirements.yml')
    if (config?.requirementsPathsRelativeToPlaybookDir && config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir) {
        config.ansibleCollectionsRequirements = "${config.ansiblePlaybookDir}/${config.ansibleCollectionsRequirements}"
    }

//     config.get('ansibleRolesRequirements', './roles/requirements.yml')
//    config.get('ansibleInventory', 'inventory')
//    config.get('ansibleInventory', 'hosts.yml')
    if ( config.ansibleInventory ) {
        if (config?.inventoryPathRelativeToPlaybookDir && config.inventoryPathRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir) {
            config.ansibleInventory = "${config.ansiblePlaybookDir}/${config.ansibleInventory}"
        }
        config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))
    }

    config.get('ansibleGalaxyIgnoreCerts', false)
    config.get('ansibleGalaxyForceOpt', false)
    config.get('ansibleGalaxyUpgradeOpt', false)

    config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.get('ansibleVaultCredId', 'ansible-vault-password-file')
// //     config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token-file')
//     config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token')
    config.get('ansiblePlaybook', 'site.yml')
    config.get('ansibleTags', '')
    config.get('ansibleSkipTags', '')

    String ansibleGalaxyCmd = "ansible-galaxy"
    String ansibleCmd = "ansible"

    config.get('useDockerAnsibleInstallation', false)
    if (!config.useDockerAnsibleInstallation) {
        config.get('ansibleInstallation', 'ansible-venv')
    }
    config.ansibleGalaxyCmd = ansibleGalaxyCmd
    config.ansibleCmd = ansibleCmd

    List ansibleGalaxyEnvVarsListDefault = []

//     ansibleGalaxyEnvVarsListDefault=[
//         "ANSIBLE_GALAXY_SERVER_LIST=published_repo,rh_certified,community_repo",
//         // published_repo
//         "ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_URL=https://ansible-galaxy.dettonville.int/api/galaxy/content/published/",
//         "ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_VALIDATE_CERTS=no",
//         // rh_certified
//         "ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_URL=https://ansible-galaxy.dettonville.int/api/galaxy/content/rh-certified/",
//         "ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_VALIDATE_CERTS=no",
//         // community_repo
//         "ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_URL=https://ansible-galaxy.dettonville.int/api/galaxy/content/community/",
//         "ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_VALIDATE_CERTS=no"
//     ]

    config.get('ansibleGalaxyEnvVarsList', ansibleGalaxyEnvVarsListDefault)

    List galaxySecretVarsListDefault=[]
//     List galaxySecretVarsListDefault=[
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_TOKEN'),
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_TOKEN'),
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_TOKEN')
//     ]
//
    config.get('galaxySecretVarsList', galaxySecretVarsListDefault)

    config.get('isTestPipeline', false)
    if (config.isTestPipeline) {
        config.get('testBaseDir', "test-results")
    }

    config.get('ansibleEnvVarsList', [])

    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://docs.cloudbees.com/docs/cloudbees-ci/latest/cloud-secure-guide/injecting-secrets
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
    List secretVarsListDefault = []
//     List secretVarsListDefault=[
//         usernamePassword(credentialsId: 'ansible-ssh-password-linux', passwordVariable: 'ANSIBLE_SSH_PASSWORD', usernameVariable: 'ANSIBLE_SSH_USERNAME'),
//         sshUserPrivateKey(credentialsId: 'bitbucket-ssh-jenkins'),
//         string(credentialsId: 'awx-oauth-token', variable: 'TOWER_OAUTH_TOKEN'),
//         file(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_TOKEN_PATH')
//     ]

    config.get('ansibleSecretVarsList', secretVarsListDefault)

    log.debug("params=${JsonUtils.printToJsonString(params)}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
