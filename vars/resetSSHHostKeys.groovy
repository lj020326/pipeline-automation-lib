#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

// Import necessary Jenkins API classes for introspection
import jenkins.model.Jenkins
import hudson.model.Node
import hudson.model.Computer

import groovy.transform.Field
@Field Logger log = new Logger(this)

List getNodeNameList() {
    String logPrefix = "getNodeNameList():"
    List nodeNameList = []
    println "${logPrefix} Initializing nodeNameList list. Current size: ${nodeNameList.size()}" // Log 1
    // Add the master node (built-in node)
    nodeNameList.add("built-in")
    println "${logPrefix} Added built-in node. Current size: ${nodeNameList.size()}" // Log 2

    // Iterate through all slave nodes
    Jenkins.instance.computers.each { computer ->
        // Check if node is not null and not master
        if (computer.node != null
            && computer.node.nodeName.length()>0
            && !["built-in","master"].contains(computer.node.nodeName))
        {
            nodeNameList.add(computer.node.nodeName)
            println "${logPrefix} Added agent node: ${computer.node.nodeName}. Current size: ${nodeNameList.size()}" // Log 3
        } else {
            println "${logPrefix} Skipping node: ${computer.node?.nodeName ?: 'null'} (is null or master)" // Log 4
        }
    }
    println "${logPrefix} Before unique and sort. Current nodes: ${nodeNameList}" // Log 5
    return nodeNameList
}

def call() {

    List nodeNameList = getNodeNameList()
    // Define parameters directly in the pipeline
    properties([
        parameters([
            string(name: 'SshHosts',
                   defaultValue: "github.com",
                   description: "SSH hosts\\nE.g., 'host01.example.com', 'host01.example.int,host02.example.int'"),
            choice(
                name: 'JenkinsNode',
                choices: nodeNameList.join('\n'),
                description: "Select the Jenkins node to run on"),
            booleanParam(
                name: 'InitializeParamsOnly',
                defaultValue: false,
                description: 'Set to true to only initialize parameters and skip execution of stages.')
        ]),
        disableConcurrentBuilds()
    ])

    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            node {
                label config.jenkinsNode
            }
        }
        stages {
            stage('Initialization Only') {
                when {
                    expression { return config.initializeParamsOnly }
                }
                steps {
                    script {
                        log.info("InitializeParamsOnly set to true. Skipping main execution stages.")
                    }
                }
            }
            stage('reset SSH host keys') {
                when {
                    // This stage will only run if InitializeParamsOnly is false
                    expression { return !config.initializeParamsOnly }
                }
                steps {
                    script {
                        log.info("Resetting SSH host keys")
                        log.info("config=${JsonUtils.printToJsonString(config)}")
                        List sshHostList = config.sshHosts.split(',')
                        log.info("sshHostList=${JsonUtils.printToJsonString(sshHostList)}")
                        sshHostList.each { String sshHost ->
                            sh "mkdir -p ~/.ssh && chmod 700 ~/.ssh"
                            log.info("clear existing ssh host key for ${sshHost}")
                            sh "ssh-keygen -R ${sshHost} || true"
                            log.info("scanning new ssh host key for ${sshHost}")
                            sh "ssh-keyscan -t rsa ${sshHost} >> ~/.ssh/known_hosts"
                            log.info("Finished resetting host key for ${sshHost}")
                        }
                        log.info("Finished resetting host keys")
                    }
                }
            }
        }
    }
}

//@NonCPS
Map loadPipelineConfig(Map params) {

    Map config = [:]

    log.debug("copy immutable params map to mutable config map")

    log.info("params=${JsonUtils.printToJsonString(params)}")

    params.each { key, value ->
        String decapitalizedKey = Utilities.decapitalize(key)
        if (value!="") {
            config[decapitalizedKey] = value
        }
    }
    config.get('logLevel', "DEBUG")
    config.get('jenkinsNode', "build-in")

    // ref: https://stackoverflow.com/questions/40261710/getting-current-timestamp-in-inline-pipeline-script-using-pipeline-plugin-of-hud
    Date now = new Date()

    String buildDate = now.format("yyyy-MM-dd", TimeZone.getTimeZone('UTC'))
    log.debug("buildDate=${buildDate}")

//     String buildId = "${env.BUILD_NUMBER}"
    String buildId = "build-${env.BUILD_NUMBER}"
    log.debug("buildId=${buildId}")

    config.get("buildId", buildId)
    config.get("buildDate", buildDate)
    config.buildLabel = "${config.buildImageLabel}:${config.buildId}"

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}
