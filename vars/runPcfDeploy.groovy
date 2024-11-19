#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utility.VaultUtil
import com.dettonville.pipeline.utility.CaaSUtil

import groovy.json.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map params=[:]) {

// //     Logger.init(this, LogLevel.INFO)
//     Logger log = new Logger(this, LogLevel.INFO)

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)

    VaultUtil vaultUtil = new VaultUtil(this)
    CaaSUtil caasUtil = new CaaSUtil(this)

    pipeline {

        agent {
            label "QA-LINUX || PROD-LINUX"
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
                            config = loadPipelineConfig(params, configFile)
                            log.info("****Loaded Config File ${configFile}")
                            log.debug("config=${printToJsonString(config)}")
                        } else {
                            log.debug("****Config file ${configFile} not present, skipping config file load and using global defaults instead...")
                        }

                        gitPullSharedAnsibleFiles('2.0.0-RELEASE')

                    }
                }
            }
            stage('Static Analysis - findbugs') {
                agent { label "QA-LINUX || PROD-LINUX" }
                when {
                    expression { config.runFindbugs }
                }
                steps {
                    script {
                        config.pcfSpringAppList.each {
                            if(it?.findbugs) {
                                deleteDir()
                                unstash "${it.pcfAppName}-pre-workspace"

                                def pomFile = ''
                                if(it?.pomFile) pomFile = " -f ${it?.pomFile}"

                                sh "mvn clean compile ${config.mvnLogOptions} ${pomFile} -Dfindbugs=true"

                                def targetFolder = ''
                                if(it?.pomFile && it?.pomFile.contains('/')) {
                                    targetFolder = it?.pomFile.substring(0,(it?.pomFile.lastIndexOf('/')+1))
                                }
                                targetFolder = "${targetFolder}target/findbugs"

                                publishHTML (target: [
                                        allowMissing: true,
                                        alwaysLinkToLastBuild: false,
                                        keepAll: true,
                                        reportDir: "${targetFolder}",
                                        reportFiles: "findbugsXml.html",
                                        reportName: "${it.pcfAppName}-findbugs"
                                ])
                                stash "${it.pcfAppName}-pre-workspace"
                            }
                        }
                    }
                }
            }

            stage('Static Analysis - Sonar') {
                agent { label "QA-LINUX || PROD-LINUX" }
                when {
                    expression { config.runSonar }
                }
                steps {
                    script {
                        config.pcfSpringAppList.each {
                            if(it.sonar == null || it.sonar) {
                                deleteDir()

                                def pomFile = 'pom.xml'
                                if(it?.pomFile) pomFile = it?.pomFile

                                unstash "${it.pcfAppName}-pre-workspace"
                                sh "mvn clean ${config.mvnLogOptions} org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent install -Dmaven.test.failure.ignore=true -f ${pomFile}"

                                runSonar(config)
                            }
                        }
                    }
                }
            }

            stage('Build') {
                agent { label "QA-LINUX || PROD-LINUX" }
                when {
                    expression { config.runBuild }
                }
                steps {
                    script {
                        config.pcfSpringAppList.each {

                            Map appConfig = config.findAll { it.key != 'pcfSpringAppList' } + it
                            if(!appConfig?.skipBuild) {
                                deleteDir()
                                unstash "${appConfig.pcfAppName}-pre-workspace"

                                mvnPackage(appConfig)

                                stash includes: '**', name: "${appConfig.pcfAppName}-workspace"
                            }
                        }
                    }
                }
            }

            stage('Publish to Artifactory') { // only publishing to artifactory from the develop branch
                agent { label "QA-LINUX || PROD-LINUX" }
                when {
                    expression { config.publishToArtifactory }
                    anyOf {
                        expression { config.publishToArtifactoryFromBranch==null }
                        expression { config.gitRepoBranch==config.publishToArtifactoryFromBranch }
                    }
                }
                steps {
                    script {

                        config.pcfSpringAppList.each {

                            Map appConfig = config.findAll { it.key != 'pcfSpringAppList' } + it
                            log.info("deploying to artifactory")

                            deleteDir()
                            unstash "${appConfig.pcfAppName}-pre-workspace"

                            publishToArtifactory(appConfig)
                        }
                    }
                }
            }

            stage('Create Snapshot') {  // only create snapshots from the develop branch
                agent { label "QA-LINUX || PROD-LINUX" }
                when {
                    expression { config.createSnapshot }
                    anyOf {
                        expression { config.createSnapshotFromBranch==null }
                        expression { config.gitRepoBranch==config.createSnapshotFromBranch }
                    }
                }
                steps {
                    script {
                        config.pcfSpringAppList.each {

                            Map appConfig = config.findAll { it.key != 'pcfSpringAppList' } + it
                            log.info("creating snapshot")

                            deleteDir()
                            git url: "${appConfig.gitRepo}", branch: "${appConfig.gitRepoBranch}"

                            config.updatePOM = createSnapshot(appConfig)
                        }

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

//                            Map envConfig = config.findAll { it.key != 'pcfDeployEnvironmentList' } + it
                            Map appEnvConfig = config.pcfEnvironments[pcfAppEnv] + config.findAll { !["pcfDeployEnvironmentList","pcfEnvironments"].contains(it.key) } + it
                            log.debug("appEnvConfig=${printToJsonString(appEnvConfig)}")

                            deployPcfAppEnv(appEnvConfig, vaultUtil, caasUtil)
                        }
                    }
                }
            }

        }
        post {
            changed {
                script {
                    log.debug("**** post[changed] currentBuild.result=${currentBuild.result}")
                    if(config.notifications?.changed?.email?.address_list) {

                        String addressList = config.notifications.changed.email.address_list.join(",")

                        notifyBuild(config, addressList, currentBuild, "CHANGED")
                    }
                }
            }
            always {
                script {
                    log.debug("**** post[always] currentBuild.result=${currentBuild.result}")

                    def duration = "${currentBuild.durationString.replace(' and counting', '')}"
                    currentBuild.description = "Deploy Duration: ${duration}"

                    postReleaseUpdatePom(config)

                    if(config.notifications?.always?.email?.address_list) {

                        String addressList = config.notifications.always.email.address_list.join(",")
                        echo "addressList is ${addressList}"
                        notifyBuild(config, addressList, currentBuild)
                    }

                }
            }
        }
    }
}


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


