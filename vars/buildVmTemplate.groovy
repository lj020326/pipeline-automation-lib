#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.Utilities

// ref: https://github.com/jenkinsci/packer-plugin/issues/20#issuecomment-469681596

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.JsonUtils
import groovy.json.*
//import groovy.json.JsonOutput

def call(Map params=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)
    String packerTool = "packer-1.6.2" // Name of Packer Installation

    Map config=[:]
    boolean vmTemplateExists = false

//    Map config=loadPipelineConfig(log, params)
//    String agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)

    pipeline {

        agent {
//            label agentLabel as String
            label "packer"
        }

        tools {
            "biz.neustar.jenkins.plugins.packer.PackerInstallation" "$packerTool"
        }

        environment {
            GOVC_INSECURE = true
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            disableConcurrentBuilds()
            timestamps()
            timeout(time: 2, unit: 'HOURS')
        }

        stages {

            stage("Initialize") {
                steps {
                    script {
                        config=loadPipelineConfig(log, params)
                        log.info("config=${JsonUtils.printToJsonString(config)}")
                    }
                }
            }

            stage("Pre-check if template already exists") {
                environment {
                    GOVC_URL = "${config['vcenter-host']}"
                }
                steps {
                    script {
                        log.info("checking if template already exists...")
                        withCredentials([usernamePassword(credentialsId: 'dcapi-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME')]) {
                            sh "govc vm.info ${config['vm-template-name']}"
//                            sh "govc -u=${config['vcenter-host']} vm.info ${config['vm-template-name']}"
                            vmTemplateExists = sh(script: "govc vm.info ${config['vm-template-name']} | grep 'UUID:'", returnStatus: true) == 0
                        }
                        log.info("initial check if template already exists=>${vmTemplateExists}")

                        if (vmTemplateExists) {
                            log.info("vmTemplateExists=${vmTemplateExists} - skipping build")
                        }

                    }
                }
            }

            stage('Fetch OS image') {
                when {
                    expression { !vmTemplateExists }
                }
                environment {
                    GOVC_URL = "${config['vcenter-host']}"
                }

                steps {
                    script {
//                        String govcCmd = "govc datastore.ls -ds=${config['vm-remote-cache-datastore']} ${config['vm-iso-file-dir']} | grep ${config['iso-file']}"
//                        boolean imageExists = sh(script: govcCmd, returnStatus: true)==0
                        boolean imageExists = sh(script: "ls -Fla ${config['vm-data-dir']}/${config['vm-iso-file-dir']}/ | grep ${config['iso-file']} ", returnStatus: true)==0

                        if (!imageExists) {
                            sh """
                            ansible-playbook \
                              --inventory-file localhost, \
                              -e fetch_images='"[${JsonOutput.toJson(config.imageInfo)}]"' \
                              fetch-osimages.yml
                            """

//                            sh "govc datastore.upload -ds=${config['vm-remote-cache-datastore']} /data/osimages/${config['iso-file']} ${config['vm-iso-file-dir']}/${config['iso-file']}"
//                            sh "cp -np /data/osimages/${config['iso-file']} /vmware/${config['vm-iso-file-dir']}/"
                        }
                    }
                }
            }

            stage("Run Packer to build template") {
                when {
                    expression { !vmTemplateExists && !config["skip-packer-build"]?.toBoolean() }
                }
                environment {
                    GOVC_URL = "${config['vcenter-host']}"
                }

                steps {

                    script {

                        dir("${config['build-dir']}/${env.JOB_BASE_NAME}") {
//                        dir("${env.WORKSPACE}/${env.JOB_BASE_NAME}") {

                            // ref: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html-single/installation_guide/index#s2-kickstart2-boot-media
                            // ref: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html-single/installation_guide/index#s2-kickstart2-networkbased
                            if (config["build-type"] == "vsphere-iso-nfs") {
                                sh "cp -p ${config['vm-init-file']} ${config['vm-init-dir']}/${config['vm-init-file']}"
                            }

                            // ref: https://docs.cloudbees.com/docs/cloudbees-ci/latest/cloud-secure-guide/injecting-secrets
                            // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
                            // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
                            List secretVars=[
                                    string(credentialsId: 'vmware-vcenter-password', variable: 'VMWARE_VCENTER_PASSWORD'),
                                    string(credentialsId: 'vmware-esxi-password', variable: 'VMWARE_ESXI_PASSWORD'),
                                    string(credentialsId: 'packer-ssh-password', variable: 'PACKER_SSH_PASSWORD'),
                                    string(credentialsId: 'vm-root-password', variable: 'VM_ROOT_PASSWORD'),
                                    string(credentialsId: 'ansible-vault-password', variable: 'ANSIBLE_VAULT_PASSWORD'),
                            ]

                            withCredentials(secretVars) {

                                // ref: https://vsupalov.com/packer-ami/
                                // ref: https://blog.deimos.fr/2015/01/16/packer-build-multiple-images-easily/
                                // ref: https://github.com/hashicorp/packer/pull/7184
                                sh """
                                ${tool packerTool}/packer build \
                                    -only ${config['build-type']} \
                                    -on-error=abort \
                                    -var-file=build-vars.json \
                                    -debug \
                                    ${env.WORKSPACE}/${config['build-dir']}/builder-config.json
                                """
                            }

                        }
                    }
                }
            }

//            stage("Deploy Template to $config['vm-template-datastore'] $config['vm-template-folder']") {
            stage("Deploy Template") {
                when {
                    expression { !vmTemplateExists && !config["skip-packer-build"]?.toBoolean() }
                }
                environment {
                    GOVC_URL = "${config['vcenter-host']}"
                }

                steps {
                    script {

                        withCredentials([usernamePassword(credentialsId: 'dcapi-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME')]) {

                            sh "govc vm.info ${config['vm-template-name']}"
                            vmTemplateExists = sh(script: "govc vm.info ${config['vm-template-name']} | grep 'UUID:'", returnStatus: true) == 0
                            log.info("vmTemplateExists=${vmTemplateExists}")

                            if (vmTemplateExists) {
                                log.info("destroying existing template ${config['vm-template-name']}")
                                sh "govc vm.destroy ${config['vm-template-name']}"
                            }

//                            sh "govc vm.clone -ds=${config['vm-template-datastore']} -vm=${env.TEMPLATE_BUILD_ID} -pool=/johnsondc/host/${config['vm-template-host']}/Resources -folder=${config['vm-template-folder']} -template ${config['vm-template-name']} >/dev/null"
                            sh "govc vm.clone -ds=${config['vm-template-datastore']} -vm=${env.TEMPLATE_BUILD_ID} -host=${config['vm-template-host']} -folder=${config['vm-template-folder']} -template ${config['vm-template-name']} >/dev/null"

                            String getVmPathCmd = "govc vm.info -json ${config['vm-template-name']} | jq '.. |.Config?.VmPathName? | select(. != null)'"
                            sh "${getVmPathCmd}"
                            String vmPath = sh(script: "${getVmPathCmd}", returnStdout: true).replaceAll('"',"")
//                            String vmDatastore = vmPath.split("/")[-1].replaceAll('([|])+','')
//                            String vmDatastore = vmPath.split(" ")[-1].replaceAll('([|])+','')
                            String vmDatastore = vmPath.split(" ")[0].replaceAll('([|])+','')
                            vmPath = vmPath.substring(0, vmPath.lastIndexOf("/"))
                            log.info("vmDatastore=${vmDatastore} vmPath='${vmPath}'")

                            String targetPath = "[${config['vm-template-datastore']}] ${config['vm-template-folder']}"
                            log.info("targetPath='${targetPath}'")

                            if (vmPath != targetPath) {
                                log.info("moving VM from '${vmPath}' to '${targetPath}'")
                                sh "govc vm.unregister ${config['vm-template-name']}"
                                sh "govc datastore.mv -ds=${config['vm-template-datastore']} ${config['vm-template-name']} ${config['vm-template-folder']}/${config['vm-template-name']}"
                                sh "govc vm.register -template=true -ds=${config['vm-template-datastore']} -folder=${config['vm-template-folder']} -host=${config['vm-template-host']} ${config['vm-template-folder']}/${config['vm-template-name']}/${config['vm-template-name']}.vmtx"
                            }
                            sh "govc datastore.ls -ds=${config['vm-template-datastore']} ${config['vm-template-folder']}"
                            log.info("removing temporary template build ${env.TEMPLATE_BUILD_ID}")
                            sh "govc vm.destroy ${env.TEMPLATE_BUILD_ID}"
                        }
                    }
                }
            }

        }
    }

}

