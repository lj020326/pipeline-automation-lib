#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    List paramList = []

    Map paramMap = [
        host : string(defaultValue: "",
                      description: "SSH hosts\nE.g., 'host01.example.com', 'host01.example.int,host02.example.int'",
                      name: 'SshHosts')
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
        if (value!="") {
            config[key] = value
        }
    }

    pipeline {
        agent any
        stages {
            stage('reset SSH host keys') {
                steps {
                    script {
                        log.info("Display env info")
                        sh "export -p | sed 's/declare -x //'"
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