Map loadPipelineConfig(Map params, String configFile=null) {
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
    log.debug("${logPrefix} config1=${printToJsonString(config)}")

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

    config.deploySnapshot=config.get('deploySnapshot',true)
    config.deployRelease=config.get('deployRelease', false)
    config.pcfDeployEnvironmentsList.each {
        if(it?.deploySnapshot && it?.deployRelease) {
            error("${it} has conflicting deploySnapshot [${it.deploySnapshot}] and deployRelease [${it?.deployRelease}] in the config")
        }
    }

    // ref: https://stackoverflow.com/questions/21638697/disable-maven-download-progress-indication
    // ref: https://stackoverflow.com/questions/17979685/disable-maven-execution-debug-output
    // ref: https://books.sonatype.com/mvnref-book/reference/running-sect-options.html
    config.mvnLogOptions=config.get('mvnLogOptions', "-B -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
//    config.mvnLogOptions=config.get('mvnLogOptions', "-B -Dorg.slf4j.simpleLogger.defaultLogLevel=warn")

    config.debugMvn=config.get('debugMvn', false)
    if (config.debugMvn) {
        config.mvnLogOptions="-B -X"
    }

    config.gitRepoBranch = config.gitRepoBranch ?: env.BRANCH_NAME ?: "develop"

    log.debug("${logPrefix} env.BRANCH_NAME = ${env.BRANCH_NAME}")
    log.debug("${logPrefix} config.gitRepoBranch = ${config.gitRepoBranch}")

//    config.useConfigFile = config.get('useConfigFile', false)
    config.useConfigFile = config.get('useConfigFile', true)
//    config.configFile = config.configFile ?: "jenkins/${config.gitRepoBranch}.yml"
    config.configFile = config.configFile ?: "jenkins/runPcfDeploy.yml"
    config.checkoutDir = config.get('checkoutDir', '.')

    config.jenkinsArtifactoryCredId = config.get('jenkinsArtifactoryCredId', null)
    config.jenkinsBitbucketCredId = config.get('jenkinsBitbucketCredId', null)

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
    config.emailFrom=config.get('emailFrom',"DCAPI.pcfDeployAutomation@dettonville.com")

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


void mvnPackage(Map config) {
    String logPrefix = "mvnPackage():"
    log.debug("${logPrefix} started")

    String buildCmd = "mvn clean package ${config.mvnLogOptions}"

    if (config?.pomFile) buildCmd += " -f ${config.pomFile}"

    if (!config.skipTests) {
        buildCmd += " -DskipTests=true"
    }

    log.debug("${logPrefix} BUILD CMD: ${buildCmd}")

    if (config.useSimulationMode) {
        log.info("**** USING SIMULATION MODE - following command not actually run *****")
        log.info("${buildCmd}")
    } else {
        log.info("mvnCmd=${buildCmd}")
        sh "${buildCmd}"
    }
}

void publishToArtifactory(Map config) {
    String logPrefix = "publishToArtifactory():"
    log.debug("${logPrefix} started")

    String jenkinsArtifactoryCredId = config.jenkinsArtifactoryCredId
    String pomLoc = config.pomLoc

    String pomFlag = ''
    if(pomLoc) pomFlag = "-f ${pomLoc}"

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${jenkinsArtifactoryCredId}", usernameVariable: 'UNAME', passwordVariable: 'PASS']]) {
        sh """
      export ARTIFACTORY_USR=${UNAME}
      export ARTIFACTORY_PSW=${PASS}
      mvn clean deploy ${config.mvnLogOptions} -DskipTests=true ${pomFlag}
      """
    }
}


boolean createSnapshot(Map config) {
    String logPrefix = "createSnapshot():"
    log.debug("${logPrefix} started")

    boolean updatePOM = false

    String pomFile = 'pom.xml'
    if(config?.pomFile) pomFile = config?.pomFile
    def pom = readMavenPom file: pomFile

    if(pom.version.contains('RELEASE')) {
        log.error("${logPrefix} You can not create a snapshot from a release version")
    }

    def versionNUmberToUpdate = pom.version

    def result = sh (returnStdout: true, script: "git ls-remote --heads ${config.gitRepo} ${versionNUmberToUpdate} | wc -l")
    log.debug("${logPrefix} # of git branches matching [${versionNUmberToUpdate}] is ${result}")

    if (result.contains('1') && !config.forceSnapshot) {
        log.error("${logPrefix} There is already a snapshot branch created for ${versionNUmberToUpdate}.")
    }else {
        String remoteOrigin = config.gitRepo.replace('https://','')
        log.debug("${logPrefix} remoteOrigin=${remoteOrigin}")

        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: config.jenkinsBitbucketCredId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
            sh('git config --global user.email "jenkins@dettonville.com"')
            sh('git config --global user.name "Jenkins Pipeline"')
            String bitbucketCreds="${GIT_USERNAME}:${GIT_PASSWORD}"
            String remoteUrl = "https://${bitbucketCreds}@${remoteOrigin}"
            log.debug("${logPrefix} remoteUrl=${remoteUrl}")
            sh("git remote set-url origin '${remoteUrl}'")
            sh("git checkout -b ${versionNUmberToUpdate}")
            sh("git push -u origin ${versionNUmberToUpdate}")

            publishToArtifactory(config)
            updatePOM = true
        }
    }
    return updatePOM
}

