#!/usr/bin/env groovy

/**
 * Runs 'pip install -r' with specified requirements file
 *
 * @args config A map containing configuration options:
 * - pipRequirementsFile (String, optional): Default requirements.txt
 * - pipCommand (String, optional): Default 'python3 -m pip'
 */

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map args=[:]) {

    String pipRequirementsFile=args.get("pipRequirementsFile", "requirements.txt")
    String pipCommand=args.get("pipCommand", "python3 -m pip")
//     String pipCommand=args.get("pipCommand", "pip")

    Boolean pipIgnoreCerts=args.get("pipIgnoreCerts", false)
    Boolean pipDisableVersionCheck=args.get("pipInstallIgnoreCerts", false)
    Boolean pipForceReinstall=args.get("pipForceReinstall", false)
    Boolean pipUpgradePackages=args.get("pipUpgradePackages", false)
    Boolean pipQuietMode=args.get("pipQuietMode", true)

    // pip install -q -r requirements.txt
    // python3 -m pip install --upgrade -q -r requirements.txt

    // install python packages
    List pipInstallCmdArray = ["${pipCommand} install"]

//     if (pipIgnoreCerts) {
//         pipInstallCmdArray.push("--ignore-certs")
//     }
    if (pipDisableVersionCheck) {
        pipInstallCmdArray.push("--disable-pip-version-check")
    }
    if (pipForceReinstall) {
        pipInstallCmdArray.push("--force-reinstall")
    }
    // ref: https://docs.ansible.com/ansible/latest/collections_guide/collections_installing.html
    if (pipUpgradePackages) {
        pipInstallCmdArray.push("--upgrade")
    }
    if (pipQuietMode) {
        pipInstallCmdArray.push("--quiet")
    }
    pipInstallCmdArray.push("-r ${pipRequirementsFile}")

    String pipInstallCommand = pipInstallCmdArray.join(" ")

    log.info("pipRequirementsFile=${pipRequirementsFile}")
    if (log.isLogActive(LogLevel.DEBUG)) {
        sh "${pipCommand} freeze"
        sh "${pipCommand} --version"
        sh "set -x; ls -Fla ${pipRequirementsFile}; set +x"
    }

    if (pipRequirementsFile && fileExists(pipRequirementsFile)) {
        sh "set -x; ${pipInstallCommand}; set +x"
    }
}
