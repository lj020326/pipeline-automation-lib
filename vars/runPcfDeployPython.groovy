#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import groovy.json.*

def call(Map params=[:]) {

//     Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this, LogLevel.INFO)

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(log, params)

    pipeline {

        agent {
            label "M3 || QA-M3"
        }

        tools {
            maven 'M3'
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            skipDefaultCheckout()
            timestamps()
            timeout(time: 1, unit: 'HOURS')
        }
        stages {
            stage('Checkout and Initialize') {
                steps {
                    script {
                        deleteDir()
                        checkout scm

                        if (fileExists(config?.configFile)) {
                            String configFile = config.configFile
                            config = loadPipelineConfig(log, params, configFile)
                            log.info("****Loaded Config File ${configFile}")
                            log.debug("config=${printToJsonString(config)}")
                        } else {
                            log.debug("****Config file ${configFile} not present, skipping config file load and using global defaults instead...")
                        }

                        gitPullSharedAnsibleFiles('2.0.0-RELEASE')

                    }
                }
            }
            stage('Deploy App Environments') {
                when {
                    expression { config?.pcfSpringAppList && config.pcfSpringAppList.size()>0 }
                }
                steps {
                    script {
                        config.pcfDeployEnvironmentList.each {
                            String pcfAppEnv = it.pcfAppEnv
                            log.debug("pcfAppEnv=${pcfAppEnv}")
                            log.debug("it=${it}")

                            Map appEnvConfig = config.pcfEnvironments[pcfAppEnv] + config.findAll { !["pcfDeployEnvironmentList","pcfEnvironments"].contains(it.key) } + it
                            log.debug("appEnvConfig=${printToJsonString(appEnvConfig)}")

                            deployPcfAppEnv(log, appEnvConfig, vaultUtil, caasUtil)
                        }
                    }
                }
            }

        }
        post {
            changed {
                script {
                    log.debug("**** post[changed] currentBuild.result=${currentBuild.result}")
                    if(config.notifications?.changed?.email?.addressList) {

                        String addressList = config.notifications.changed.email.addressList.join(",")

                        notifyBuild(config, addressList, currentBuild, "CHANGED")
                    }
                }
            }
            always {
                script {
                    log.debug("**** post[always] currentBuild.result=${currentBuild.result}")

                    def duration = "${currentBuild.durationString.replace(' and counting', '')}"
                    currentBuild.description = "Deploy Duration: ${duration}"

                    postReleaseUpdatePom(log, config)

                    if(config.notifications?.always?.email?.addressList) {
                        String addressList = config.notifications.always.email.addressList.join(",")
                        echo "addressList is ${addressList}"
                        notifyBuild(config, addressList, currentBuild)
                    }

                }
            }
        }

    }

} // body


String printToJsonString(Map config) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(config))
}

// ref: https://stackoverflow.com/questions/4052840/most-efficient-way-to-make-the-first-character-of-a-string-lower-case
String decapitalize(String string) {
    if (string == null || string.length() == 0) {
        return string;
    }
    return string.substring(0, 1).toLowerCase() + string.substring(1);
}


