#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call() {

//     String packerTool = "packer-1.6.2" // Name of Packer Installation
//     String packerTool = "packer-1.8.6" // Name of Packer Installation

    Map config=[:]
    boolean vmTemplateExists = false

//    Map config = loadPipelineConfig(params)
//    String agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)

    List paramList = []

    Map paramMap = [
        replaceExistingTemplate  : booleanParam(defaultValue: false, description: "Replace Existing Template?", name: 'ReplaceExistingTemplate')
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList)
    ])

    params.each { key, value ->
        key=Utilities.decapitalize(key)
        log.info("key=${key} value=${value}")
        if (value!="") {
            config[key] = value
        }
    }

    pipeline {

        agent {
            label "packer"
        }
        // ref: https://stackoverflow.com/questions/45348761/jenkins-pipeline-how-do-i-use-the-tool-option-to-specify-a-custom-tool
        environment {
            PACKER_HOME = tool name: 'packer-local', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
            PACKER_CONFIG_DIR = "${env.JENKINS_HOME}/.config/packer/"
            CHECKPOINT_DISABLE = 1
            GOVC_HOME = tool name: 'govc-local', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
            GOVC_INSECURE = true
            PATH = "${env.GOVC_HOME}/bin:${env.PACKER_HOME}/bin:${env.PATH}"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            skipDefaultCheckout(false)
            disableConcurrentBuilds()
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }

        stages {

            stage("Initialize") {
                steps {
                    script {

                        // ref: https://stackoverflow.com/questions/60756020/print-environment-variables-sorted-by-name-including-variables-with-newlines
                        sh "export -p | sed 's/declare -x //' | sed 's/export //'"

                        config=loadPipelineConfig(params)
                        log.info("config=${JsonUtils.printToJsonString(config)}")

                        // ref: https://stackoverflow.com/questions/25785/delete-all-but-the-most-recent-x-files-in-bash
                        // ref: https://stackoverflow.com/questions/22407480/command-to-list-all-files-except-dot-and-dot-dot
//                         sh "cd /tmp/ && ls -Art1 | grep packer | tail -n +5 | xargs -I {} rm -fr -- {} || true"
//                         sh "cd /tmp/ && ls -Art1 | tail -n +${config.tmpDirMaxFileCount} | xargs -I {} rm -fr -- {} || true"
                        sh "cd /tmp/ && find . -mtime ++${config.tmpDirMaxAge} -type d | xargs rm -f -r;"

                    }
                }
            }

            stage("Pre-check if template already exists") {
                environment {
                    GOVC_URL = "${config.vcenter_host}"
                }
                steps {
                    script {

                        withCredentials(config.secret_vars) {
                            log.info("Checking if template root folder at ${config.vm_template_root_folder} exists...")
//                             sh "govc folder.info ${config.vm_template_root_folder}"
                            vmTemplateFolderExists = sh(script: "govc folder.info ${config.vm_template_root_folder} | grep 'Path:'", returnStatus: true) == 0
                            log.info("vmTemplateFolderExists=>${vmTemplateFolderExists}")

                            if (!vmTemplateFolderExists) {
                                log.info("Create template folder ${config.vm_template_root_folder}")
                                sh "govc folder.create ${config.vm_template_root_folder}"
                            }

                            log.info("Checking if template build folder at ${config.vm_template_build_folder} exists...")
//                             sh "govc folder.info ${config.vm_template_build_folder}"
                            vmTemplateFolderExists = sh(script: "govc folder.info ${config.vm_template_build_folder} | grep 'Path:'", returnStatus: true) == 0
                            log.info("vmTemplateFolderExists=>${vmTemplateFolderExists}")

                            if (!vmTemplateFolderExists) {
                                log.info("Create template folder ${config.vm_template_build_folder}")
                                sh "govc folder.create ${config.vm_template_build_folder}"
                            }

                            log.info("Ensure datastore template directory ${config.vm_build_folder} already exists...")
                            sh "govc datastore.mkdir -p -ds=${config.vm_template_datastore} ${config.vm_build_folder}"

                            log.info("Checking if template already exists...")
                            vmTemplateExists = doesVmTemplateExist(this, log, config.vm_template_name)

                            log.info("initial check if template already exists=>${vmTemplateExists}")
                        }
                    }
                }
            }

            stage('Fetch OS image') {
                when {
                    allOf {
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
                        expression { config?.fetch_os_image && config.fetch_os_image.toBoolean() }
                    }
                }
                steps {
                    script {
//                         String vmware_images_dir = "${config.vm_data_dir}/${config.iso_base_dir}/${config.iso_dir}"
//                         log.info("vmware_images_dir=>${vmware_images_dir}")

                        boolean imageExists = sh(script: "ls -Fla ${config.os_image_dir}/ | grep ${config.iso_file} ",
                            returnStatus: true)==0

                        if (!imageExists) {
                            List fetchImageCmd = []
                            fetchImageCmd.push("ansible-playbook")
                            fetchImageCmd.push("--inventory-file localhost,")
                            if (config?.vmware_iso_nfs_local_mounted && config.vmware_iso_nfs_local_mounted.toBoolean()) {
                                fetchImageCmd.push("-e fetch_os_images__vmware_nfs_iso_locally_mounted='true'")
                                fetchImageCmd.push("-e fetch_os_images__vmware_images_dir='${config.vmware_images_dir}'")
                            }
                            fetchImageCmd.push("-e fetch_os_images__osimage_dir='${config.os_image_dir}'")
                            fetchImageCmd.push("-e fetch_images='[${JsonOutput.toJson(config.image_info)}]'")
                            fetchImageCmd.push("${config.ansible_fetch_images_playbook}")

                            String fetchImageCmdString = fetchImageCmd.join(' ')
                            sh(fetchImageCmdString)
                        }
                    }
                }
            }

            stage('Upload OS image to DC') {
                when {
                    allOf {
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
                        expression { config?.vmware_iso_nfs_local_mounted && !config.vmware_iso_nfs_local_mounted.toBoolean() }
                    }
                }
                environment {
                    GOVC_URL = "${config.vcenter_host}"
                }
                steps {
                    script {
                        withCredentials(config.secret_vars) {
                            String datastoreLsCmd = """
                                govc datastore.ls \
                                 -ds=${config.vm_iso_datastore} \
                                 ${config.iso_base_dir}/${config.iso_dir}/${config.iso_file} \
                                 | grep "${config.iso_file}"
                            """
                            Boolean vmIsoExists = sh(script: "${datastoreLsCmd}", returnStatus: true) == 0
                            log.info("vmIsoExists=${vmIsoExists}")

                            if (!vmIsoExists) {
                                log.info("uploading ISO ${config.iso_file} to ${config.vm_iso_datastore}")

                                sh """
                                govc datastore.upload \
                                 -ds=${config.vm_iso_datastore} \
                                 ${config.os_image_dir}/${config.iso_file} \
                                 ${config.iso_base_dir}/${config.iso_dir}/${config.iso_file}
                                 """
                            }
                        }
                    }
                }
            }

            stage("Run Packer validate") {
                when {
                    allOf {
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
//                         expression { config?.skip_packer_build && !config.skip_packer_build.toBoolean() }
                    }
                }
                steps {

                    script {

                        dir("${config.build_dir}") {

                            // ref: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html-single/installation_guide/index#s2-kickstart2-boot-media
                            // ref: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html-single/installation_guide/index#s2-kickstart2-networkbased
                            if (config.builder_type == "vsphere-iso-nfs") {
                                sh "cp -p ${config.vm_init_file} ${config.vm_init_dir}/${config.vm_init_file}"
                            }

                            log.debug("initialize packer")

                            sh "packer init ${config.build_config}"

                            // ref: https://vsupalov.com/packer-ami/
                            // ref: https://blog.deimos.fr/2015/01/16/packer-build-multiple-images-easily/
                            // ref: https://github.com/hashicorp/packer/pull/7184
                            List packerCmdArgList = getPackerCommandArgList("validate", config)
                            packerCmdString = packerCmdArgList.join(" ")
                            log.debug("packerCmdString=${packerCmdString}")

                            withCredentials(config.secret_vars) {
                                sh(packerCmdString)
                            }
                        }
                    }
                }
            }

            stage("Run Packer to build template") {
                when {
                    allOf {
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
//                         expression { config?.skip_packer_build && !config.skip_packer_build.toBoolean() }
                    }
                }
                environment {
                    GOVC_URL = "${config.vcenter_host}"
                }

                steps {

                    script {

                        dir("${config.build_dir}") {

                            // ref: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html-single/installation_guide/index#s2-kickstart2-boot-media
                            // ref: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/6/html-single/installation_guide/index#s2-kickstart2-networkbased
                            if (config.builder_type == "vsphere-iso-nfs") {
                                sh "cp -p ${config.vm_init_file} ${config.vm_init_dir}/${config.vm_init_file}"
                            }

                            // ref: https://vsupalov.com/packer-ami/
                            // ref: https://blog.deimos.fr/2015/01/16/packer-build-multiple-images-easily/
                            // ref: https://github.com/hashicorp/packer/pull/7184
                            List packerCmdArgList = getPackerCommandArgList("build", config)
                            String packerCmdString = packerCmdArgList.join(" ")
                            log.debug("packerCmdString=${packerCmdString}")

                            withCredentials(config.secret_vars) {
                                if (config?.skip_packer_build && !config.skip_packer_build.toBoolean()) {
                                    sh(packerCmdString)
                                }
                                log.info("packer build complete - check build template exists for ${config.template_build_name}")
                                boolean vmBuildTemplateExists = doesVmTemplateExist(this, log, config.template_build_name)

                                log.info("vmBuildTemplateExists=${vmBuildTemplateExists}")

                                if (config?.skip_packer_build && !config.skip_packer_build.toBoolean()) {
                                    if (vmBuildTemplateExists) {
                                        currentBuild.result = 'SUCCESS'
                                    } else {
                                        currentBuild.result = 'FAILURE'
                                        String message = "build template [${config.template_build_name}] not found"
                                        log.error(message)
                                        throw new RuntimeException(message)
                                    }
                                }

                                int retryAttempt = 0
                                String result = ""
                                retry(count: 5) {
                                    try {

                                        vmTemplateExists = doesVmTemplateExist(this, log, config.vm_template_name, false)

                                        log.info("vmTemplateExists=${vmTemplateExists}")

                                        if (vmTemplateExists) {
                                            if (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) {
                                                log.info("destroying existing template ${config.vm_template_name}")
                                                // ref: https://stackoverflow.com/questions/73473059/retry-with-condition-jenkins-pipeline
                                                // ref: https://stackoverflow.com/questions/36422888/how-do-i-implement-a-retry-option-for-failed-stages-in-jenkins-pipelines
                                                if (retryAttempt > 0) {
                                                    sleep(time: 60, unit: 'SECONDS')
                                                }
                                                retryAttempt += 1
                                                result = sh(script: "govc vm.destroy ${config.vm_template_name} 2>&1", returnStdout: true)
                                            } else {
                                                log.error("FOLLOWING CONDITION SHOULD NOT EXIST!")
                                                log.error("(vmTemplateExists == true) AND (replaceExistingTemplate == false)!")
                                            }
                                        }
                                    } catch(e) {
                                        log.warn("result = ${result}")
                                        log.warn("An error occurred: ${e}")
                                        if (e.getMessage() ==~ "govc: The object 'vim.VirtualMachine:vm-.*' has already been deleted or has not been completely created") {
                                            sh "govc vm.unregister ${vm_template_name}"
                                            // Let's error out the the condition is true which will trigger a retry
                                            throw e
                                        }
                                        log.error("Condition failed, means no retry, so complete the Job")
                                        currentBuild.result = 'FAILURE'
                                    }
                                }

                                vmTemplateFolderExists = sh(script: "govc folder.info ${config.vm_template_deploy_folder} | grep 'Path:'", returnStatus: true) == 0
                                log.info("vmTemplateFolderExists=>${vmTemplateFolderExists}")

                                if (!vmTemplateFolderExists) {
                                    log.info("Create template folder ${config.vm_template_deploy_folder}")
                                    sh "govc folder.create ${config.vm_template_deploy_folder}"
                                }
                                log.info("Ensure datastore template directory ${config.vm_deploy_folder} exists")
                                sh "govc datastore.mkdir -p -ds=${config.vm_template_datastore} ${config.vm_deploy_folder}"

                                log.info("Clone template to deploy directory [${config.vm_deploy_folder}]")
                                try {
                                    sh """
                                        govc vm.clone \
                                        -ds=${config.vm_template_datastore} \
                                        -vm=${config.template_build_name} \
                                        -host=${config.vm_template_host} \
                                        -folder=${config.vm_template_deploy_folder} \
                                        -on=false -template=true \
                                        ${config.vm_template_name} >/dev/null
                                     """
                                } catch (Exception ex) {
                                    log.error("govc vm.clone error: " + ex.getMessage())
                                    throw ex
                                }

                                log.info("Remove temporary template build ${config.template_build_name}")
                                sh "govc vm.destroy ${config.template_build_name}"

                            }

                        }
                    }
                }
            }

            stage("Export Template to ovf") {
                when {
                    allOf {
                        expression { currentBuild.result == 'SUCCESS' }
                        expression { config?.import_ovf_to_dc2 && config.import_ovf_to_dc2.toBoolean() }
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
                    }
                }
                environment {
                    GOVC_URL = "${config.vcenter_host}"
                }
                steps {
                    script {
                        log.info("Export template to ovf [${config.vm_template_dir}]")
                        sh "mkdir -p ${config.vm_template_dir}"
                        withCredentials(config.secret_vars) {
                            try {
                                sh """
                                    govc export.ovf -f=true -i=false \
                                        -dc=${config.vcenter_datacenter} \
                                        -vm ${config.vm_template_name} \
                                        ${config.vm_template_dir} >/dev/null
                                """
                                log.info("Export template ovf complete")
                            } catch (Exception ex) {
                                log.error("govc export.ovf error: " + ex.getMessage())
                                throw ex
                            }

                        }
                    }
                }
            }
            stage("Import Template ovf to DC2") {
                when {
                    allOf {
                        expression { currentBuild.result == 'SUCCESS' }
                        expression { config?.import_ovf_to_dc2 && config.import_ovf_to_dc2.toBoolean() }
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
                    }
                }
                environment {
                    GOVC_URL = "${config.vcenter_host2}"
                }
                steps {
                    script {
                        log.info("Export spec for template ovf")
                        sh """
                            govc import.spec \
                            ${config.vm_template_dir}/${config.vm_template_name}/${config.vm_template_name}.ovf \
                            | jq '.MarkAsTemplate = true' \
                            > ${config.vm_template_dir}/${config.vm_template_name}/options.${config.vcenter_shortname1}.json
                        """

                        sh """
                            govc import.spec \
                            ${config.vm_template_dir}/${config.vm_template_name}/${config.vm_template_name}.ovf \
                            | jq '.NetworkMapping[].Network = \"${config.vm_network_mgt2}\"' \
                            | jq '.MarkAsTemplate = true' \
                            > ${config.vm_template_dir}/${config.vm_template_name}/options.${config.vcenter_shortname2}.json
                        """

                        withCredentials(config.secret_vars) {
                            // ref: https://github.com/vmware/govmomi/blob/main/govc/USAGE.md#vmclone
                            vmTemplateFolderExists = sh(script: "govc folder.info ${config.vm_template_deploy_folder2} | grep 'Path:'", returnStatus: true) == 0
                            log.info("vmTemplateFolderExists=>${vmTemplateFolderExists}")

                            if (!vmTemplateFolderExists) {
                                log.info("Ensure template deploy folder [${config.vm_template_deploy_folder2}] exists...")
                                sh "govc folder.create ${config.vm_template_deploy_folder2}"
                            }

                            vmTemplateExists = doesVmTemplateExist(this, log, config.vm_template_name, false)

                            log.info("vmTemplateExists=${vmTemplateExists}")

                            if (vmTemplateExists) {
                                log.info("destroying existing template ${config.vm_template_name}")
                                sh "govc vm.destroy ${config.vm_template_name}"
                            }

                            try {
                                log.info("Import template ovf to [${config.vm_template_deploy_folder2}]")
                                sh """
                                    govc import.ovf \
                                    -ds=${config.vm_template_datastore} \
                                    -folder=${config.vm_template_deploy_folder2} \
                                    -host=${config.vm_template_host2} \
                                    -name=${config.vm_template_name} \
                                    -options ${config.vm_template_dir}/${config.vm_template_name}/options.${config.vcenter_shortname2}.json \
                                    ${config.vm_template_dir}/${config.vm_template_name}/${config.vm_template_name}.ovf >/dev/null
                                 """
                            } catch (Exception ex) {
                                log.error("govc import.ovf error: " + ex.getMessage())
                                throw ex
                            }

                        }
                    }
                }
            }
            stage("Deploy Template") {
                when {
                    allOf {
                        expression { currentBuild.result == 'SUCCESS' }
                        expression { !vmTemplateExists || (config?.replaceExistingTemplate && config.replaceExistingTemplate.toBoolean()) }
                    }
                }
                steps {
                    script {

                        Map deployConfigPrimary = [:]
                        Map deployConfigSecondary = [:]

                        deployConfigPrimary.vm_template_name = config.template_name
                        deployConfigPrimary.vm_template_datastore = config.vm_template_datastore
                        deployConfigPrimary.vm_template_deploy_folder = config.vm_template_deploy_folder
                        deployConfigPrimary.vm_deploy_folder = config.vm_deploy_folder
                        deployConfigPrimary.vm_template_host = config.vm_template_host
                        deployConfigPrimary.vcenter_host = config.vcenter_host
                        deployConfigPrimary.vcenter_shortname = config.vcenter_shortname1
                        deployConfigPrimary.secret_vars = config.secret_vars

                        log.info("Move template in ${config.vcenter_shortname1} to ${config.vm_template_deploy_folder}")
                        moveTemplate(this, log, deployConfigPrimary)

                        if (config?.import_ovf_to_dc2 && config.import_ovf_to_dc2.toBoolean()) {
                            deployConfigSecondary.vm_template_name = config.template_name
                            deployConfigSecondary.vm_template_datastore = config.vm_template_datastore
                            deployConfigSecondary.vm_template_deploy_folder = config.vm_template_deploy_folder2
                            deployConfigSecondary.vm_deploy_folder = config.vm_deploy_folder
                            deployConfigSecondary.vm_template_host = config.vm_template_host2
                            deployConfigSecondary.vcenter_host = config.vcenter_host2
                            deployConfigSecondary.vcenter_shortname = config.vcenter_shortname2
                            deployConfigSecondary.secret_vars = config.secret_vars

                            log.info("Move template in ${config.vcenter_shortname2} to ${config.vm_template_deploy_folder2}")
                            moveTemplate(this, log, deployConfigSecondary)
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (config?.alwaysEmailList) {
                        log.info("config.alwaysEmailList=${config.alwaysEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.alwaysEmailList.split(","))
                    } else {
                        sendEmail(currentBuild, env)
                    }
                    log.info("Empty current workspace dir")
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }
            success {
                script {
                    if (config?.successEmailList) {
                        log.info("config.successEmailList=${config.successEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.successEmailList.split(","))
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            changed {
                script {
                    if (config?.changedEmailList) {
                        log.info("config.changedEmailList=${config.changedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.changedEmailList.split(","))
                    }
                }
            }
        }
    }
} // body

//@NonCPS
Map loadPipelineConfig(Map params) {

    Map config = [:]

    List jobParts = JOB_NAME.split("/")
    log.info("jobParts=${jobParts}")
//     config.jobBaseFolderLevel = (jobParts.size() - 4)
    config.jobBaseFolderLevel = 2
    config.build_dir="templates"

    config.get('logLevel', "INFO")
    config.get('timeout', "3")
    config.get('timeoutUnit', "HOURS")
    config.get('debugPipeline', false)

    config.get('alwaysEmailList', "lee.james.johnson@gmail.com")
    config.get('tmpDirMaxFileCount', 100)
    config.get('tmpDirMaxFileCount', 7)

    // ref: https://blog.mrhaki.com/2011/09/groovy-goodness-take-and-drop-items.html
    jobParts = jobParts.drop(config.jobBaseFolderLevel)
    log.info("jobParts[after drop]=${jobParts}")

    config.template_build_env = jobParts[0]
    config.build_platform = jobParts[1]

//     config.build_release = jobParts[2]
//     config.template_build_type = jobParts[3]
    config.build_release = jobParts[-2]
    config.template_build_type = jobParts[-1]

    // ref: https://blog.mrhaki.com/2011/09/groovy-goodness-take-and-drop-items.html
    jobParts = jobParts.drop(1)
    // ref: https://blog.mrhaki.com/2015/01/groovy-goodness-take-or-drop-last-items.html
    jobParts = jobParts.dropRight(2)

    log.info("jobParts[after drop2]=${jobParts}")
    config.build_platform_type = 'server'
    if (jobParts.size() > 1) {
        platformTypeParts = jobParts.drop(1)
        config.build_platform_type = platformTypeParts.join('-')
    }
    config.build_distribution = jobParts.join('/')

    config.builder_type = "vsphere-iso"
    config.gitBranch = env.GIT_BRANCH

    log.info("template_build_env=${config.template_build_env}")
    log.info("build_platform=${config.build_platform}")
    log.info("build_platform_type=${config.build_platform_type}")
    log.info("build_distribution=${config.build_distribution}")
    log.info("build_release=${config.build_release}")
    log.info("template_build_type=${config.template_build_type}")

    String vm_template_name = "vm-template"
//     vm_template_name += "-${config.build_distribution.toLowerCase()}${config.build_release}"
    vm_template_name += "-${config.build_platform.toLowerCase()}${config.build_release}"
    vm_template_name += "-${config.template_build_type}"
    vm_template_name += "-${config.template_build_env.toLowerCase()}"

    config.template_name = vm_template_name
    config.build_id_left_padded = env.BUILD_NUMBER.toString().padLeft(5, '0')
    config.template_build_name = "${config.template_name}-${config.build_id_left_padded}"

    config.build_platform_config_dir = config.build_platform
    log.info("build_platform_config_dir=${config.build_platform_config_dir}")

    config.build_distribution_config_dir = config.build_distribution
    log.info("build_distribution_config_dir=${config.build_distribution_config_dir}")

//     config.build_release_config_dir = jobParts[1..2].join("/") + "/server"
//     config.build_release_config_dir = jobParts[1..2].join("/")
//     config.build_release_config_dir = "${config.build_distribution}/${config.build_release}"
    if (config.build_platform_type) {
        config.build_release_config_dir = "${config.build_distribution}/${config.build_release}/${config.build_platform_type}"
    } else {
        config.build_release_config_dir = "${config.build_distribution}/${config.build_release}"
    }
    log.info("build_release_config_dir=${config.build_release_config_dir}")

    config.get('replaceExistingTemplate', false)
    config.get('vmware_iso_nfs_local_mounted', false)
    config.get('import_ovf_to_dc2', false)

    log.info("loading build config")

    String buildConfigFile = "./${config.build_dir}/${config.build_distribution_config_dir}/build-config.json"
    if (fileExists(buildConfigFile)) {
        Map buildConfig = readJSON file: buildConfigFile
        config = MapMerge.merge(config, buildConfig.variables)
    } else {
        String message = "${logPrefix} buildConfigFile [${buildConfigFile}] not found"
        log.error("${message}")
        throw new RuntimeException(message)
    }

    log.info("loading common and build vars")
    Map commonVars = readJSON file: "./${config.build_dir}/common-vars.json"
    log.info("commonVars=${JsonUtils.printToJsonString(commonVars)}")
    config = MapMerge.merge(config, commonVars.variables)

    Map envVars = readJSON file: "./${config.build_dir}/env-vars.${config.template_build_env}.json"
    log.info("envVars=${JsonUtils.printToJsonString(envVars)}")
    config = MapMerge.merge(config, envVars)

    Map distributionVars = readJSON file: "./${config.build_dir}/${config.build_distribution_config_dir}/distribution-vars.json"
    log.info("distributionVars=${JsonUtils.printToJsonString(distributionVars)}")
    config = MapMerge.merge(config, distributionVars)

    Map templateVars = readJSON file: "./${config.build_dir}/${config.build_release_config_dir}/template.json"
    config = MapMerge.merge(config, templateVars)
    log.info("templateConfig=${JsonUtils.printToJsonString(templateVars)}")

//     Map boxInfoVars = readJSON file: "./${config.build_dir}/${config.build_release_config_dir}/box_info.json"
    Map boxInfoVars = readJSON file: "./${config.build_dir}/${config.build_release_config_dir}/box_info.${config.template_build_type}.json"
    config = MapMerge.merge(config, boxInfoVars)
    log.info("boxInfoVars=${JsonUtils.printToJsonString(boxInfoVars)}")

//     if (config.build_distribution=="Windows") {
    if (config.build_distribution==~"^Windows.*") {
        config.iso_dir = "${config.build_distribution.toLowerCase()}/${config.build_release}"
    } else {
        config.iso_dir = "${config.build_distribution}/${config.build_release}"
    }
    // ref: https://stackoverflow.com/questions/605696/get-file-name-from-url
    config.iso_file = config.iso_url.substring(config.iso_url.lastIndexOf('/') + 1, config.iso_url.length()).replace(".jigdo", ".iso")
    if (config.iso_file.contains("?")) {
        config.iso_file = config.iso_file.split("\\?")[0]
    }
    config.get('os_image_dir', "/data/datacenter/jenkins/osimages")

    log.info("build_distribution_config_dir=${config.build_distribution_config_dir}")
    log.info("build_release_config_dir=${config.build_release_config_dir}")

    // copy immutable params maps to mutable config map
    // config = MapMerge.merge(config, params)
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key= Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    config.get('jenkinsNodeLabel',"packer")

    // ***********************************************
    // build specific vars overridden by pipeline
    config.vm_template_build_name=config.template_build_name
    config.vm_template_build_type=config.template_build_type
    config.vm_template_name=config.template_name
    config.vm_build_env=config.template_build_env
    config.iso_dir=config.iso_dir
    config.iso_file=config.iso_file

    log.setLevel(config.logLevel)

    if (config?.debugPipeline && config.debugPipeline.toBoolean()) {
        log.setLevel(LogLevel.DEBUG)
    }

    Map imageInfo = [:]
    imageInfo.name = "${env.JOB_BASE_NAME}"

    imageInfo.name = config.iso_dir
    imageInfo.iso_url = config.iso_url
    imageInfo.iso_file = config.iso_file
    imageInfo.iso_checksum = "${config.iso_checksum_type}:${config.iso_checksum}"
    config.image_info = imageInfo

    config.get('build_on_error', 'abort')
    config.build_config = "${config.build_distribution_config_dir}/"
    if (config.build_format == "json") {
        config.packer_build_only = "${config.builder_type}"
        config.packer_var_format = "json"
        config.packer_build_format = "json"
        config.build_config = "${config.build_distribution_config_dir}/build-config.${config.packer_build_format}"
    } else {
//         config.packer_build_only = "${config.builder_type}.${config.build_distribution}"
        config.packer_build_only = "${config.builder_type}.${config.build_platform}"
        config.packer_var_format = "json.pkrvars.hcl"
        config.packer_build_format = "json.pkr.hcl"
    }
    config.vmware_images_dir = "${config.vm_data_dir}/${config.iso_base_dir}/${config.iso_dir}"

    // ref: https://stackoverflow.com/questions/7935858/the-split-method-in-java-does-not-work-on-a-dot
    config.vcenter_shortname1 = config.vcenter_host.tokenize('.')[0]
    config.vcenter_shortname2 = config.vcenter_host2.tokenize('.')[0]

    config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token')

    // ref: https://docs.cloudbees.com/docs/cloudbees-ci/latest/cloud-secure-guide/injecting-secrets
    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
    List secretVars=[
        usernamePassword(credentialsId: 'infra-vcenter-cred', passwordVariable: 'VMWARE_VCENTER_PASSWORD', usernameVariable: 'VMWARE_VCENTER_USERNAME'),
        usernamePassword(credentialsId: 'infra-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME'),
        usernamePassword(credentialsId: 'infra-packer-cred', passwordVariable: 'PACKER_USER_PASSWORD', usernameVariable: 'PACKER_USER_USERNAME'),
        string(credentialsId: 'ansible-vault-password', variable: 'ANSIBLE_VAULT_PASSWORD'),
        string(credentialsId: 'bitbucket-ssh-jenkins-string', variable: 'ANSIBLE_BITBUCKET_SSH_KEY_STRING'),
        string(credentialsId: 'packer-ssh-public-key', variable: 'PACKER_USER_SSH_PUBLIC_KEY'),
//         string(credentialsId: config.ansibleGalaxyTokenCredId, variable: 'ANSIBLE_PAH_TOKEN'),
        sshUserPrivateKey(credentialsId: 'bitbucket-ssh-jenkins', keyFileVariable: 'ANSIBLE_BITBUCKET_SSH_KEY')
    ]
    config.secret_vars = secretVars

    log.debug("params=${JsonUtils.printToJsonString(params)}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}

boolean doesVmTemplateExist(def dsl, Logger log, String vmTemplateName, boolean onlyIfTypeTemplate=true) {
    boolean vmTemplateExists = false

    dsl.sh "govc vm.info ${vmTemplateName}"
//     boolean vmTemplateExists = dsl.sh(script: "govc vm.info -json ${vmTemplateName} | jq -er '.VirtualMachines[] | select(.Config.Template == true)'", returnStatus: true) == 0
    String vmInfoResultJson = dsl.sh(script: "govc vm.info -json ${vmTemplateName}", returnStdout: true)
    log.debug("vmInfoResultJson=${vmInfoResultJson}")

    def slurper = new JsonSlurper()
    Map vmInfoResult = slurper.parseText(vmInfoResultJson)

//     def vmInfoResult = dsl.readJSON(text: vmInfoResultJson)
    log.debug("vmInfoResult: ${JsonUtils.printToJsonString(vmInfoResult)}")
//     log.info("vmInfoResult: ${vmInfoResult}")
//     vmTemplateExists = (vmInfoResult.containsKey('VirtualMachines') && vmInfoResult.VirtualMachines.size() > 0)

    Map templateKeysLookup = [
        'lower': [
            'virtualMachines': 'virtualMachines',
            'config': 'config',
            'template': 'template'
        ],
        'upper': [
            'virtualMachines': 'VirtualMachines',
            'config': 'Config',
            'template': 'Template'
        ]
    ]
    Map templateKeys = templateKeysLookup['lower']

    if (vmInfoResult.containsKey('VirtualMachines')) {
        templateKeys = templateKeysLookup['upper']
    }

    List virtualMachines=vmInfoResult[templateKeys.virtualMachines]

    log.debug("virtualMachines=${virtualMachines}")
    boolean resultsFound = (virtualMachines != null)
    log.info("resultsFound=${resultsFound}")
    if (resultsFound && virtualMachines.size() == 1) {
        if (onlyIfTypeTemplate) {
            vmTemplateExists = (virtualMachines[0].containsKey(templateKeys.config)
                && virtualMachines[0][templateKeys.config].containsKey(templateKeys.template)
                && virtualMachines[0][templateKeys.config][templateKeys.template] == true)
        } else {
            vmTemplateExists = true
        }
    }

    log.info("vmTemplateExists=${vmTemplateExists}")
    return vmTemplateExists
}

List getPackerCommandArgList(String packerCommand, Map config) {

    List packerCmdArgList = []
    packerCmdArgList.push("packer")
    packerCmdArgList.push("${packerCommand}")
    packerCmdArgList.push("-only ${config.packer_build_only}")
    if (packerCommand == 'build') {
        packerCmdArgList.push("-on-error=${config.build_on_error}")
    }
    if (config.build_format == "json") {
        packerCmdArgList.push("-var-file=common-vars.${config.packer_var_format}")
    }
//     packerCmdArgList.push("-var-file=${config.build_distribution_config_dir}/env-vars.${config.template_build_env}.${config.packer_var_format}")
    packerCmdArgList.push("-var-file=env-vars.${config.template_build_env}.${config.packer_var_format}")
    packerCmdArgList.push("-var-file=${config.build_distribution_config_dir}/distribution-vars.${config.packer_var_format}")
    packerCmdArgList.push("-var-file=${config.build_release_config_dir}/template.${config.packer_var_format}")
//     packerCmdArgList.push("-var-file=${config.build_release_config_dir}/box_info.${config.packer_var_format}")
    packerCmdArgList.push("-var-file=${config.build_release_config_dir}/box_info.${config.template_build_type}.${config.packer_var_format}")
    packerCmdArgList.push("-var vm_template_build_name=${config.template_build_name}")
    packerCmdArgList.push("-var vm_template_build_type=${config.template_build_type}")
    packerCmdArgList.push("-var vm_template_name=${config.template_name}")
    packerCmdArgList.push("-var vm_build_env=${config.template_build_env}")
    packerCmdArgList.push("-var iso_dir=${config.iso_dir}")
    packerCmdArgList.push("-var iso_file=${config.iso_file}")
    packerCmdArgList.push("${config.build_config}")

    log.debug("packerCmdArgList=${JsonUtils.printToJsonString(packerCmdArgList)}")
    return packerCmdArgList
}

void moveTemplate(def dsl, Logger log, Map deployConfig) {
    String logPrefix="[${deployConfig.vcenter_shortname}]:"

    String vm_template_name = deployConfig.vm_template_name
    String vm_template_datastore = deployConfig.vm_template_datastore
    String vm_template_deploy_folder = deployConfig.vm_template_deploy_folder
    String vm_deploy_folder = deployConfig.vm_deploy_folder
    String vm_template_host = deployConfig.vm_template_host
    String vcenter_host = deployConfig.vcenter_host

    log.info("${logPrefix} vm_template_name=${vm_template_name}")
    log.info("${logPrefix} vm_template_datastore=${vm_template_datastore}")
    log.info("${logPrefix} vm_template_deploy_folder=${vm_template_deploy_folder}")
    log.info("${logPrefix} vm_deploy_folder=${vm_deploy_folder}")
    log.info("${logPrefix} vm_template_host=${vm_template_host}")
    log.info("${logPrefix} vcenter_host=${vcenter_host}")

    dsl.withEnv(["GOVC_URL=${vcenter_host}"]) {
        dsl.withCredentials(deployConfig.secret_vars) {

//             sh "govc folder.info ${vm_template_deploy_folder}"
            vmTemplateFolderExists = sh(script: "govc folder.info ${vm_template_deploy_folder} | grep 'Path:'", returnStatus: true) == 0
            log.info("vmTemplateFolderExists=>${vmTemplateFolderExists}")

            if (!vmTemplateFolderExists) {
                log.info("Create template folder ${vm_template_deploy_folder}")
                dsl.sh "govc folder.create ${vm_template_deploy_folder}"
            }

            log.info("Ensure datastore template directory ${vm_deploy_folder} already exists...")
            dsl.sh "govc datastore.mkdir -p -ds=${vm_template_datastore} ${vm_deploy_folder}"

            String targetPath = "[${vm_template_datastore}] ${vm_deploy_folder}"
            log.info("${logPrefix} targetPath='${targetPath}'")

            String getVmPathCmd = "govc vm.info -json ${vm_template_name} | jq '.. |.Config?.VmPathName? | select(. != null)'"
            dsl.sh "${getVmPathCmd}"
            String vmPath = dsl.sh(script: "${getVmPathCmd}", returnStdout: true).replaceAll('"',"")
            log.info("${logPrefix} vmPath='${vmPath}'")
//     //        String vmDatastore = vmPath.split("/")[-1].replaceAll('([|])+','')
//     //        String vmDatastore = vmPath.split(" ")[-1].replaceAll('([|])+','')
//             String vmDatastore = vmPath.split(" ")[0].replaceAll('([|])+','')
//             vmPath = vmPath.substring(0, vmPath.lastIndexOf("/"))
//             String vmFolder = vmPath.split(" ")[1].split("/${vm_template_name}/")[0]
            String vmFolderPath = vmPath.split("/${vm_template_name}/")[0]
            log.info("${logPrefix} vmFolderPath='${vmFolderPath}'")

            if (vmFolderPath != targetPath) {
                log.info("${logPrefix} moving VM from '${vmFolderPath}' to '${targetPath}'")
                // ref: https://github.com/vmware/govmomi/blob/main/govc/USAGE.md
                dsl.sh "govc vm.unregister ${vm_template_name}"
                dsl.sh "govc datastore.rm -f -ds=${vm_template_datastore} ${vm_deploy_folder}/${vm_template_name}"
                dsl.sh "govc datastore.mv -ds=${vm_template_datastore} ${vm_template_name} ${vm_deploy_folder}/${vm_template_name}"
                dsl.sh """
                    govc vm.register -template=true \
                    -ds=${vm_template_datastore} \
                    -folder=${vm_template_deploy_folder} \
                    -host=${vm_template_host} \
                    ${vm_deploy_folder}/${vm_template_name}/${vm_template_name}.vmtx
                """
            }
            log.info("${logPrefix} templates/VMs in [${vm_template_datastore}] ${vm_template_deploy_folder}:")
            dsl.sh "govc datastore.ls -ds=${vm_template_datastore} ${vm_deploy_folder}"
        }
    }
}