//@NonCPS
Map loadPipelineConfig(Logger log, Map params) {
    String logPrefix="loadPipelineConfig():"
    Map config = [:]

    List jobParts = JOB_NAME.split("/")
    log.info("${logPrefix} jobParts=${jobParts}")
    config.jobBaseFolderLevel = config.jobBaseFolderLevel ?: 4
    config['build-dir']="packer_templates"

    List buildTagList = env.BUILD_TAG.split("-")
    buildTagList[-1] = env.BUILD_NUMBER.toString().padLeft(4, '0')

    // ref: https://mrhaki.blogspot.com/2011/09/groovy-goodness-take-and-drop-items.html
    buildTagList = buildTagList.drop(config.jobBaseFolderLevel)
    templateBuildTag = buildTagList.join("-")

    log.info("${logPrefix} templateBuildTag=${templateBuildTag}")
    env.TEMPLATE_BUILD_ID = templateBuildTag

    log.info("${logPrefix} TEMPLATE_BUILD_ID=${env.TEMPLATE_BUILD_ID}")

    String buildConfigFile = "./${config['build-dir']}/builder-config.json"
    if (fileExists(buildConfigFile)) {
        Map buildConfig = readJSON file: buildConfigFile
        config = MapMerge.merge(config, buildConfig.variables)
    } else {
        String message = "${logPrefix} buildConfigFile [${buildConfigFile}] not found"
        log.error("${message}")
        throw message
    }

    String buildVarsFile = "./${config['build-dir']}/${env.JOB_BASE_NAME}/build-vars.json"
    if (fileExists(buildVarsFile)) {
        Map buildVars = readJSON file: buildVarsFile
        config = MapMerge.merge(config, buildVars)
        log.debug("${logPrefix} buildVars=${JsonUtils.printToJsonString(buildVars)}")
    } else {
        String message = "${logPrefix} buildVarsFile [${buildVarsFile}] not found"
        log.error("${message}")
        throw message
    }

    // copy immutable params maps to mutable config map
    // config = MapMerge.merge(config, params)
    params.each { key, value ->
        log.debug("${logPrefix} params[${key}]=${value}")
        key= Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"packer")
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    Map imageInfo = [:]
    imageInfo['name'] = "${env.JOB_BASE_NAME}"

    String isoUrl = config['iso-url']
    // ref: https://stackoverflow.com/questions/605696/get-file-name-from-url
    String isoFile = isoUrl.substring(isoUrl.lastIndexOf('/') + 1, isoUrl.length());
    imageInfo['iso-url'] = isoUrl
    imageInfo['iso-file'] = isoFile
    imageInfo['iso-checksum'] = config['iso-checksum']
    config['image-info'] = imageInfo

    log.debug("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}
