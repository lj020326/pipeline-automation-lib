#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.Utilities

// ref: https://github.com/jenkinsci/packer-plugin/issues/20#issuecomment-469681596

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.JsonUtils
import groovy.json.*
//import groovy.json.JsonOutput

def call() {

//     Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this, LogLevel.INFO)

//     String packerTool = "packer-1.6.2" // Name of Packer Installation
// //     String packerTool = "packer-1.8.6" // Name of Packer Installation

    Map config=[:]
    boolean vmTemplateExists = false

//    Map config=loadPipelineConfig(log, params)
//    String agentLabel = getJenkinsAgentLabel(config.jenkinsNodeLabel)

    List paramList = []

    Map paramMap = [
        replaceExistingTemplate  : booleanParam(defaultValue: false, description: "Replace Existing Template?", name: 'ReplaceExistingTemplate')
    ]

    paramMap.each { String key, def param ->
        paramList.addAll([param])
    }

    properties([
        parameters(paramList),
        disableConcurrentBuilds()
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
//            label agentLabel as String
            label "packer"
        }
//         tools {
//             // ref: https://webapp.chatgpt4google.com/s/MzY5MjYw
//             customTool 'packer-local'
//         }
//         environment {
//             PACKER_HOME = tool name: 'packwer-local', type: 'hudson.plugins.customtools.CustomTool'
//         }

        environment {
            GOVC_INSECURE = true
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            disableConcurrentBuilds()
            skipDefaultCheckout(false)
            timestamps()
            timeout(time: 3, unit: 'HOURS')
        }

        stages {

            stage("Initialize") {
                steps {
                    script {
//                         sh "env -0 | sort -z | tr \'\0\' \'\n\'"
//                         sh "export -p | sed 's/declare -x //' | sed 's/export //'"
                        config=loadPipelineConfig(log, params)
                        log.info("config=${JsonUtils.printToJsonString(config)}")
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
                            log.info("Checking if template folder at ${config.vm_build_folder} exists...")
//                             sh "govc folder.info ${config.vm_build_folder}"
                            vmTemplateFolderExists = sh(script: "govc folder.info ${config.vm_build_folder} | grep 'Path:'", returnStatus: true) == 0
                            log.info("vmTemplateFolderExists=>${vmTemplateFolderExists}")

                            if (!vmTemplateFolderExists) {
                                log.info("Create template folder ${config.vm_template_build_folder}")
                                sh "govc folder.create ${config.vm_template_build_folder}"
                            }

                            log.info("Ensure datastore template directory ${config.vm_build_folder} already exists...")
                            sh "govc datastore.mkdir -p -ds=${config.vm_template_datastore} ${config.vm_build_folder}"

                            log.info("Checking if template already exists...")
                            sh "govc vm.info ${config.vm_template_build_name}"
                            vmTemplateExists = sh(script: "govc vm.info ${config.vm_template_build_name} | grep 'UUID:'", returnStatus: true) == 0
                            log.info("initial check if template already exists=>${vmTemplateExists}")

//                             if (vmTemplateExists) {
//                                 if (config.replaceExistingTemplate?.toBoolean()) {
//                                     log.info("destroying existing template ${config.vm_template_build_name}")
//                                     sh "govc vm.destroy ${config.vm_template_build_name}"
//                                 } else {
//                                     log.info("vmTemplateExists=${vmTemplateExists} - skipping build")
//                                 }
//                             }
                        }
                    }
                }
            }

            stage('Fetch OS image') {
                when {
                    expression { !vmTemplateExists || config.replaceExistingTemplate?.toBoolean() }
                }
                environment {
                    GOVC_URL = "${config.vcenter_host}"
                }

                steps {
                    script {
//                         String vmware_images_dir = "${config.vm_data_dir}/${config.iso_base_dir}/${config.iso_dir}"
//                         log.info("vmware_images_dir=>${vmware_images_dir}")

                        boolean imageExists = sh(script: "ls -Fla ${config.os_image_dir}/ | grep ${config.iso_file} ",
                            returnStatus: true)==0

                        if (!imageExists) {
                            sh """
                            ansible-playbook \
                              --inventory-file localhost, \
                              -e fetch_os_images__vmware_images_dir='"${config.vmware_images_dir}"' \
                              -e fetch_os_images__osimage_dir='"${config.os_image_dir}"' \
                              -e fetch_images='"[${JsonOutput.toJson(config.image_info)}]"' \
                              ${config.ansible_fetch_images_playbook}
                            """
                        }

                        log.info("config.vmware_iso_nfs_local_mounted=${config.vmware_iso_nfs_local_mounted}")

                        if (!(config.vmware_iso_nfs_local_mounted?.toBoolean())) {
                            withCredentials(config.secret_vars) {
                                String datastoreLsCmd = """
                                    govc datastore.ls \
                                     -ds=${config.vm_iso_datastore} \
                                     ${config.iso_base_dir}/${config.iso_dir}/${config.iso_file} \
                                     | grep "${config.iso_file}"
                                """
                                vmIsoExists = sh(script: "${datastoreLsCmd}", returnStatus: true) == 0
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
            }

            stage("Run Packer to validate json settings") {
                when {
                    allOf {
                        expression { !vmTemplateExists || config.replaceExistingTemplate?.toBoolean() }
                        expression { !config.skip_packer_build?.toBoolean() }
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

                            withCredentials(config.secret_vars) {

                                // ref: https://vsupalov.com/packer-ami/
                                // ref: https://blog.deimos.fr/2015/01/16/packer-build-multiple-images-easily/
                                // ref: https://github.com/hashicorp/packer/pull/7184
//                                 sh """
//                                 ${tool packer-local}/packer validate \

                                List buildArgs = []
                                buildArgs.push("-only ${config.packer_build_only}")
                                if (config.build_format == "json") {
                                    buildArgs.push("-var-file=common-vars.${config.packer_var_format}")
                                }
                                buildArgs.push("-var-file=${config.build_distribution_config_dir}/distribution-vars.${config.packer_var_format}")
                                buildArgs.push("-var-file=${config.build_release_config_dir}/template.${config.packer_var_format}")
//                                 buildArgs.push("-var-file=${config.build_release_config_dir}/box_info.${config.packer_var_format}")
                                buildArgs.push("-var-file=${config.build_release_config_dir}/box_info.${config.packer_var_format}")
                                buildArgs.push("-var vm_template_build_name=${config.vm_build_id}")
                                buildArgs.push("-var iso_dir=${config.iso_dir}")
                                buildArgs.push("-var iso_file=${config.iso_file}")
                                buildArgs.push("${config.build_config}")
                                String buildArgsString = buildArgs.join(" ")
                                log.debug("buildArgsString=${buildArgsString}")

                                // ref: https://stackoverflow.com/questions/45348761/jenkins-pipeline-how-do-i-use-the-tool-option-to-specify-a-custom-tool?noredirect=1&lq=1
                                withEnv(["PATH+PACKER=${tool 'packer-local'}/bin"]) {
                                    sh "packer validate ${buildArgsString}"
                                }
                            }

                        }
                    }
                }
            }

            stage("Run Packer to build template") {
                when {
                    allOf {
                        expression { !vmTemplateExists || config.replaceExistingTemplate?.toBoolean() }
                        expression { !config.skip_packer_build?.toBoolean() }
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

                            withCredentials(config.secret_vars) {

                                // ref: https://vsupalov.com/packer-ami/
                                // ref: https://blog.deimos.fr/2015/01/16/packer-build-multiple-images-easily/
                                // ref: https://github.com/hashicorp/packer/pull/7184
                                List buildArgs = []
                                buildArgs.push("-only ${config.packer_build_only}")
                                buildArgs.push("-on-error=${config.packer_build_on_error}")
                                if (config.build_format == "json") {
                                    buildArgs.push("-var-file=common-vars.${config.packer_var_format}")
                                }
                                buildArgs.push("-var-file=${config.build_distribution_config_dir}/distribution-vars.${config.packer_var_format}")
                                buildArgs.push("-var-file=${config.build_release_config_dir}/template.${config.packer_var_format}")
//                                 buildArgs.push("-var-file=${config.build_release_config_dir}/box_info.${config.packer_var_format}")
                                buildArgs.push("-var-file=${config.build_release_config_dir}/box_info.${config.packer_var_format}")
                                buildArgs.push("-var vm_template_build_name=${config.vm_build_id}")
                                buildArgs.push("-var iso_dir=${config.iso_dir}")
                                buildArgs.push("-var iso_file=${config.iso_file}")
                                buildArgs.push("-debug")
                                buildArgs.push("${config.build_config}")
                                String buildArgsString = buildArgs.join(" ")
                                log.debug("buildArgsString=${buildArgsString}")

                                // ref: https://stackoverflow.com/questions/45348761/jenkins-pipeline-how-do-i-use-the-tool-option-to-specify-a-custom-tool?noredirect=1&lq=1
                                withEnv(["PATH+PACKER=${tool 'packer-local'}/bin"]) {
//                                     sh "env"

                                    try {
                                        sh "packer build ${buildArgsString}"

                                        log.info("packer build complete - check build template exists for ${config.vm_build_id}")

                                        vmBuildTemplateExists = sh(script: "govc vm.info ${config.vm_build_id} | grep 'UUID:'", returnStatus: true) == 0
                                        log.info("vmBuildTemplateExists=${vmBuildTemplateExists}")

                                        if (vmBuildTemplateExists) {
                                            currentBuild.result = 'SUCCESS'
                                        } else {
                                            currentBuild.result = 'FAILURE'
                                        }
                                    } catch (hudson.AbortException ae) {
                                        currentBuild.result = 'FAILURE'
                                        log.error("packer build abort occurred: " + ae.getMessage())
                                        throw ae
                                    } catch (Exception ex) {
                                        log.error("packer build error: " + ex.getMessage())
                                        throw ex
                                    }

                                }

                                sh "govc vm.info ${config.vm_template_build_name}"
                                vmTemplateExists = sh(script: "govc vm.info ${config.vm_template_build_name} | grep 'UUID:'", returnStatus: true) == 0
                                log.info("vmTemplateExists=${vmTemplateExists}")

                                if (vmTemplateExists) {
                                    if (config.replaceExistingTemplate?.toBoolean()) {
                                        log.info("destroying existing template ${config.vm_template_build_name}")
                                        sh "govc vm.destroy ${config.vm_template_build_name}"
                                    } else {
                                        log.error("FOLLOWING CONDITION SHOULD NOT EXIST!")
                                        log.error("(vmTemplateExists == true) AND (replaceExistingTemplate == false)!")
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
                                        -vm=${config.vm_build_id} \
                                        -host=${config.vm_template_host} \
                                        -folder=${config.vm_deploy_folder} \
                                        -on=false -template=true \
                                        ${config.vm_template_build_name} >/dev/null
                                     """
                                } catch (Exception ex) {
                                    log.error("govc vm.clone error: " + ex.getMessage())
                                    throw ex
                                }

                                log.info("Remove temporary template build ${config.vm_build_id}")
                                sh "govc vm.destroy ${config.vm_build_id}"

                            }

                        }
                    }
                }
            }

            stage("Export Template to ovf") {
                when {
                    allOf {
                        expression { currentBuild.result == 'SUCCESS' }
                        expression { !vmTemplateExists || config.replaceExistingTemplate?.toBoolean() }
                    }
                }
                environment {
                    GOVC_URL = "${config.vcenter_host}"
                }
                steps {
                    script {
                        log.info("Export template to ovf [${config.vmware_images_dir}]")
                        withCredentials(config.secret_vars) {
                            try {
                                sh """
                                    govc export.ovf -f=true -i=false \
                                        -dc=${config.vcenter_datacenter} \
                                        -vm ${config.vm_template_build_name} \
                                        ${config.vmware_images_dir} >/dev/null
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
            stage("Deploy Template") {
                when {
                    allOf {
                        expression { currentBuild.result == 'SUCCESS' }
                        expression { !vmTemplateExists || config.replaceExistingTemplate?.toBoolean() }
                    }
                }
                steps {
                    script {

                        Map deployConfigPrimary = [:]

                        deployConfigPrimary.vm_template_build_name = config.vm_template_build_name
                        deployConfigPrimary.vm_template_datastore = config.vm_template_datastore
                        deployConfigPrimary.vm_template_deploy_folder = config.vm_template_deploy_folder
                        deployConfigPrimary.vm_deploy_folder = config.vm_deploy_folder
                        deployConfigPrimary.vm_template_host = config.vm_template_host
                        deployConfigPrimary.vcenter_host = config.vcenter_host
                        deployConfigPrimary.vcenter_shortname = config.vcenter_shortname1
                        deployConfigPrimary.secret_vars = config.secret_vars

                        log.info("Move template in ${config.vcenter_shortname1} to ${config.vm_template_deploy_folder}")
                        moveTemplate(this, log, deployConfigPrimary)

                    }
                }
            }
        }
        post {
            always {
                script {
                    List emailAdditionalDistList = []
                    if (config.alwaysEmailDistList) {
                        emailAdditionalDistList = config.alwaysEmailDistList
                    }
                    if (config.gitBranch in ['origin/main','main']) {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result})")
                        sendEmail(currentBuild, env, emailAdditionalDistList=alwaysEmailDistList)
                    } else {
                        log.info("post(${env.BRANCH_NAME}): sendEmail(${currentBuild.result}, 'RequesterRecipientProvider')")
                        sendEmail(currentBuild, env, [[$class: 'RequesterRecipientProvider']])
                    }
                    log.info("Empty current workspace dir")
                    cleanWs()
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
    config.jobBaseFolderLevel = (jobParts.size() - 3)
    config.build_dir="templates"
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')
    config.alwaysEmailList = config.get('alwaysEmailList', "Lee.Johnson.Contractor@alsac.stjude.org")

    jobParts = jobParts.drop(config.jobBaseFolderLevel)
    log.info("${logPrefix} jobParts[after drop]=${jobParts}")

    config.build_distribution = jobParts[0]
    config.build_release = jobParts[1]
    config.builder_type = "vsphere-iso"
    config.gitBranch = env.GIT_BRANCH

    log.info("${logPrefix} build_distribution=${config.build_distribution}")
    log.info("${logPrefix} build_release=${config.build_release}")

    config.build_distribution_config_dir = config.build_distribution
    config.build_release_config_dir = jobParts[0..1].join("/") + "/server"
    log.info("${logPrefix} build_release_config_dir=${config.build_release_config_dir}")

    config.vmware_iso_nfs_local_mounted = config.get('vmware_iso_nfs_local_mounted', false)
    config.replaceExistingTemplate = config.get('replaceExistingTemplate', false)

    log.info("${logPrefix} loading build config")

    String buildConfigFile = "./${config.build_dir}/${config.build_distribution_config_dir}/build-config.json"
    if (fileExists(buildConfigFile)) {
        Map buildConfig = readJSON file: buildConfigFile
        config = MapMerge.merge(config, buildConfig.variables)
    } else {
        String message = "${logPrefix} buildConfigFile [${buildConfigFile}] not found"
        log.error("${message}")
        throw message
    }

    log.info("${logPrefix} loading common and build vars")
    Map commonVars = readJSON file: "./${config.build_dir}/common-vars.json"
//     config = MapMerge.merge(config, commonVars)
    config = MapMerge.merge(config, commonVars.variables)
    log.info("${logPrefix} commonVars=${JsonUtils.printToJsonString(commonVars)}")

    Map distributionVars = readJSON file: "./${config.build_dir}/${config.build_distribution_config_dir}/distribution-vars.json"
    config = MapMerge.merge(config, distributionVars)
    log.info("distributionVars=${JsonUtils.printToJsonString(distributionVars)}")

    Map templateVars = readJSON file: "./${config.build_dir}/${config.build_release_config_dir}/template.json"
    config = MapMerge.merge(config, templateVars)
    log.info("templateConfig=${JsonUtils.printToJsonString(templateVars)}")

//     Map boxInfoVars = readJSON file: "./${config.build_dir}/${config.build_release_config_dir}/box_info.json"
    Map boxInfoVars = readJSON file: "./${config.build_dir}/${config.build_release_config_dir}/box_info.json"
    config = MapMerge.merge(config, boxInfoVars)
    log.info("boxInfoVars=${JsonUtils.printToJsonString(boxInfoVars)}")

    if (config.build_distribution=="Windows") {
        config.iso_dir = "${config.build_distribution.toLowerCase()}/${config.build_release}"
    } else {
        config.iso_dir = "${config.build_distribution}/${config.build_release}"
    }
    // ref: https://stackoverflow.com/questions/605696/get-file-name-from-url
    config.iso_file = config.iso_url.substring(config.iso_url.lastIndexOf('/') + 1, config.iso_url.length()).replace(".jigdo", ".iso")
    if (config.iso_file.contains("?")) {
        config.iso_file = config.iso_file.split("\\?")[0]
    }
    config.os_image_dir = config.get('os_image_dir', "/data/datacenter/jenkins/osimages")

    log.info("${logPrefix} build_distribution_config_dir=${config.build_distribution_config_dir}")
    log.info("${logPrefix} build_release_config_dir=${config.build_release_config_dir}")

    log.info("${logPrefix} BUILD_TAG=${BUILD_TAG}")
    List buildTagList = env.BUILD_TAG.split("-")
    buildTagList[-1] = env.BUILD_NUMBER.toString().padLeft(4, '0')

    // ref: https://mrhaki.blogspot.com/2011/09/groovy-goodness-take-and-drop-items.html
    buildTagList = buildTagList.drop(config.jobBaseFolderLevel)
    templateBuildTag = buildTagList.join("-").toLowerCase()

    log.info("${logPrefix} templateBuildTag=${templateBuildTag}")
    config.vm_build_id = templateBuildTag
//    env.TEMPLATE_BUILD_ID = templateBuildTag

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

    if (config.debugPipeline?.toBoolean()) {
        log.setLevel(LogLevel.DEBUG)
    }

    Map imageInfo = [:]
    imageInfo.name = "${env.JOB_BASE_NAME}"

    imageInfo.name = config.iso_dir
    imageInfo.iso_url = config.iso_url
    imageInfo.iso_file = config.iso_file
    imageInfo.iso_checksum = "${config.iso_checksum_type}:${config.iso_checksum}"
    config.image_info = imageInfo

    config.packer_build_on_error = config.get('packer_build_on_error', 'abort')
    config.build_config = "${config.build_distribution_config_dir}/"
    if (config.build_format == "json") {
        config.packer_build_only = "${config.builder_type}"
        config.packer_var_format = "json"
        config.packer_build_format = "json"
        config.build_config = "${config.build_distribution_config_dir}/build-config.${config.packer_build_format}"
    } else {
        config.packer_build_only = "${config.builder_type}.${config.build_distribution}"
        config.packer_var_format = "json.pkrvars.hcl"
        config.packer_build_format = "json.pkr.hcl"
    }
    config.vmware_images_dir = "${config.vm_data_dir}/${config.iso_base_dir}/${config.iso_dir}"

    // ref: https://stackoverflow.com/questions/7935858/the-split-method-in-java-does-not-work-on-a-dot
    config.vcenter_shortname1 = config.vcenter_host.tokenize('.')[0]
    config.vcenter_shortname2 = config.vcenter_host2.tokenize('.')[0]

    config.ansibleGalaxyTokenCredId = config.get('ansibleGalaxyTokenCredId', 'ansible-galaxy-pah-token')

    // ref: https://docs.cloudbees.com/docs/cloudbees-ci/latest/cloud-secure-guide/injecting-secrets
    // require SSH credentials for some ansible jobs (e.g., deploy-cacerts)
    // ref: https://emilwypych.com/2019/06/15/how-to-pass-credentials-to-jenkins-pipeline/
    List secretVars=[
        usernamePassword(credentialsId: 'infra-vcenter-cred', passwordVariable: 'VMWARE_VCENTER_PASSWORD', usernameVariable: 'VMWARE_VCENTER_USERNAME'),
        usernamePassword(credentialsId: 'infra-govc-cred', passwordVariable: 'GOVC_PASSWORD', usernameVariable: 'GOVC_USERNAME'),
        string(credentialsId: 'packer-ssh-password', variable: 'PACKER_SSH_PASSWORD'),
        string(credentialsId: 'ansible-vault-password', variable: 'ANSIBLE_VAULT_PASSWORD'),
        string(credentialsId: 'bitbucket-ssh-jenkins-string', variable: 'ANSIBLE_BITBUCKET_SSH_KEY_STRING'),
        sshUserPrivateKey(credentialsId: 'bitbucket-ssh-jenkins', keyFileVariable: 'ANSIBLE_BITBUCKET_SSH_KEY')
    ]
    config.secret_vars = secretVars

    log.debug("${logPrefix} params=${JsonUtils.printToJsonString(params)}")
    log.debug("${logPrefix} config=${JsonUtils.printToJsonString(config)}")

    return config
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}

void moveTemplate(def dsl, Logger log, Map deployConfig) {
    String logPrefix="moveTemplate(${deployConfig.vcenter_shortname}):"

    String vm_template_build_name = deployConfig.vm_template_build_name
    String vm_template_datastore = deployConfig.vm_template_datastore
    String vm_template_deploy_folder = deployConfig.vm_template_deploy_folder
    String vm_deploy_folder = deployConfig.vm_deploy_folder
    String vm_template_host = deployConfig.vm_template_host
    String vcenter_host = deployConfig.vcenter_host

    log.info("${logPrefix} vm_template_build_name=${vm_template_build_name}")
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
                sh "govc folder.create ${vm_template_deploy_folder}"
            }

            log.info("Ensure datastore template directory ${vm_deploy_folder} already exists...")
            sh "govc datastore.mkdir -p -ds=${vm_template_datastore} ${vm_deploy_folder}"

            String targetPath = "[${vm_template_datastore}] ${vm_deploy_folder}"
            log.info("${logPrefix} targetPath='${targetPath}'")

            String getVmPathCmd = "govc vm.info -json ${vm_template_build_name} | jq '.. |.Config?.VmPathName? | select(. != null)'"
            dsl.sh "${getVmPathCmd}"
            String vmPath = dsl.sh(script: "${getVmPathCmd}", returnStdout: true).replaceAll('"',"")
            log.info("${logPrefix} vmPath='${vmPath}'")
//     //        String vmDatastore = vmPath.split("/")[-1].replaceAll('([|])+','')
//     //        String vmDatastore = vmPath.split(" ")[-1].replaceAll('([|])+','')
//             String vmDatastore = vmPath.split(" ")[0].replaceAll('([|])+','')
//             vmPath = vmPath.substring(0, vmPath.lastIndexOf("/"))
//             String vmFolder = vmPath.split(" ")[1].split("/${vm_template_build_name}/")[0]
            String vmFolderPath = vmPath.split("/${vm_template_build_name}/")[0]
            log.info("${logPrefix} vmFolderPath='${vmFolderPath}'")

            if (vmFolderPath != targetPath) {
                log.info("${logPrefix} moving VM from '${vmFolderPath}' to '${targetPath}'")
                // ref: https://github.com/vmware/govmomi/blob/main/govc/USAGE.md
                dsl.sh "govc vm.unregister ${vm_template_build_name}"
                dsl.sh "govc datastore.rm -f -ds=${vm_template_datastore} ${vm_deploy_folder}/${vm_template_build_name}"
                dsl.sh "govc datastore.mv -ds=${vm_template_datastore} ${vm_template_build_name} ${vm_deploy_folder}/${vm_template_build_name}"
                dsl.sh """
                    govc vm.register -template=true \
                    -ds=${vm_template_datastore} \
                    -folder=${vm_deploy_folder} \
                    -host=${vm_template_host} \
                    ${vm_deploy_folder}/${vm_template_build_name}/${vm_template_build_name}.vmtx
                """
            }
            log.info("${logPrefix} templates/VMs in [${vm_template_datastore}] ${vm_template_deploy_folder}:")
            dsl.sh "govc datastore.ls -ds=${vm_template_datastore} ${vm_deploy_folder}"
        }
    }
}

