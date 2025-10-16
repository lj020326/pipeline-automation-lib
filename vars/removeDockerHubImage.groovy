#!/usr/bin/env groovy
import com.dettonville.pipeline.utils.JsonUtils

// ref: https://devopsheaven.com/docker/dockerhub/2018/04/09/delete-docker-image-tag-dockerhub.html
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    List paramList = [
            string(defaultValue: "org/image_1:tag_1 org/image_2:tag_2 ...", description: "Specify docker hub images/labels to remove", name: 'DockerImageLabels')
    ]

    properties([
        parameters(paramList)
    ])

    params.each { key, value ->
        key = Utilities.decapitalize(key)
        if (value!="") {
            config[key] = value
        }
    }

    pipeline {

        agent {
            label "docker-in-docker"
        }

        stages {

            stage("Remove Docker Registry Images/Labels") {
                steps {
                    script {

                        log.info("config=${JsonUtils.printToJsonString(config)}")
                        withCredentials([usernamePassword(credentialsId: "docker-registry-admin", passwordVariable: 'REG_PWD', usernameVariable: 'REG_USER')]) {
                            sh """
                             docker run --rm lumir/remove-dockerhub-tag \
                             --user ${REG_USER} --password ${REG_PWD} \
                             ${config.dockerImageLabels}
                            """

                        }
                    }
                }
            }

        }
    }

}
