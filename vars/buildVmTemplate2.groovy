#!/usr/bin/env groovy

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
    Map config = [:]

    boolean vmTemplateExists = false

    pipeline {

//        agent any
        agent {
//            label "docker"
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
//                        setPackerEnv()

                        log.info("JOB_BASE=${env.JOB_NAME}")
                        log.info("JOB_BASE_NAME=${env.JOB_BASE_NAME}")
                        log.info("BUILD_TAG=${env.BUILD_TAG}")

                        List jobParts = JOB_NAME.split("/")
                        log.info("${logPrefix} jobParts=${jobParts}")
                        config.jobBaseFolderLevel = config.jobBaseFolderLevel ?: 4
                        config['build-dir']="build-config-new"

                        int startIdx = config.jobBaseFolderLevel + 1
//                            int endIdx = jobParts.size() - 1
                        int endIdx = jobParts.size() - 2

//                            config['build-distribution'] = jobParts[config.jobBaseFolderLevel]
                        config['build-distribution'] = jobParts[-3]
                        config['build-release'] = jobParts[-2]
                        config['build-type'] = jobParts[-1]

                        //    String[] buildTemplateParts = jobParts[startIdx..<jobParts.size()-1]
                        List buildTemplateParts = []
                        for (int i = startIdx; i < endIdx; i++) {
                            buildTemplateParts.add(jobParts[i])
                        }
//                            buildTemplateParts.remove(0)

                        log.info("buildTemplateParts=${buildTemplateParts}")

                        config['build-distribution-config-dir'] = buildTemplateParts[0..-1].join("/")
                        config['build-release-config-dir'] = buildTemplateParts.join("/")

                        log.info("build-distribution-config-dir=${config['build-distribution-config-dir']}")
                        log.info("build-release-config-dir=${config['build-release-config-dir']}")

                        List buildTagList = env.BUILD_TAG.split("-")
                        buildTagList[-1] = env.BUILD_NUMBER.toString().padLeft(4, '0')

                        // ref: https://mrhaki.blogspot.com/2011/09/groovy-goodness-take-and-drop-items.html
                        buildTagList = buildTagList.drop(3)
                        templateBuildTag = buildTagList.join("-")

                        //                        echo "templateBuildTag=${templateBuildTag}"
                        env.TEMPLATE_BUILD_ID = templateBuildTag

                        log.info("env.TEMPLATE_BUILD_ID=${env.TEMPLATE_BUILD_ID}")
                        //                        log.info("env.TEMPLATE_NAME=${env.TEMPLATE_NAME}")

                        //                        config['vm_name'] = "${env.TEMPLATE_NAME}"
                        config.logLevel = "INFO"

//                            -var-file=box_info.json -var-file=template.json ../../ubuntu-server-live-installer.json

                        Map buildConfig = readJSON file: "./${config['build-dir']}/build-config.json"
                        config = MapMerge.merge(config, buildConfig.variables)

                        Map distBuildConfig = readJSON file: "./${config['build-dir']}/${config['build-distribution-config-dir']}/build-config.json"
                        config = MapMerge.merge(config, distBuildConfig.variables)
                        log.info("buildConfig=${JsonUtils.printToJsonString(buildConfig)}")

                        Map boxInfoConfig = readJSON file: "./${config['build-dir']}/${config['build-release-config-dir']}/box_info.json"
                        config = MapMerge.merge(config, boxInfoConfig)
                        log.debug("boxInfoConfig=${JsonUtils.printToJsonString(boxInfoConfig)}")

                        Map templateConfig = readJSON file: "./${config['build-dir']}/${config['build-release-config-dir']}/template.json"
                        config = MapMerge.merge(config, templateConfig)
                        log.debug("templateConfig=${JsonUtils.printToJsonString(templateConfig)}")

                        config = MapMerge.merge(config, params)

                        Map imageInfo = [:]
                        imageInfo['name'] = "${env.JOB_BASE_NAME}"
                        //                        imageInfo['iso-url'] = config['iso-url']
                        //                        imageInfo['iso-file'] = config['iso-file']

                        String isoUrl = config['iso-url']
                        // ref: https://stackoverflow.com/questions/605696/get-file-name-from-url
                        String isoFile = isoUrl.substring(isoUrl.lastIndexOf('/') + 1, isoUrl.length());
                        imageInfo['iso-url'] = isoUrl
                        imageInfo['iso-file'] = isoFile
                        imageInfo['iso-checksum'] = config['iso-checksum']
                        config['image-info'] = imageInfo

                        log.setLevel(config.logLevel)

                        log.info("config=${JsonUtils.printToJsonString(config)}")
                    }
                }
            }

            stage("Pre-check if template already exists") {
                environment {
                    GOVC_URL = "${config['vcenter_host']}"
                }
                steps {
                    script {
                        log.info("checking if template already exists...")
                        withCredentials([usernamePassword(credentialsId: 'dcapi-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME')]) {
                            sh "govc vm.info ${config['vm_name']}"
//                            sh "govc -u=${config['vcenter-host']} vm.info ${config['vm_name']}"
                            vmTemplateExists = sh(script: "govc vm.info ${config['vm_name']} | grep 'UUID:'", returnStatus: true) == 0
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
                        boolean imageExists = sh(script: "ls -Fla ${config['vm_data_dir']}/${config['vm_iso_file_dir']}/ | grep ${config['iso-file']} ", returnStatus: true)==0

                        if (!imageExists) {
                            sh """
                            ansible-playbook \
                              --inventory-file localhost, \
                              -e fetch_images='"[${JsonOutput.toJson(config['image-info'])}]"' \
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

//                        dir("${env.WORKSPACE}/${config['build-dir']}/${env.JOB_BASE_NAME}") {
//                        dir("${env.WORKSPACE}/${config['build-dir']}/${config['build-release-config-dir']}") {
//                        dir("${env.WORKSPACE}/${config['build-dir']}") {
                        dir("${config['build-dir']}") {

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

                                // -var-file=box_info.json -var-file=template.json ../../build-config.json

                                // ref: https://vsupalov.com/packer-ami/
                                // ref: https://blog.deimos.fr/2015/01/16/packer-build-multiple-images-easily/
                                // ref: https://github.com/hashicorp/packer/pull/7184
                                sh """
                                ${tool packerTool}/packer build -only ${config['build-type']} \
                                    -on-error=abort \
                                    -var-file=build-config.json \
                                    -var-file=${config['build-release-config-dir']}/server/box_info.json \
                                    -var-file=${config['build-release-config-dir']}/server/template.json \
                                    -debug \
                                    ${env.WORKSPACE}/${config['build-dir']}/${config['build-distribution-config-dir']}/build-config.json
                                """
                            }

                        }
                    }
                }
            }

//            stage("Move Template to $config['vm-template-datastore'] $config['vm-template-folder']") {
            stage("Move Template") {
                when {
                    expression { !vmTemplateExists && !config["skip-packer-build"]?.toBoolean() }
                }
                environment {
                    GOVC_URL = "${config['vcenter-host']}"
                }

                steps {
                    script {

                        withCredentials([usernamePassword(credentialsId: 'dcapi-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME')]) {

                            sh "govc vm.info ${config['vm_name']}"
                            vmTemplateExists = sh(script: "govc vm.info ${config['vm_name']} | grep 'UUID:'", returnStatus: true) == 0
                            log.info("vmTemplateExists=${vmTemplateExists}")

                            if (vmTemplateExists) {
                                log.info("destroying existing template ${config['vm_name']}")
                                sh "govc vm.destroy ${config['vm_name']}"
                            }

//                            sh "govc vm.clone -ds=${config['vm-template-datastore']} -vm=${env.TEMPLATE_BUILD_ID} -pool=/johnsondc/host/${config['vm-template-host']}/Resources -folder=${config['vm-template-folder']} -template ${config['vm_name']} >/dev/null"
                            sh "govc vm.clone -ds=${config['vm-template-datastore']} -vm=${env.TEMPLATE_BUILD_ID} -host=${config['vm-template-host']} -folder=${config['vm-template-folder']} -template ${config['vm_name']} >/dev/null"

                            String getVmPathCmd = "govc vm.info -json ${config['vm_name']} | jq '.. |.Config?.VmPathName? | select(. != null)'"
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
                                sh "govc vm.unregister ${config['vm_name']}"
                                sh "govc datastore.mv -ds=${config['vm-template-datastore']} ${config['vm_name']} ${config['vm-template-folder']}/${config['vm_name']}"
                                sh "govc vm.register -template=true -ds=${config['vm-template-datastore']} -folder=${config['vm-template-folder']} -host=${config['vm-template-host']} ${config['vm-template-folder']}/${config['vm_name']}/${config['vm_name']}.vmtx"
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