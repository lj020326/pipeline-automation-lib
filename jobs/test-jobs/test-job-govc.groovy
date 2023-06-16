#!/usr/bin/env groovy

//@Library("pipeline-automation-lib@develop")
@Library("pipeline-automation-lib")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils

Logger.init(this, LogLevel.INFO)
Logger log = new Logger(this)

Map config=[:]

pipeline {

    agent any

    options {
        timestamps()
    }

    environment {
        GOVC_INSECURE = true
        GOVC_URL = "vcsa.dettonville.int"
    }

    stages {
        stage("Test GOVC") {
            steps {
                script {

                    withCredentials([usernamePassword(credentialsId: 'infra-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME')]) {

                        setPackerEnv()
                        echo "env.TEMPLATE_BUILD_ID=${env.TEMPLATE_BUILD_ID}"
                        echo "env.TEMPLATE_NAME=${env.TEMPLATE_NAME}"
                        echo "env.JOB_BASE_NAME=${env.JOB_BASE_NAME}"

                        // ref: https://github.com/jetbrains-infra/packer-builder-vsphere/issues/85#issuecomment-384968433
//                        config['vm-template-name'] = "TestJob-VM-GOVC"
                        config['vm-template-name'] = "${env.JOB_BASE_NAME}"
                        config['vm-template-host'] = "esx02.dettonville.int"
                        config['vm-template-datastore'] = "esx2_ds3"
                        config['vm-template-folder'] = "templates"

                        config.logLevel = "INFO"

                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        sh "govc vm.info ${config['vm-template-name']}"
                        boolean vmTemplateExists = sh(script: "govc vm.info ${config['vm-template-name']} | grep 'UUID:'", returnStatus: true) == 0
                        log.info("vmTemplateExists=${vmTemplateExists}")

                        if (vmTemplateExists) {
                            log.info("destroying existing template ${config['vm-template-name']}")
                            sh "govc vm.destroy ${config['vm-template-name']}"
                        }

                        log.info("creating VM ${env.TEMPLATE_BUILD_ID}")
//                        sh "govc vm.create -g=centos64Guest -on=false -iso=isos/CentoOS.iso -iso-datastore=DataStoreXYZ MyVM"
                        sh "govc vm.create -g=centos64Guest -on=false -ds=${config['vm-template-datastore']} -host=${config['vm-template-host']} -folder=${config['vm-template-folder']} ${env.TEMPLATE_BUILD_ID}"

                        log.info("cloning VM ${env.TEMPLATE_BUILD_ID} to ${config['vm-template-name']}")
                        sh "govc vm.clone -on=false -ds=${config['vm-template-datastore']} -vm=${env.TEMPLATE_BUILD_ID} -pool=/johnsondc/host/${config['vm-template-host']}/Resources -folder=${config['vm-template-folder']} ${config['vm-template-name']}"

                        String getVmPathCmd = "govc vm.info -json ${config['vm-template-name']} | jq '.. |.Config?.VmPathName? | select(. != null)'"
                        sh "${getVmPathCmd}"
                        String vmPath = sh(script: "${getVmPathCmd}", returnStdout: true).replaceAll('"',"")
                        vmPath = vmPath.substring(0, vmPath.lastIndexOf("/"))
                        log.info("vmPath='${vmPath}'")

                        String targetPath = "[${config['vm-template-datastore']}] ${config['vm-template-folder']}"
                        log.info("targetPath='${targetPath}'")

                        if (targetPath != vmPath) {
                            log.info("moving VM from '${vmPath}' to '${targetPath}'")
                            sh "govc vm.unregister ${config['vm-template-name']}"
                            sh "govc datastore.mv -ds=${config['vm-template-datastore']} ${config['vm-template-name']} ${config['vm-template-folder']}/${config['vm-template-name']}"
                            sh "govc vm.register -ds=${config['vm-template-datastore']} -folder=${config['vm-template-folder']} -host=${config['vm-template-host']} ${config['vm-template-folder']}/${config['vm-template-name']}/${config['vm-template-name']}.vmx"
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

