#!/usr/bin/env groovy

/**
 * Runs 'ansible-galaxy collection install' with specified arguments
 *
 * @args config A map containing configuration options:
 * - ansibleCollectionsRequirements (String, optional): Default null
 * - ansibleRolesRequirements (String, optional): Default null
 * - ansibleGalaxyIgnoreCerts (Boolean, optional): Default false
 * - ansibleGalaxyForceOpt (Boolean, optional): Default false
 * - ansibleGalaxyUpgradeOpt (Boolean, optional): Default false
 * - ansibleGalaxyEnvVarsList (List, optional): Default []
 * - galaxySecretVarsList (List, optional): Default []
 * - ansibleGalaxyCmd (String, optional): Default "ansible-galaxy"
 */

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map args=[:]) {

    String ansibleCollectionsRequirements=args.get("ansibleCollectionsRequirements")
    String ansibleRolesRequirements=args.get("ansibleRolesRequirements")

    Boolean ansibleGalaxyIgnoreCerts=args.get("ansibleGalaxyIgnoreCerts", false)
    Boolean ansibleGalaxyForceOpt=args.get("ansibleGalaxyForceOpt", false)
    Boolean ansibleGalaxyUpgradeOpt=args.get("ansibleGalaxyUpgradeOpt", false)
    List ansibleGalaxyEnvVarsList=args.get("ansibleGalaxyEnvVarsList", [])
    List galaxySecretVarsList=args.get("galaxySecretVarsList", [])
    String ansibleGalaxyCmd=args.get("ansibleGalaxyCmd", "ansible-galaxy")

    // install galaxy roles
    List ansibleGalaxyArgList = []
    if (ansibleGalaxyIgnoreCerts) {
        ansibleGalaxyArgList.push("--ignore-certs")
    }
    if (ansibleGalaxyForceOpt) {
        ansibleGalaxyArgList.push("--force")
    }
    // ref: https://docs.ansible.com/ansible/latest/collections_guide/collections_installing.html
    if (ansibleGalaxyUpgradeOpt) {
        ansibleGalaxyArgList.push("--upgrade")
//         ansibleGalaxyArgList.push("--clear-response-cache")
    }
    String ansibleGalaxyArgs = ansibleGalaxyArgList.join(" ")

    runWithConditionalEnvAndCredentials(ansibleGalaxyEnvVarsList, galaxySecretVarsList) {
        sh "${ansibleGalaxyCmd} collection list"

        if (log.isLogActive(LogLevel.DEBUG)) {
            sh "${ansibleGalaxyCmd} --version"
        }

        log.info("ansibleCollectionsRequirements=${ansibleCollectionsRequirements}")

//         if (log.isLogActive(LogLevel.DEBUG)) {
//     //         sh "set -x; ls -Fla ${ansibleCollectionsRequirements}; set +x"
//             sh """
//                 pwd;
//                 find tests/ -maxdepth 2 -mindepth 1 -type f -print
//                 ls -Fla "${ansibleCollectionsRequirements}"
//             """
//         }
        if (ansibleCollectionsRequirements && fileExists(ansibleCollectionsRequirements)) {
            sh "set -x; ${ansibleGalaxyCmd} collection install ${ansibleGalaxyArgs} -r ${ansibleCollectionsRequirements} > /dev/null; set +x"
        }

        if (ansibleRolesRequirements && fileExists(config.ansibleRolesRequirements)) {
            log.debug("ansibleRolesRequirements=${ansibleRolesRequirements}")
            sh "set -x; ${ansibleGalaxyCmd} install ${ansibleGalaxyArgs} -r ${ansibleRolesRequirements} > /dev/null; set +x"
        }
    }
}