def insertIntoVault(Map config, VaultUtil vaultUtil) {
    String logPrefix="insertIntoVault(${config.pcfAppEnv}):"
    log.debug("${logPrefix} starting")

    String env=config.pcfAppEnv
    def configEnv = env.replace('-','_')
    def vaultCredentials = config?.vault?.environments?."${configEnv}"?.vaultCredentials
    def verbose = false
    if(config?.vault?.environments?."${configEnv}"?.verbose.equalsIgnoreCase('true')) {
        verbose = true
    }
    def directory = config?.vault?.environments?."${configEnv}"?.directory

    def insertMap = [:]
    config?.vault?.environments?."${configEnv}"?.insert.each {
        insertMap[it.key] = it.value
    }
    
    config?.vault?.environments?."${configEnv}"?.jenkinsCredentials.each {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${it.credentialsId}", usernameVariable: 'KEY', passwordVariable: 'VALUE']]) {
            insertMap[it.key] = it.value
        }
    }
    
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${vaultCredentials}", usernameVariable: 'ROLE_ID', passwordVariable: 'SECRET_ID']]) {
        vaultUtil.writeSecrets(this, env, ROLE_ID, SECRET_ID, directory,insertMap,verbose)
    }

}

def getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}

def deployPcfAppEnv(Map config, VaultUtil vaultUtil, CaaSUtil caasUtil) {
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
                    insertIntoVault(config, vaultUtil)
                }
            }
        }
    }

    if (config.deployServices) {
        stage("Create Services - ${pcfAppEnv}") {
            node(agentLabelDeploy as String) {
                script {
                    deployServicesToPCF(config)
                }
            }
        }
    }

    stage("Get Certs - ${pcfAppEnv}") {
        node(agentLabelCaaS as String) {
            script {
                getCertsForPCF(config, caasUtil)
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

                deployPCFApp(appConfig)
            }
        }
    }

    if (config.runTests) {
        stage("Run Spock Tests - ${pcfAppEnv}") {
            node(agentLabelM3 as String) {
                script {
                    runSpockTestsInPCF(config)
                }
            }
        }
    }

    if (config.runJMeter) {
        stage("Run JMeter - ${pcfAppEnv}") {
            node(agentLabelM3 as String) {
                script {
                    runPerformanceTestsInPCF(config)
                }
            }
        }
    }

