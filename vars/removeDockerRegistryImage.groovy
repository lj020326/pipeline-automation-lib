#!/usr/bin/env groovy

// ref: https://devopsheaven.com/docker/dockerhub/2018/04/09/delete-docker-image-tag-dockerhub.html
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    List paramList = [
        string(defaultValue: "org/image_1:tag_1 org/image_2:tag_2 ...", description: "Specify docker registry image/label to remove", name: 'DockerImageLabels')
    ]

    properties([
            parameters(paramList)
    ])

    params.each { key, value ->
        key = Utilities.decapitalize(key)
        log.info("key=${key} value=${value}")
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

                            String auth='-u ${REG_USER}:${REG_PWD}'

                            List imageList = config.dockerImageLabels.split(" ")

                            imageList.each {String imageLabel ->
                                log.info("imageLabel=${imageLabel}")
                                registry = imageLabel.split("/")[0]
                                name = imageLabel.split("/")[1].split(":")[0]
                                tag = imageLabel.split("/")[1].split(":")[1]

                                log.info("registry=${registry} name=${name} tag=${tag}")

                                // ref: https://gist.github.com/jaytaylor/86d5efaddda926a25fa68c263830dac1#gistcomment-2662407
//                                sh """
//                                    curl ${auth} -X DELETE -sI -k "https://${registry}/v2/${name}/manifests/\$(\\
//curl ${auth} -sI -k \\
//-H "Accept: application/vnd.docker.distribution.manifest.v2+json" \\
//"https://${registry}/v2/${name}/manifests/${tag}" \\
//| tr -d '\r' | sed -En 's/^Docker-Content-Digest: (.*)/\1/pi'
//)"
//                                """


                                String getHashCmd = """
curl ${auth} -sI -k -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
https://${registry}/v2/${name}/manifests/${tag} | tr -d \'\r\' | sed -En \'s/^Docker-Content-Digest: (.*)/\\1/pi\'
                                """

                                String imageHash = sh(returnStdout: true, script: getHashCmd)

                                log.info("imageHash=${imageHash}")

                                sh "curl ${auth} -v -X DELETE -sI -k https://${registry}/v2/${name}/manifests/${imageHash}"

                            }

                        }
                    }
                }
            }

        }
    }

}