Map loadPipelineConfig(Logger log, Map params, String configFile=null) {
    String logPrefix="loadPipelineConfig(configFile=${configFile}):"
    log.debug("${logPrefix} started")

    Map config = [:]

    def pcfDefaultsTxt = libraryResource 'pcfPipelineDefaults.yml'
    Map defaultSettings = readYaml text: "${pcfDefaultsTxt}"
    config=defaultSettings.pipeline

    if (configFile != null && fileExists(configFile)) {
        log.info("${logPrefix} pipeline config file ${configFile} present, loading ...")
        Map configSettings = readYaml file: "${configFile}"
        config=config + configSettings.pipeline
    }
    else {
        log.info("${logPrefix} pipeline config file ${configFile} not present, using defaults...")
    }
    log.debug("${logPrefix} config (pre-apply-default-settings)=${printToJsonString(config)}")

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("${logPrefix} params[${key}]=${value}")
        key=decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

//    config.logLevel = config.get('logLevel', "INFO")
    config.logLevel = config.get('logLevel', "DEBUG")
    config.debugPipeline = config.get('debugPipeline', true)
    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.setLevel(config.logLevel)

    config.gitRepoBranch = config.gitRepoBranch ?: env.BRANCH_NAME ?: "develop"

    log.debug("${logPrefix} env.BRANCH_NAME = ${env.BRANCH_NAME}")
    log.debug("${logPrefix} config.gitRepoBranch = ${config.gitRepoBranch}")

//    config.useConfigFile = config.get('useConfigFile', false)
    config.useConfigFile = config.get('useConfigFile', true)
    config.configFile = config.configFile ?: "jenkins/runPcfDeploy.yml"
    config.checkoutDir = config.get('checkoutDir', '.')

    config.deleteBeforePush = config.get('deleteBeforePush', false)
    log.info("${logPrefix} params=${params}")

//    config.useSimulationMode = config.get('useSimulationMode', true)
    config.useSimulationMode = config.get('useSimulationMode', false)
//    config.pomFile = config.get('pomFile','pom.xml')
    config.pomFile = config.get('pomFile',null)

    // secret vars
    config.jenkinsArtifactoryCredId = config.get('jenkinsArtifactoryCredId',"dcapi_ci_vcs_user")
    config.secretVars = getSecretEnvVars(config)

    //
    // essential/minimal params
    //
//    log.debug("${logPrefix} env.JOB_NAME = ${env.JOB_NAME}")
    config.jenkinsJobName = config.get('jenkinsJobName', env.JOB_NAME.replaceAll('%2F', '/').replaceAll('/', '-').replaceAll(' ', '-').toUpperCase())

    log.debug("${logPrefix} config.jenkinsJobName = ${config.jenkinsJobName}")
    config.buildNumber = currentBuild.number
    config.emailFrom=config.get('emailFrom',"DCAPI.pcfDeployAutomation@dettonville.org")

    config.runTests = config.get('runTests', true)
    config.runJMeter = config.get('runJMeter', true)
    config.runSonar = config.get('runSonar', false)
    config.runFindbugs = config.get('runFindbugs', false)
    config.runBuild = config.get('runBuild', true)

    // if useLocal is true - then sets/overrides the gitRepoBranch with local branch
    config.useLocal = config.get('useLocal', false)

    if(config.useLocal && config.gitRepoBranch!=env.BRANCH_NAME) {
        log.warn("${logPrefix} useLocal is true: the gitRepoBranch set as ${config.gitRepoBranch} will be set to local branch ${env.BRANCH_NAME}")
        config.gitRepoBranch=env.BRANCH_NAME
    }

    config.publishToArtifactory = config.get('publishToArtifactory', true)
    config.publishToArtifactoryFromBranch = config.get('publishToArtifactoryFromBranch', null)

    config.createSnapshot = config.get('createSnapshot', true)
    config.createSnapshotFromBranch = config.get('createSnapshotFromBranch', null)
    config.deployFromSnapshot = config.get('deployFromSnapshot', true)
    config.deployFromRelease = config.get('deployFromRelease', false)

    config.createRelease = config.get('createRelease', false)
    config.createReleaseFromBranch = config.get('createReleaseFromBranch', "develop")

    config.writeToVault = config.get('writeToVault', false)
    config.deployServices = config.get('deployServices', false)
    config.updatePOM = config.get('updatePOM', false)

    config.forceSnapshot = config.get('forceSnapshot', false)

    if(config.deploySnapshot && config.deployRelease) {
        log.error("Conflicting deploySnapshot and deployRelease in the config")
    }

    // deployApp config
    config.pcfAppName=config.get('pcfAppName',null)
    config.pcfAppEnv=config.get('pcfAppEnv',null)
    config.pcfOrg=config.get('pcfOrg',null)
    config.pcfSpace=config.get('pcfSpace',null)
    config.jenkinsPcfCredentialsId=config.get('jenkinsPcfCredentialsId',null)
    config.jenkinsVaultCredentialsId=config.get('jenkinsVaultCredentialsId',null)
    config.jenkinsVaultBackendId=config.get('jenkinsVaultBackendId',null)
    config.synapseEnabled=config.get('synapseEnabled',true)
    config.vaultEnabled=config.get('vaultEnabled',false)
    config.activeProfile=config.get('activeProfile',null)
    config.dirToPush=config.get('dirToPush','.')
    config.manifestLoc=config.get('manifestLoc',null)

    config.SMOKE_TEST_DEV = "DCAPI/Acceptance_Test_Jobs/PCF/Smoke_Test_Dev"
    config.SMOKE_TEST_STAGE = "DCAPI/Acceptance_Test_Jobs/PCF/Smoke_Test_Stage"
    config.SMOKE_TEST_PROD = "DCAPI/Acceptance_Test_Jobs/PCF/Smoke_Test_Prod"

    config.keyMaps = [:]
    config.pcfSpringAppList.eachWithIndex { it, index ->
        Map appConfig = config.findAll { it.key != 'pcfSpringAppList' } + it

        config.keyMaps[appConfig.pcfAppName] = [:]
        if (appConfig.useLocal) {
            String gitRepo = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
            config.pcfSpringAppList[index].gitRepo=gitRepo
            config.pcfSpringAppList[index].gitRepoBranch=env.BRANCH_NAME
        } else {
            git url: "${appConfig.gitRepo}", branch: "${appConfig.gitRepoBranch}"
        }
        stash includes: '**', name: "${appConfig.pcfAppName}-pre-workspace"
        stash includes: '**', name: "${appConfig.pcfAppName}-workspace"
    }

    log.debug("${logPrefix} config=${printToJsonString(config)}")

    return config
}