//            stage("Run ATH Smoke Test - ${pcfAppEnv}") {
//                steps {
//                    script {
//                        recipients = emailextrecipients([
//                                [$class: 'CulpritsRecipientProvider'],
//                                [$class: 'DevelopersRecipientProvider'],
//                                [$class: 'RequesterRecipientProvider']
//                        ])
//                        List paramList = [
//                                [$class: 'StringParameterValue', name: 'alwaysEmailList', value: recipients]
//                        ]
//
//                        build job: "${SMOKE_TEST}", parameters: paramList, wait: true
//                    }
//                }
//            }

}

def deployPCFApp(Map config) {
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
    deployToPCFGoRouter(config)
}


void deployToPCFGoRouter(Map config) {
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


def deployServicesToPCF(Map config) {
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


def runSpockTestsInPCF(Map config) {
    String logPrefix="runSpockTestsInPCF(${config.pcfAppEnv}, ${config.pcfAppName}):"
    log.debug("${logPrefix} starting")

    String env=config.pcfAppEnv
    def configEnv = env.replace('-','_')
    def envLoc = env.split('-')[0]
    def envPhase = env.split('-')[1]
    def publishTests = true

    if(config?.integration_tests?.publish == false) {
        publishTests = false
    }

    if(config?.integration_tests) {
        if(config.pcfEnvironments?."${configEnv}") {
            deleteDir()
            git branch: "${config.integration_tests.branch}", url: "${config.integration_tests.gitrepo}"

            config.pcfEnvironments?."${configEnv}".each {
                def target_service_baseurl = " -Dtarget.service.base-url=\".apps.${envLoc}.pcf${envPhase}00.dettonville.int\""
                def app_route_suffix = ''
                def skipIntegrationTests = false
                if(config?.skip_integration_tests) {
                    skipIntegrationTests = true
                }
                def title_suffix = config?.pcfSpace
                if(config?.route_suffix) {
                    app_route_suffix = " -Dapp.route.suffix=${config?.route_suffix}"
                }
                def testingParams = ''
                if(config?.testing_params) {
                    testingParams = " ${config?.testing_params}"
                }

                def ignoreFailures = "false"
                if(config?.release?.integration_tests?.ignore_failures) {
                    ignoreFailures = "true"
                }

                def failBuild = false

                if(!skipIntegrationTests) {
                    try {
                        sh "${tool 'M3'}/bin/mvn clean test -Dmaven.test.failure.ignore=${ignoreFailures}${target_service_baseurl}${app_route_suffix}${testingParams}"
                    }catch(e) {
                        failBuild = true
                    }
                    if(publishTests) {
                        publishHTML (target: [
                                allowMissing: false,
                                alwaysLinkToLastBuild: false,
                                keepAll: true,
                                reportDir: "target/spock-reports",
                                reportFiles: "index.html",
                                reportName: "${env}-${title_suffix}-${config.integration_tests.spockreport_title}"
                        ])

                        publishHTML (target: [
                                allowMissing: false,
                                alwaysLinkToLastBuild: false,
                                keepAll: true,
                                reportDir: "target/api-report",
                                reportFiles: "index.html",
                                reportName: "${env}-${title_suffix}-${config.integration_tests.timingreport_title}"
                        ])
                    }
                    if(failBuild) {
                        error("build failed due to integration tests")
                    }
                }
            }
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

void gitPullSharedAnsibleFiles(String branch = null) {

    if(branch) git branch: "${branch}", url: "https://gitrepository.dettonville.int/stash/scm/ca/shared-pct-ansible-libraries.git"
    else git url: "https://gitrepository.dettonville.int/stash/scm/ca/shared-pct-ansible-libraries.git"

    stash includes: '**', name: 'ansible-workspace'
}


void setupPcfServices(Map config) {
    String logPrefix="setupPcfServices():"
    log.info("${logPrefix} starting")

    config.pcfServices.each { Map pcfServiceConfig ->

        log.info("${logPrefix} adding/updating pcfServiceConfig=${pcfServiceConfig}")

        switch (pcfServiceConfig.type) {
            case "postgres":
                setupPostgresService(pcfServiceConfig)
                break
            default: log.warn("unknown/unhandled service type: ${pcfServiceConfig.type}")
        }
    }

    log.info("${logPrefix} all services setup and ready")

}

void setupPostgresService(Map config) {
    String logPrefix="setupPostgresService():"
    log.info("${logPrefix} starting")

    withCredentials([usernamePassword(credentialsId: pcfServiceConfig.jenkinsCredId, passwordVariable: 'DB_PWD', usernameVariable: 'DB_USER')]) {

        config.username = "${DB_USER}"
        config.password = "${DB_PWD}"
        config.uri = "postgres://${DB_USER}:${DB_PWD}@${config.serviceUrl}"

        String dbServiceJson = JsonOutput.toJson(config)
        log.info("${logPrefix} dbServiceJson = [${dbServiceJson}]")
        log.info("${logPrefix} service config=${printToJsonString(config)}")

        int serviceExists = sh(script: "cf service ${config.pcfServiceName}", returnStatus: true)
        log.info("${logPrefix} cf services status = [${serviceExists}]")
        if (!serviceExists) {
            log.info("${logPrefix} service already exists so updating existing service")
            sh("cf uups ${config.pcfServiceName} -p '${dbServiceJson}'")
        } else {
            log.info("${logPrefix} creating user provided service [${config.dbServiceName}]")
            sh("cf cups ${config.pcfServiceName} -p '${dbServiceJson}'")
        }

    }

    // ref: https://artifacts.dettonville.int/stash/projects/MRS_RFRSH/repos/zeus-pipelines/browse/ls-security/services/Jenkinsfile
    log.info("${logPrefix} wait until service is ready...")
    int x = 0
    while(true) {
        x++
        update_output = sh(script: "cf service ${config.dbServiceName}", returnStdout: true).trim()
        sleep 20
        if(!update_output.contains('create in progress') || x >= 20) break
    }
    log.info("${logPrefix} service is ready")

}



def getCertsForPCF(Map config, CaaSUtil caasUtil) {
    String logPrefix="getCertsForPCF(${config.pcfAppEnv}):"
    log.debug("${logPrefix} starting")

    String env=config.pcfAppEnv

    def configEnv = env.replace('-','_')

    config.pcfSpringAppList.each {
//        if(config.release.pcfEnvironments?."${configEnv}") {
        if(config.pcfEnvironments?."${env}") {
            deleteDir()
            def keyMap = keyMaps[it.pcfAppName]
            if(it.ouName) {
                caasUtil.getJKSFromCaaS(this, it.pcfAppName, env, keyMap, it.cnName, it.ouName)
            }else {
                caasUtil.getJKSFromCaaS(this, it.pcfAppName, env, keyMap, it.cnName)
            }
        }
    }
}

def runPerformanceTestsInPCF(Map config) {
    String logPrefix="runPerformanceTestsInPCF(${config.pcfAppEnv}, ${config.pcfAppName}):"
    log.debug("${logPrefix} starting")

    String env=config.pcfAppEnv

    def configEnv = env.replace('-','_')
    def envLoc = env.split('-')[0]
    def envPhase = env.split('-')[1]

    if(!config?.performance_tests) {
        log.debug("${logPrefix} skipping")
        return
    }

    deleteDir()
    git branch: "${config.performance_tests.gitRepoBranch}", url: "${config.performance_tests.gitRepo}"

    def target_service_baseurl = " -Dtarget.service.base-url=\".apps.${envLoc}.pcf${envPhase}00.dettonville.int\""
    def app_route_suffix = ''
    def skipPerformanceTests = false
    if(config?.skip_performance_tests) {
        skipPerformanceTests = true
    }
    def title_suffix = config?.pcfSpace
    if(config?.route_suffix) {
        app_route_suffix = " -Dapp.route.suffix=${config?.route_suffix}"
    }
    def performanceParams = ''
    if(config?.performance_params) {
        performanceParams = " ${config?.performance_params}"
    }

    if(!skipPerformanceTests) {
        echo "skipping performance tests in space ${title_suffix}"

        def failBuild = false
        try {
            sh "${tool 'M3'}/bin/mvn clean verify -Pperformance -Dmaven.test.failure.ignore=true${target_service_baseurl}${app_route_suffix}${performanceParams}"
        }catch(e) {
            failBuild = true
        }

        sh "cp -r target/jmeter/reports/*/* target/jmeter/reports"

        publishHTML (target: [
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: "target/jmeter/reports",
                reportFiles: "index.html",
                reportName: "${env}-${title_suffix}-${config.performance_tests?.publish_title}"
        ])

        if(failBuild) {
            error("build failed due to performance tests")
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
        def emailFrom = "${hostname.trim()}@dettonville.com"

        mail bcc: '', body: details, cc: '', from: emailFrom, replyTo: '', subject: subject, to: emailList
    }
}

def postReleaseUpdatePom(Map config) {
    String logPrefix="postReleaseUpdatePom():"
    log.info("${logPrefix} starting")

    if (config.debugPipeline) log.debug("${logPrefix} config=${printToJsonString(config)}")

    if (config.updatePOM) {
        config.pcfSpringAppList.each {
            Map appConfig = config.findAll { it.key != 'pcfSpringAppList' } + it

//            def bitbucketCreds = appConfig?.bitbucket_creds
            def bitbucketCreds = appConfig?.jenkinsBitbucketCredId
            deleteDir()
            git url: "${appConfig.gitRepo}", branch: "${appConfig.gitRepoBranch}"

            def pomFile = 'pom.xml'
            if(appConfig?.pomFile) pomFile = appConfig?.pomFile
            def pom = readMavenPom file: pomFile

            def versionNUmberToUpdate = pom.version
            def lastPart = versionNUmberToUpdate.replace('-SNAPSHOT','').tokenize('.').last()
            int intVersion = -1
            try {
                intVersion = lastPart as Integer
            }catch(e) {
                error("unable to update POM version because there was an error converting version to integer(${pom.version})")
            }

            intVersion++
            int versionSize = versionNUmberToUpdate.tokenize('.').size()
            def versionArray = versionNUmberToUpdate.tokenize('.')

            def newVersion = ''
            for(int i=0;i<versionSize-1;i++) {
                newVersion += versionArray[i]
                newVersion += '.'
            }
            newVersion += intVersion
            newVersion += '-SNAPSHOT'

            def remoteOrigin = appConfig.gitRepo.replace('https://','')

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: bitbucketCreds, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
                sh('git config --global user.email "jenkins@dettonville.com"')
                sh('git config --global user.name "Jenkins Pipeline"')
                sh("git remote set-url origin 'https://${GIT_USERNAME}:${GIT_PASSWORD}@${remoteOrigin}'")

                pom.version = newVersion
                writeMavenPom model: pom

                sh("git add ${pomFile}")
                sh("git commit -m '[maven-release-plugin] updated version for ${appConfig.gitRepoBranch}'")
                sh("git push --set-upstream origin ${appConfig.gitRepoBranch}")
            }
        }
    }

}

def sendReports(Map config, String emailDist, def currentBuild, String notifyAction=null) {
    if (["SUCCESS", "FAILED", "ABORTED"].contains(env.ACCEPTANCE_TESTS)) {
        if (config.sendJbehaveRpt) {
            dir("target/jbehave/view") {
                sendEmailTestReport(config, emailDist, currentBuild, config.jbehaveRpt, config.jbehaveRptName, notifyAction)
            }
        }
        dir("target/site/serenity/") {
            if (config.sendSerenityRpt) {
                sendEmailTestReport(config, emailDist, currentBuild, config.serenityRpt, config.serenityRptName, notifyAction)
            }
            if (config.sendSerenitySnapshot) {
                def baseUrl = "${BUILD_URL}${config.serenityRptName}/"
                sendEmailRptSnapshot(config, emailDist, currentBuild, "${config.snapFile}.png", config.serenityRptName, baseUrl, notifyAction)
            }
        }
    } else {
        if (config.sendSerenityRpt) {
            sendEmailTestReport(config, emailDist, currentBuild, config.serenityRpt, notifyAction)
        }
    }
}

/**
 * Call CaaS with CSR and get back a multiple certificates for synpase as well as CaaS integration,
 * add the sub CA's to the root and turn the pem into a JKS, then combine the JKS files into a sungle JKS
 *
 * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
 * @param appHostName Name of the app, should be same as in manifest.
 * @param env what env we want the certs for. values can be 'nyc-dev', 'nyc-stage', or 'bel-prod'
 * @param returnMap A Map shared back to the caller where it can add the certs and passwords to
 * @param cnForCert the CN name you want the cert to have. usually your appName + -client. like dcuser-switch-account-registry-services-client
 */
//void getJKSFromCaaS(script, String appHostName, String env, Map returnMap, String cnForCert,String ouForCert=null) {
void getJKSFromCaaS(Map config) {
    String logPrefix="getJKSFromCaaS(${config.pcfAppEnv}, ${config.pcfAppName}):"
    log.debug("${logPrefix} starting")

    String appHostName=config.pcfAppName
    String env=config.pcfAppEnv
    String cnForCert=config.pcfAppCnName
    String ouForCert=config.pcfOuForCert
    Map returnMap=[:]

    def nyc_devTruststoreFilename = "devpaas-truststore.jks"
    def nyc_devTruststorePassword = "password"
    def nyc_stageTruststoreFilename = "stage-cacerts.jks"
    def nyc_stageTruststorePassword = "password"
    def bel_prodTruststoreFilename = "prod-cacerts.jks"
    def bel_prodTruststorePassword = "changeit"
    def nonProdPwdLabel = "X509-Default-non-PRD"
    def prodPwdLabel = "X509-prd-ci-cd-sw"
    def util = new com.dettonville.api.pipeline.utility.Utilities()
    def synapseClientProfileId
    def synapseClientJurisdictionId
    def otherClientProfileId
    def otherClientJurisdictionId
    def caasEndpoint
    def credentialsId
    def pwdLabel
    def truststoreFilename
    def truststorePassword
    def nyc_devDomain = "apps.nyc.pcfdev00.dettonville.int"
    def nyc_stageDomain = "apps.nyc.pcfstage00.dettonville.int"
    def bel_prodDomain = "apps.bel.pcfprod00.dettonville.int"
    def nyc_prodDomain = "apps.nyc.pcfprod00.dettonville.int"
    def jpn_prodDomain = "apps.jpn.pcfprod00.dettonville.int"
    def domain

    git branch: "master", url: "https://gitrepository.dettonville.int/stash/scm/ca/truststore-certs.git"

    switch(env) {
        case 'nyc-dev':   truststorePassword = nyc_devTruststorePassword;
            truststoreFilename = nyc_devTruststoreFilename;
            pwdLabel = nonProdPwdLabel;
            synapseClientProfileId = globalVars.nyc_devSynapseClientProfileId;
            otherClientProfileId =  globalVars.nyc_devOtherClientProfileId;
            synapseClientJurisdictionId = globalVars.nyc_devSynapseClientJurisdictionId;
            otherClientJurisdictionId = globalVars.nyc_devOtherClientJurisdictionId;
            caasEndpoint = globalVars.devCaaSEndpoint;
            credentialsId = 'dev_caas_conversion';
            domain =  nyc_devDomain;
            break;
        case 'nyc-stage': truststorePassword = nyc_stageTruststorePassword;
            truststoreFilename = nyc_stageTruststoreFilename;
            pwdLabel = nonProdPwdLabel;
            synapseClientProfileId = globalVars.nyc_stageSynapseClientProfileId;
            otherClientProfileId =  globalVars.nyc_stageOtherClientProfileId;
            synapseClientJurisdictionId = globalVars.nyc_stageSynapseClientJurisdictionId;
            otherClientJurisdictionId = globalVars.nyc_stageOtherClientJurisdictionId;
            caasEndpoint = globalVars.stageCaaSEndpoint;
            credentialsId = 'stage_caas_conversion';
            domain =  nyc_stageDomain;
            break;
        case 'bel-prod':  truststorePassword = bel_prodTruststorePassword;
            truststoreFilename = bel_prodTruststoreFilename;
            pwdLabel = prodPwdLabel;
            synapseClientProfileId = globalVars.bel_prodSynapseClientProfileId;
            otherClientProfileId =  globalVars.bel_prodOtherClientProfileId;
            synapseClientJurisdictionId = globalVars.bel_prodSynapseClientJurisdictionId;
            otherClientJurisdictionId = globalVars.bel_prodOtherClientJurisdictionId;
            caasEndpoint = globalVars.prodCaaSEndpoint;
            credentialsId = 'prod_caas_conversion';
            domain =  bel_prodDomain;
            break;
        case 'jpn-':  truststorePassword = bel_prodTruststorePassword;
            truststoreFilename = bel_prodTruststoreFilename;
            pwdLabel = prodPwdLabel;
            synapseClientProfileId = globalVars.bel_prodSynapseClientProfileId;
            otherClientProfileId =  globalVars.bel_prodOtherClientProfileId;
            synapseClientJurisdictionId = globalVars.bel_prodSynapseClientJurisdictionId;
            otherClientJurisdictionId = globalVars.bel_prodOtherClientJurisdictionId;
            caasEndpoint = globalVars.prodCaaSEndpoint;
            credentialsId = 'prod_caas_conversion';
            domain =  jpn_prodDomain;
            break;
        case 'nyc-prod':  truststorePassword = bel_prodTruststorePassword;
            truststoreFilename = bel_prodTruststoreFilename;
            pwdLabel = prodPwdLabel;
            synapseClientProfileId = globalVars.bel_prodSynapseClientProfileId;
            otherClientProfileId =  globalVars.bel_prodOtherClientProfileId;
            synapseClientJurisdictionId = globalVars.bel_prodSynapseClientJurisdictionId;
            otherClientJurisdictionId = globalVars.bel_prodOtherClientJurisdictionId;
            caasEndpoint = globalVars.prodCaaSEndpoint;
            credentialsId = 'prod_caas_conversion';
            domain =  nyc_prodDomain;
            break;
    }

    try {

        def oValueLookup = ['synapse': 'Dettonville - CentralAuth', 'other': 'Dettonville - Common ProdInfra SSL']
        def typesOfCerts = ['synapse','other']

        typesOfCerts.each { certType ->

            def oValue = oValueLookup[certType]
            def jurisdictionId
            def profileId
            def alias

            switch(certType) {
                case 'synapse':   jurisdictionId = synapseClientJurisdictionId;
                    profileId = synapseClientProfileId;
                    alias = 'client-access'
                    break;
                case 'other':     jurisdictionId = otherClientJurisdictionId;
                    profileId = otherClientProfileId;
                    alias = 'client-common'
                    break;
            }

            def csrFile = "sslcert-${certType}-${env}.csr"
            def jsonFile = "sslcert-${certType}-${env}.json"
            def caasResponseFile = "caas_response-${certType}-${env}.json"
            def fullPemFile = "sslcert-${certType}-${env}.pem"

            //figure out if we are overriding ou value. it defaults to env
            def ouValue = env
            if(ouForCert) ouValue = ouForCert

            //Create the private keys in the same keystore
            sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -keystore multiclient.jks -genkeypair -alias ${alias} -keyalg rsa -keysize 2048 -dname \"CN=${cnForCert}, OU=${ouValue}, O=${oValue}\" -ext \"san=dns:${appHostName}.${domain}\""

            //Create the cert request from the private keys in the keystore
            sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -certreq -keystore multiclient.jks -alias ${alias} -file ${csrFile}"

            //create the json request for caas. remove the word NEW as that is a type of CSR that caas does not accept but that is what the keytool gives us.
            def replacefile = readFile(csrFile)
            replacefile = replacefile.replace("END NEW CERTIFICATE REQUEST", "END CERTIFICATE REQUEST")
            replacefile = replacefile.replace("BEGIN NEW CERTIFICATE REQUEST", "BEGIN CERTIFICATE REQUEST")
            //replace actual line breaks with newline character sequence for the json request to caas
            String massagedFile = replacefile.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n")
            def requestTemplate = """
          {
            "p10":"${massagedFile}",
            "jurisdictionId":"${jurisdictionId}",
            "profileId":"${profileId}"
          }
          """

            writeFile file: "${jsonFile}", text: requestTemplate
            def jsonFileString = readFile(jsonFile)
            //echo "jsonFileString: ${jsonFileString}"

            def csrFileString = readFile(csrFile)
            //echo "csrFileString: ${csrFileString}"

            //echo "csrFileString after NEW removed: ${replacefile}"

            //get password for caas
            def caas_pwd = sh(returnStdout: true, script: "mcgetpw -label ${pwdLabel} | tr -d '\n'")

            // send request to caas
            sh(returnStdout: true, script: "#!/bin/sh -e\n curl --tlsv1.2 -H 'Content-Type: application/json' -k -X POST --key /apps_data_01/security/keystores/jenkins-agent.key --cert /apps_data_01/security/keystores/jenkins-agent.crt --pass ${caas_pwd} --cacert /apps_data_01/security/keystores/jenkins-agent-truststore.pem -T ${jsonFile} ${caasEndpoint}cert/sign > ${caasResponseFile}")

            //get caas_response.json into a string
//            def responseString = util.getStringFromFile(caasResponseFile)
            def responseString = readFile caasResponseFile
            echo "responseString: ${responseString}"

            //parse it for pem
//            def pemText = util.parseJSON(responseString).certificate
            def pemText = readJSON responseString

            def subCaPemText = util.parseJSON(responseString).caCertificates

            concatCerts(pemText,subCaPemText,env,certType)

            def fullPemFileString = readFile(fullPemFile)
            //echo "fullPemFileString: ${fullPemFileString}"

            //import the signed certificates using same aliases as private keys
            sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -import -keystore multiclient.jks -file ${fullPemFile} -alias ${alias}"

            sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -list -v -keystore multiclient.jks"

            //encode keyfile password while in code block that has access
            returnMap["encodedKeyfilePassword"] = sh(returnStdout: true, script: "printf password | base64")
        }

        returnMap["encodedKeyfile"] = sh(returnStdout: true, script: "base64 -i multiclient.jks -w 0")
        returnMap["encodedTruststore"] = sh(returnStdout: true, script: "base64 -i ${truststoreFilename} -w 0")
        returnMap["encodedTruststorePassword"] = sh(returnStdout: true, script: "printf ${truststorePassword} | base64")

    } finally {
        deleteDir()
    }

}

void concatCerts(String cert,ArrayList subCa,String env, String certType) {

    writeFile file: "sslcert-${certType}-${env}-test1.pem", text: cert
    int x = 0
    subCa.each {
        writeFile file: "sslcert-${certType}-${env}-test${x+2}.pem", text: "${it}"
        x++;
    }

    def catString = "cat "
    for(int i = 1;i<=(x+1);i++) {
        catString += "sslcert-${certType}-${env}-test${i}.pem "
    }
    catString += ">> sslcert-${certType}-${env}.pem"

    //echo "catString:"
    //echo catString

    sh "#!/bin/sh -e\n ${catString}"

}
