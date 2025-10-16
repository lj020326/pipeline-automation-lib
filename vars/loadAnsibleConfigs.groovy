#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import groovy.transform.Field
@Field Logger log = new Logger(this)

//@NonCPS
Map call(Map params) {
    Map config = [:]

    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    //
    // core pipeline configuration
    //
//     config.get('logLevel', "INFO")
    config.get('logLevel', "DEBUG")
    config.get('debugPipeline', false)
    log.setLevel(config.logLevel)
    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    config.get('jenkinsNodeLabel',"docker")
    config.get('timeout', 3)
    config.get('timeoutUnit', 'HOURS')
    config.get('tmpDirMaxFileCount', 100)
    config.get('skipDefaultCheckout', false)
    config.get('emailDist',"lee.johnson@dettonville.com")
    config.get('emailFrom',"admin+ansible@dettonville.com")

    config.get('gitRemoteRepoType', 'bitbucket')
    config.get('gitRemoteBuildKey', 'Ansible playbook run')
	config.get('gitRemoteBuildName', 'Ansible playbook run')
    config.get('gitRemoteBuildSummary', "${config.gitRemoteBuildName} update")
    config.get('gitPerformCheckout', !config.get('skipDefaultCheckout',false))

    //
    // docker configuration
    //
//     config.get('ansibleVersion', '2.18')
    config.get('ansibleVersion', '2.19')
    config.get('pythonVersion', '3.13')
    config.get("dockerRegistry", "media.johnson.int:5000")
    config.get("dockerImageName", "ansible/ansible-runner")
    config.dockerImage = getAnsibleDockerImageId(
                            dockerImageName: config.dockerImageName,
                            ansibleVersion: config.ansibleVersion,
                            pythonVersion: config.pythonVersion,
                            dockerRegistry: config.dockerRegistry)

    List dockerArgsList = []
    // required to trust internal ca certificates
    dockerArgsList.push("-v /etc/ssl/certs/ca-certificates.crt:/etc/ssl/certs/ca-certificates.crt:ro")

//     dockerArgsList.push("-v ${env.SSH_AUTH_SOCK}:${env.SSH_AUTH_SOCK}")
    dockerArgsList.push("-e SSH_AUTH_SOCK")

//     dockerArgsList.push("-v /var/run/docker.sock:/var/run/docker.sock")
//     dockerArgsList.push("--privileged")
//     dockerArgsList.push("-u root")

//     // configure to share the host's network stack.
//     // This removes the network isolation between the container and the host, allowing the container
//     // to access services running on the host via 127.0.0.1 or the host's primary IP address/
    dockerArgsList.push("--network host")

    if (config?.dockerUid && config?.dockerGid) {
        dockerArgsList.push("-u ${config.builderUid}:${config.builderGid}")
    }
    config.get("dockerArgs", dockerArgsList.join(" "))

    //
    // ansible configuration
    //
    config.get('ansiblePlaybook', 'site.yml')
//     config.get('ansibleInventory', './inventory/PROD')
    config.get('ansibleInventory', null)
    config.get('ansiblePlaybookDir', null)
    config.get('ansibleTags', '')
    config.get('ansibleSkipTags', '')
    config.get('ansibleLimitHosts', null)
    config.get('ansibleSshCredId', 'jenkins-ansible-ssh')
    config.get('ansibleVaultCredId', 'ansible-vault-password-file')
    config.get('ansibleVaultIdList', [])
    config.get('ansibleEnvVarsList', [])
    config.get('ansibleExtraParams', [])
    config.get('ansibleExtraVars', [:])
    config.get('ansibleDebugFlag', '-v')
    config.get('ansibleCheckMode', false)
    config.get('ansibleDiffMode', false)
    config.get('ansibleVarFiles', [])
    // we want to derive this from the inventory at runtime
//     config.get('ansiblePythonInterpreter', '/usr/bin/python3')
    config.get('varFilesRelativeToPlaybookDir', false)
    config.get('inventoryPathRelativeToPlaybookDir', false)
    config.get('requirementsPathsRelativeToPlaybookDir', false)
    config.get('ansibleCollectionsRequirements', 'requirements.yml')
    if (config?.requirementsPathsRelativeToPlaybookDir && config.requirementsPathsRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir) {
        config.ansibleCollectionsRequirements = "${config.ansiblePlaybookDir}/${config.ansibleCollectionsRequirements}"
    }
    if ( config.ansibleInventory ) {
        if (config?.inventoryPathRelativeToPlaybookDir && config.inventoryPathRelativeToPlaybookDir.toBoolean() && config.ansiblePlaybookDir) {
            config.ansibleInventory = "${config.ansiblePlaybookDir}/${config.ansibleInventory}"
        }
        config.ansibleInventoryDir = config.ansibleInventory.take(config.ansibleInventory.lastIndexOf('/'))
    }
    String ansibleGalaxyCmd = "ansible-galaxy"
    String ansibleCmd = "ansible"
    config.get('useDockerAnsibleInstallation', true)
    if (!config.useDockerAnsibleInstallation) {
        config.get('ansibleInstallation', 'ansible-venv')
    }
    config.ansibleGalaxyCmd = ansibleGalaxyCmd
    config.ansibleCmd = ansibleCmd
    config.get('ansibleGalaxyIgnoreCerts', false)
    config.get('ansibleGalaxyForceOpt', false)
    config.get('ansibleGalaxyUpgradeOpt', false)
//     config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token-file')
//     config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token')

    List ansibleGalaxyEnvVarsListDefault = []
//     List ansibleGalaxyEnvVarsListDefault=[
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
    if (config.ansibleGalaxyTokenCredId) {
        galaxySecretVarsListDefault = [
            string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_PUBLISHED_REPO_TOKEN'),
            string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_RH_CERTIFIED_TOKEN'),
            string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_GALAXY_SERVER_COMMUNITY_REPO_TOKEN')
        ]
    }
    config.get('galaxySecretVarsList', galaxySecretVarsListDefault)
    config.get('isTestPipeline', false)
    if (config.isTestPipeline) {
        config.get('testBaseDir', ".test-results")
    }

//     // Always add the ansible SSH key credential if none specified
//     List secretVarsListDefault = [sshUserPrivateKey(credentialsId: config.ansibleSshCredId, keyFileVariable: 'ANSIBLE_SSH_KEY')]
    // Do NOT set the "ANSIBLE_SSH_KEY" here since ansible 2.19+ will then attempt to start its own ssh agent internally
    List secretVarsListDefault = []
    config.get('secretVarsList', secretVarsListDefault)
    config.get('ansibleSecretVarsList', secretVarsListDefault)

    log.debug("params=${JsonUtils.printToJsonString(params)}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}