List getSecretEnvVars(Map config) {

    List envVars = []

    envVars += [usernamePassword(credentialsId: config.jenkinsArtifactoryCredId, passwordVariable: 'ARTIFACTORY_PWD', usernameVariable: 'ARTIFACTORY_USER')]

    return envVars
}

def getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}



def deployPcfAppEnv(Logger log, Map config, VaultUtil vaultUtil, CaaSUtil caasUtil) {
    String pcfAppEnv=config.pcfAppEnv
    String logPrefix = "deployPcfAppEnv(${pcfAppEnv}):"
    log.info("${logPrefix} started")

    if (config.debugPipeline) log.debug("${logPrefix} config=${printToJsonString(config)}")

    def agentLabelM3 = getJenkinsAgentLabel(config.jenkinsM3NodeLabel)
    def agentLabelDeploy = getJenkinsAgentLabel(config.jenkinsDeployNodeLabel)
    def agentLabelCaaS = getJenkinsAgentLabel(config.jenkinsCaaSNodeLabel)

    if (config.writeToVault) {
        stage("Write To Vault - ${pcfAppEnv}") {
            node(agentLabelM3 as String) {
                script {
                    insertIntoVault(log, config, pcfAppEnv, vaultUtil)
                }
            }
        }
    }

    if (config.deployServices) {
        stage("Create Services - ${pcfAppEnv}") {
            node(agentLabelDeploy as String) {
                script {
                    deployServicesToPCF(log, config)
                }
            }
        }
    }

    stage("Get Certs - ${pcfAppEnv}") {
        node(agentLabelCaaS as String) {
            script {
                getCertsForPCF(log, config, caasUtil)
            }
        }
    }

    stage("Deploy - ${pcfAppEnv}") {
        node(agentLabelDeploy as String) {
            config.pcfSpringAppList.each {
                log.info("${logPrefix} deploying it.pcfAppName=${it.pcfAppName}")
                log.debug("${logPrefix} it=${it}")

                Map appConfig = config.findAll { it.key != 'pcfSpringAppList' } + it

                log.info("${logPrefix} appConfig=${appConfig}")

                deployPCFApp(log, appConfig)
            }
        }
    }

}

