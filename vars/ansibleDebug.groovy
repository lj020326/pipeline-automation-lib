#!/usr/bin/env groovy

import groovy.json.JsonOutput
import com.dettonville.pipeline.tools.ansible.Role
import com.dettonville.pipeline.tools.ansible.RoleRequirements
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.maps.MapUtils

import static com.dettonville.pipeline.utils.ConfigConstants.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

/**
 * Executes a ansible playbook with the given configuration.
 * Please refer to the documentation for details about the configuration options
 *
 * @param config The configuration used to execute the playbook
 */
void execPlaybook(Map config) {
//     Logger log = new Logger("ansible:execPlaybook -> ")
// //     log.setLevel(LogLevel.TRACE)

    Map ansibleCfg = config[ANSIBLE] ?: null

    if (ansibleCfg == null) {
        log.fatal("provided ansible configuration is null, make sure to configure properly.")
        error("provided ansible configuration is null, make sure to configure properly.")
    }

    if (ansibleCfg.containsKey(ANSIBLE_LOG_LEVEL)) {
        log.setLevel(ansibleCfg[ANSIBLE_LOG_LEVEL])
    }

    Boolean colorized = ansibleCfg[ANSIBLE_COLORIZED] != null ? ansibleCfg[ANSIBLE_COLORIZED] : true

    String installation = ansibleCfg[ANSIBLE_INSTALLATION]

    def args = []

    if (ansibleCfg[ANSIBLE_PLAYBOOK] != null) {
        args += ansibleCfg[ANSIBLE_PLAYBOOK]
    }

    if (ansibleCfg[ANSIBLE_INVENTORY] != null) {
        args += "-i ${ansibleCfg[ANSIBLE_INVENTORY]}"
    }

    if (ansibleCfg[ANSIBLE_TAGS] != null) {
        args += "-t ${ansibleCfg[ANSIBLE_TAGS]}"
    }

    if (ansibleCfg[ANSIBLE_SKIPPED_TAGS] != null) {
        args += "--skip-tags ${ansibleCfg[ANSIBLE_SKIPPED_TAGS]}"
    }

    if (ansibleCfg[ANSIBLE_VAULT_CREDENTIALS_ID] != null) {
        def vaultFile = file(credentialsId: ansibleCfg[ANSIBLE_VAULT_CREDENTIALS_ID])
        args += "--vault-password-file ${vaultFile}"
    }

    // Add private key if provided
    if (ansibleCfg.containsKey(ANSIBLE_CREDENTIALS_ID)) {
        args += "--private-key '${ansibleCfg[ANSIBLE_CREDENTIALS_ID].trim()}'"
    }

    if (ansibleCfg[ANSIBLE_LIMIT] != null) {
        args += "-l ${ansibleCfg[ANSIBLE_LIMIT]}"
    }

    if (ansibleCfg[ANSIBLE_FORKS] != null) {
        args += "-f ${ansibleCfg[ANSIBLE_FORKS]}"
    }

    if (ansibleCfg.containsKey(ANSIBLE_EXTRA_VARS)) {
        // -e @vars.json
        args += "-e '${JsonOutput.toJson(ansibleCfg[ANSIBLE_EXTRA_VARS])}'"
    }

    if (ansibleCfg.containsKey(ANSIBLE_EXTRA_PARAMETERS)) {
        args += ansibleCfg[ANSIBLE_EXTRA_PARAMETERS]
    }

    def ansibleExecCmd = (installation != null) ? "${installation}/bin/ansible-playbook" : "ansible-playbook"

    log.debug("ansible playbook command: " + "${ansibleExecCmd} ${args.join(' ')}")

    // Execute the playbook
    // The sshagent plugin provides SSH_AUTH_SOCK and SSH_AGENT_PID, so we need to pass them to the sh command
    sh(script: "export SSH_AUTH_SOCK=${env.SSH_AUTH_SOCK} && export SSH_AGENT_PID=${env.SSH_AGENT_PID} && ${ansibleExecCmd} ${args.join(' ')}", returnStatus: true)
}

/**
 * Gets info for a single galaxy role.
 *
 * @param role
 * @return
 */
Map getGalaxyRoleInfo(Role role) {
//     Logger log = new Logger("ansible:getGalaxyRoleInfo -> ")
    if (role.isGalaxyRole() == false) {
        log.debug("Role with name: " + role.getName() + " is not a galaxy role")
        return null
    }
    log.info("Getting role info for ${role.getName()}")
    String apiUrl = "https://galaxy.ansible.com/api/v1/roles/"

    //roles/?owner__username=tecris&name=maven"

    def matcher = role.getName() =~ /(.+)\\.(.+)/
    log.debug("matcher: $matcher")

    if (!matcher) {
        log.warn("unable to extract username name role name, return and to nothing")
        return null
    }

    String ownerUsername = matcher[0][1]
    String name = matcher[0][2]
    // directly reset matcher because it is not serializable
    matcher = null
    String roleApiUrl = "$apiUrl?owner__username=$ownerUsername&name=$name"
    String apiResultStr

    // execute the shell
    try {
        apiResultStr = sh(returnStdout: true, script: "curl --silent '$roleApiUrl'")
    } catch (Exception ex) {
        log.error("Unable to get role info for ${role.getName()}")
        return null
    }

    log.trace("api curl result: $apiResultStr")
    Object apiResultJson = readJSON(text: apiResultStr)
    log.trace("api json result: $apiResultJson")

    Integer size = apiResultJson.results.size()
    // we expect only one result here because username and role should only give one result
    if (size != 1) {
        log.warn("unexpected number of results for api call, size=$size")
        return null
    }

    def result = apiResultJson.results[0]
    Map roleInfo = [:]
    roleInfo.latest_version = result.latest_version
    roleInfo.download_url = result.download_url
    roleInfo.tarball = result.tarball
    return roleInfo
}
