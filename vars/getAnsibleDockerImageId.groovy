#!/usr/bin/env groovy

/**
 * Gets ansible image pertaining to specified type, *a single* Ansible core, and *a single* Python version
 *
 * @args config A map containing configuration options:
 * - ansibleVersion (String, optional): A single Ansible core version to test against.
 * - pythonVersion (String, optional): A single Python version to test under.
 * - dockerRegistry (String, optional): docker registry uri (e.g., registry.example.int:5000)
 * - dockerImageName (String, optional): docker image name
 */

// Helper function to get Ansible Docker image tag
String call(Map args=[:]) {
    String ansibleVersion=args.get("ansibleVersion", "2.19")
    String pythonVersion=args.get("pythonVersion", "3.13")
    String dockerRegistry=args.get("dockerRegistry", "media.johnson.int:5000")
    String dockerImageName=args.get("dockerImageName", "ansible/ansible-test")

    String tag = ""
    if (ansibleVersion == 'latest') {
        tag = "latest-py${pythonVersion}"
    } else if (ansibleVersion == 'devel') {
        tag = "devel-py${pythonVersion}"
    } else {
        tag = "stable-${ansibleVersion}-py${pythonVersion}"
    }
    return "${dockerRegistry}/${dockerImageName}:${tag}"
}