def deployPCFApp(Logger log, Map config) {
    String pcfAppEnv = config.pcfAppEnv
    String pcfAppName = config.pcfAppName
    String logPrefix="deployPCFApp(${pcfAppEnv}, ${pcfAppName}):"
    log.debug("${logPrefix} starting")

    config.keyMap = config.keyMaps[config.pcfAppName]

    if (config.debugPipeline) log.debug("${logPrefix} config=${printToJsonString(config)}")

    String configEnv = pcfAppEnv.replace('-','_')
    String envLoc = pcfAppEnv.split('-')[0]
    String envPhase = pcfAppEnv.split('-')[1]

    deleteDir()
    def key = "${config.pcfOrg}${config.pcfSpace}"
    def autoscalingMap = [:]
    def workspace = "${config.pcfAppName}-workspace"
    def appHostName = config.pcfAppName
    def manifestFile = (config?.manifestFile) ? config?.manifestFile : './manifest.yml'
    def springProfile = (config?.env_profiles) ? "${envPhase}" : null
    def keyMap = config.keyMaps[config.pcfAppName]
    def instanceCount = config?.instanceCount ?: 1
    def pomFile = 'pom.xml'
    if(config.pomFile) pomFile = config?.pomFile
    def useVault = false
    def vaultCredentialsId = ''
    def vaultAppRole = ''
    def vaultBackendId = ''
    def vaultApplicationName = ''

    if(config?.vault) {
        useVault = true
        vaultCredentialsId = config?.vault?."${pcfAppEnv}"?.vaultCredentials
        vaultAppRole = config?.vault?."${pcfAppEnv}"?.appRole
        vaultBackendId = config?.vault?."${pcfAppEnv}"?.backendId
        vaultApplicationName = config?.vault?."${pcfAppEnv}"?.applicationName
    }

    if(config?.environmentOverrides?."${pcfAppEnv}") {
        config?.environmentOverrides?."${pcfAppEnv}".each {
            keyMap["PCFENV_${it.key}"] = it.value
        }
    }

    // for legacy set the instance count
    if(instanceCount > 1) {
        echo "SETTING LEGACRY INSTANCE COUNT TO ${instanceCount}"
        keyMap["instanceCount"] = "${instanceCount}"
    }

    //check to see if autoscaling is set up
    if(config?.autoscaling) {
        //update default instance count if its set
        if(config?.autoscaling?.defaultInstanceCount) keyMap["instanceCount"] = "${config?.autoscaling?.defaultInstanceCount}"

        //go through env and see if any org space match current org space and then if they have instance count
        // and if so then put that into a map for later consumption
        config?.autoscaling?.pcfEnvironments?."${configEnv}".each {
            if(config?.instanceCount) autoscalingMap["${config.pcfOrg}${config.pcfSpace}"] = config?.instanceCount
        }
    }

    if(config?.deployFromSnapshot || config?.deployFromRelease) {
        if(config?.deployFromSnapshot && config?.deployFromRelease) {
            log.error("${logPrefix} you can not set deploy from snapshot and deploy from release in the same env")
        }
        git url: "${config.gitRepo}", branch: "${config.gitRepoBranch}"
        def pom = readMavenPom file: pomFile
        def branchName = "${pom.version}"
        def baseURL = ''
        if(config?.deployFromSnapshot) {
            branchName = branchName.replace('-RELEASE','-SNAPSHOT')
            baseURL = "${config.artifactoryBaseUrl}/artifactory/snapshots"
        }
        if(config?.deployFromRelease) {
            branchName = branchName.replace('-SNAPSHOT','-RELEASE')
            baseURL = "${config.artifactoryBaseUrl}/artifactory/releases"
        }
        deleteDir()
        git url: "${config.gitRepo}", branch: "${branchName}"

        pom = readMavenPom file: pomFile
        def jarSuffix = config?.jar_suffix ?: ''
        def curlOptions = "-sS"
        if (config.debugPipeline) curlOptions = "-vsS "

        sh "curl ${curlOptions} -o ${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging} ${baseURL}/${pom.groupId.replace('.','/')}/${pom.artifactId}/${pom.version}/${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}"

        String dirToMove
        if(manifestFile.contains("/")) {
            dirToMove = manifestFile.substring(0, manifestFile.lastIndexOf("/"))
            log.debug("${logPrefix} dirToMove=${dirToMove}")
            if (dirToMove!=".") {
                sh "cp ${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging} ${dirToMove}/${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}"
            }
        }

//        sh "sed -i '/path:/c\\  path: ${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}' ${manifestFile}"
        sh "sed -i '/path:/c\\  path: ./${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}' ${manifestFile}"

        if (config.debugPipeline) {
            sh "find . -name \\*.war -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"

            log.debug("${logPrefix} updated manifest file:")
            sh "cat ${manifestFile}"
        }

    } else {
        unstash workspace
    }

    def suffix = config?.route_suffix ?: ''
    log.debug("${logPrefix} using suffix ${suffix}")

    if(useVault) {
        if (vaultApplicationName) {
            config.keyMap["vaultApplicationName"] = "${vaultApplicationName}"
        }
    }
    deployToPCFGoRouter(log, config)
}


