#!/usr/bin/env groovy

@Library("pipelineAutomationLib")

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def getBSAgent(Map config) {

    if (!fileExists("${config.bsAgentBinPath}/BrowserStackLocal")) {
        sh "mkdir -p ${config.bsAgentBinPath}"

//            sh "curl -sS https://www.browserstack.com/browserstack-local/BrowserStackLocal-${type}.zip > ${config.bsAgentBinPath}/BrowserStackLocal.zip"
//            sh "unzip -o ${config.bsAgentBinPath}/BrowserStackLocal.zip -d /var/tmp"
//            sh "chmod +x ${config.bsAgentBinPath}/BrowserStackLocal"

        log.debug("Fetching Browserstack agent")
        dir('deploy_configs') {
            checkout scm: [
                    $class: 'GitSCM',
                    branches: [[name: "main"]],
                    userRemoteConfigs: [[credentialsId: 'dcapi_ci_vcs_user', url: 'https://gitrepository.dettonville.int/stash/scm/api/deployment_configs.git']]
            ]
        }
        def envPath="deploy_configs/resources/BrowserStackLocal-${config.bsAgentBinType}.zip"

//        sh 'find deploy_configs -type f'

        sh "cp -r ${envPath} ${config.bsAgentBinPath}"

        sh "unzip -o ${config.bsAgentBinPath}/BrowserStackLocal-${config.bsAgentBinType}.zip -d ${config.bsAgentBinPath}"
        sh "chmod +x ${config.bsAgentBinPath}/BrowserStackLocal"
    }

}

node ('DEVCLD-LIN7') {
//     Logger.init(this, LogLevel.INFO)
//     Logger log = new Logger(this)

    Map config = [:]

    config.bsAgentBinType = "linux-x64"
    config.bsAgentBinPath = "tmp"

    getBSAgent(config)

    sh " ${config.bsAgentBinPath}/BrowserStackLocal --version "

}