void deployToPCFGoRouter(Logger log, Map config) {
    String logPrefix="deployToPCFGoRouter(${config.pcfAppEnv}, ${config.pcfAppName}):"
    log.debug("${logPrefix} starting")

    if (config.debugPipeline) log.debug("${logPrefix} config=${printToJsonString(config)}")

    String appHostName=config.pcfAppName
    String appDomain=config.pcfAppDomain
    String pcfApiUrl=config.pcfApiUrl
    String env=config.pcfAppEnv
    String org=config.pcfOrg
    String space=config.pcfSpace
    String pcfCredentialsId=config.jenkinsPcfCredId
    String vaultCredentialsId=config.jenkinsVaultCredentialsId
    String vaultBackendId=config.jenkinsVaultBackendId
    Map keyMap=config.keyMap
    boolean synapseEnabled=config.synapseEnabled
    boolean vaultEnabled=config.vaultEnabled
    String activeProfile=config.activeProfile
    String dirToPush=config.dirToPush
    String manifestLoc=config.manifestLoc

    def pcfAppRoute = "https://${appHostName}.${appDomain}"

    log.debug("${logPrefix} pcfAppRoute=${pcfAppRoute}")

//    withEnv(["CF_HOME=${env.WORKSPACE}"]) {
    withEnv(["CF_HOME=."]) {

        try {

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${pcfCredentialsId}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
                sh "cf login -a ${pcfApiUrl} -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
            }

            def pushString = ''
            if(manifestLoc) pushString = " -f ${manifestLoc}"

            if (config.deleteBeforePush) {
                log.info("${logPrefix} removing existing app first [${config.pcfAppName}] before pushing")
                sh "cf delete ${config.pcfAppName} -f -r"
            }

            if (config.debugPipeline) sh "find . -name \\*.war -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"

            dir(dirToPush) {
                //push application but don't start it so we can set env variables
                sh "cf push ${appHostName} ${pushString} --no-start"
            }

            log.debug("${logPrefix} activeProfile=${activeProfile}")

            if(activeProfile) {
                sh "cf set-env ${appHostName} SPRING_PROFILES_ACTIVE ${activeProfile}"
            }

            if(vaultEnabled) {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${vaultCredentialsId}", usernameVariable: 'VAULT_ID', passwordVariable: 'VAULT_SECRET']]) {
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_APP-ROLE_AUTH-PATH approle"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_APP-ROLE_ROLE-ID ${VAULT_ID}"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_APP-ROLE_SECRET-ID ${VAULT_SECRET}"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_AUTHENTICATION APPROLE"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_GENERIC_BACKEND ${vaultBackendId}"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_HOST ${env.replace('-','.')}.vault.dettonville.int"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_PORT 8200"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_SCHEME https"
                    sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_ENABLED true"
                    if(keyMap["vaultApplicationName"]) {
                        def vaultApplicationName = keyMap["vaultApplicationName"]
                        sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_GENERIC_APPLICATION-NAME ${vaultApplicationName}"
                    }
                }
            }

            def encodedClientKeyFile = keyMap["encodedKeyfile"]
            def encodedClientKeyFilePassword = keyMap["encodedKeyfilePassword"]

            sh "cf set-env ${appHostName} KEYSTORE ${encodedClientKeyFile}"
            sh "cf set-env ${appHostName} TRUSTSTORE /etc/ssl/certs/ca-certificates.crt"
            sh "cf set-env ${appHostName} KEYSTORE_PASSWORD ${encodedClientKeyFilePassword}"

            if(synapseEnabled) {
                sh "cf set-env ${appHostName} SYNAPSE_SSL_KEYSTORE_CLIENT_ALIAS client-common"
                sh "cf set-env ${appHostName} SYNAPSE_SSL_KEYSTORE_LOCATION KEYSTORE"

                switch(env) {
                    case 'nyc-dev':
                        sh "cf set-env ${appHostName} APIE_REGION US"
                        sh "cf set-env ${appHostName} APIE_PLATFORM DEV"
                        sh "cf set-env ${appHostName} APIE_ZONE STL"
                        break;
                    case 'nyc-stage':
                        sh "cf set-env ${appHostName} APIE_REGION US"
                        sh "cf set-env ${appHostName} APIE_PLATFORM STAGE"
                        sh "cf set-env ${appHostName} APIE_ZONE STL"
                        break;
                    case 'bel-prod':
                        sh "cf set-env ${appHostName} APIE_REGION EU"
                        sh "cf set-env ${appHostName} APIE_PLATFORM PROD"
                        sh "cf set-env ${appHostName} APIE_ZONE BEL"
                        break;
                    case 'jpn-':
                        sh "cf set-env ${appHostName} APIE_REGION US"
                        sh "cf set-env ${appHostName} APIE_PLATFORM PROD"
                        sh "cf set-env ${appHostName} APIE_ZONE KSC"
                        break;
                    case 'nyc-prod':
                        sh "cf set-env ${appHostName} APIE_REGION US"
                        sh "cf set-env ${appHostName} APIE_PLATFORM PROD"
                        sh "cf set-env ${appHostName} APIE_ZONE STL"
                        break;
                }
            }

            //set custom variables including any overrides
            setPCFEnvVarsFromMap(appHostName,keyMap)

            try {
                log.debug("${logPrefix} restage the application")
                sh "cf restage ${appHostName}"
            } catch (Exception err) {
                log.error("${logPrefix} restage exception occurred [${err}]")
            }

            try {
                log.debug("${logPrefix} start the application")
                sh "cf start ${appHostName}"
            } catch (Exception err) {
                log.error("${logPrefix} start exception occurred [${err}]")
            }

            if(keyMap["instanceCount"] != null) {
                def instanceCount = keyMap["instanceCount"]
                sh "cf scale ${appHostName} -i ${instanceCount}"
            }
        } finally {
            sh "cf logout"
            deleteDir()
        }
    }
}

// This sets pcf env variables from the key map
void setPCFEnvVarsFromMap(String appHostName, Map keyMap) {
    keyMap.each { k,v ->
        if(k.contains('PCFENV')) {
            echo "set ${k.replace('PCFENV_','')} to ${v}"
            sh "cf set-env ${appHostName} ${k.replace('PCFENV_','')} ${v}"
        }
    }
}


def deployServicesToPCF(Logger log, Map config) {
    String logPrefix="deployServicesToPCF(${config.pcfAppEnv}):"
    log.debug("${logPrefix} starting")

    String env=config.pcfAppEnv

    unstash 'ansible-workspace'

    def configEnv = env.replace('-','_')
    def envLoc = env.split('-')[0]
    def envPhase = env.split('-')[1]

    def org = "${config.pcfOrg}"
    def space = "${config.pcfSpace}"
    def credentialsString = "${config.credentialsString}"

    if(config.services?.cloudConfig) {
        def configRepo = "${config.services.cloud_config.gitrepo}"
        def giteaBranch = "${config.services.cloudConfig.branch}"
        def configServerName = "${config.services.cloudConfig.name}"

        def createOnly = false
        if(config.services.cloudConfig?.create_only) {
            createOnly = true
        }

        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
            ansiblePlaybook extras: "--extra-vars @shared-ansible/env/${env}.yml --extra-vars \"configRepo=${configRepo} create_only=${createOnly} configServerName=${configServerName} giteaBranch=${giteaBranch} pcfOrg=${org} pcfSpace=${space} pcf_username=$PCF_USERNAME pcf_password=$PCF_PASSWORD\"", installation: 'Ansible2', playbook: 'shared-ansible/pcf/deploy-spring-cloud-config.yml'
        }
    }
    if(config.services?.pcfServices) {
        config.services?.pcfServices.each {
            def serviceName = it.serviceName
            def service = it.service
            def plan = it.plan
            def arbitrary_params = it?.arbitrary_params ?: ''

            withEnv(["CF_HOME=."]) {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
                    sh "cf login -a api.system.${envLoc}.pcf${envPhase}00.dettonville.int -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
                }
                sh "cf create-service ${arbitrary_params} ${service} ${plan} ${serviceName}"

                sh "cf logout"
            }
        }
    }

    if(config.services?.userDefinedServices) {
        config.services?.userDefinedServices.each {

            def serviceName = config?.serviceName
            def param =  config?.param
            if(config?.environmentOverrides?."${configEnv}"?.param) {
                param = config?.environmentOverrides?."${configEnv}"?.param
            }

            log.debug("${logPrefix} PARAM ${param} for ${configEnv}")

            if(it.useCredentials) {
                def serviceCredentialString = it.credentialsString
                if(config?.environmentOverrides?."${configEnv}"?.credentialsString) {
                    serviceCredentialString = config?.environmentOverrides?."${configEnv}"?.credentialsString
                }

                withEnv(["CF_HOME=."]) {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
                        sh "cf login -a api.system.${envLoc}.pcf${envPhase}00.dettonville.int -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
                    }
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${serviceCredentialString}", usernameVariable: 'UNAME', passwordVariable: 'PWD']]) {
                        param = param.replace('#UNAME',"${UNAME}")
                        param = param.replace('#PWD',"${PWD}")
                        try {
                            sh "cf cups ${serviceName} ${param}"
                        }catch(e) {
                            sh "cf uups ${serviceName} ${param}"													}
                    }
                    sh "cf logout"
                }
            }else {
                withEnv(["CF_HOME=."]) {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
                        sh "cf login -a api.system.${envLoc}.pcf${envPhase}00.dettonville.int -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
                    }
                    try {
                        sh "cf cups ${serviceName} ${param}"
                    }catch(e) {
                        sh "cf uups ${serviceName} ${param}"													}
                }
                sh "cf logout"
            }
        }
    }

}

def notifyBuild(String buildStatus, String emailList,Boolean onSuccessEveryTime=false) {

    def sendMail = false
    def lastBuildResult = currentBuild?.getPreviousBuild()?.getResult()
    buildStatus = buildStatus ?: 'SUCCESS'

    if(onSuccessEveryTime) {
        sendMail = true
    }
    if(!lastBuildResult) {
        sendMail = true
    } else {
        if(!'SUCCESS'.equals(lastBuildResult)) {
            if('SUCCESS'.equals(buildStatus)) {
                buildStatus = 'FIXED'
                sendMail = true
            }
        }
    }

    if(!'SUCCESS'.equals(buildStatus)) sendMail = true

    if(sendMail) {
        def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
        def details = """${subject} (${env.BUILD_URL})

      STARTED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}]:

      Check console output at ${env.BUILD_URL}console"""
        def hostname = sh (returnStdout: true, script: 'hostname')
        def emailFrom = "${hostname.trim()}@dettonville.org"

        mail bcc: '', body: details, cc: '', from: emailFrom, replyTo: '', subject: subject, to: emailList
    }
}
