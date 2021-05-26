package com.dettonville.api.pipeline.utils

import com.dettonville.api.pipeline.utils.logging.Logger

class DockerUtil implements Serializable {
    private static final long serialVersionUID = 1L

    Logger log = new Logger(this)
    def dsl

    String dockerImageId
    String dockerRegUrl
    String dockerRegEndpoint
//    String dockerRegCredId

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
//    DockerUtil(dsl, String dockerRegUrl, String dockerRegCredId="docker-registry-user") {
    DockerUtil(dsl) {
        this.dsl = dsl
    }

    // ref: https://stackoverflow.com/questions/20098739/match-base-url-regex
    // ref; https://groovyconsole.appspot.com/script/1109001
    // ref: https://coderwall.com/p/utgplg/regex-full-url-base-url
    String getUrlEndpoint(String urlStr) {
        String logPrefix = "getUrlEndpoint():"
        String endpoint = urlStr.find(/^[a-z][a-z0-9+\-.]*:\/\/([a-z0-9\-._~%!$&'()*+,;=]+@)?([a-z0-9\-._~%]+|\[[a-z0-9\-._~%!$&'()*+,;=:]+\]):(\d+)/) { it[2] + ':' + it[3] }
        dsl.echo("endpoint = " + endpoint)
        return endpoint
    }

    def withRegistry(String dockerRegUrl, String dockerRegCredId="docker-registry-user", def actions) {

        String logPrefix = "withRegistry():"
        this.dockerRegUrl = dockerRegUrl
        this.dockerRegEndpoint = getUrlEndpoint(dockerRegUrl)

        dsl.withCredentials([[$class          : 'UsernamePasswordMultiBinding', credentialsId: dockerRegCredId,
                              usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD']]) {

            String loginCmd = """
                docker -v
                docker logout ${dockerRegUrl}
                docker login --username ${dsl.env.DOCKER_USERNAME} --password ${dsl.env.DOCKER_PASSWORD} ${dockerRegUrl}
            """
            dsl.sh "${loginCmd}"

            actions()

        }
    }

    def build(String dockerImageId, String dockerImageBuildPath=".") {
        String logPrefix = "build(${dockerImageId}):"

        log.debug("${logPrefix} starting")

        this.dockerImageId = dockerImageId
        String dockerCmd = "docker build -t ${dockerImageId} ${dockerImageBuildPath}"

        dsl.sh(script: "${dockerCmd}")
//        int retstat = dsl.sh(script: "${dockerCmd}", returnStatus: true)
//        boolean result = (retstat) ? false : true

        return this
    }

    boolean push(String dockerImageTag) {
        String logPrefix = "push(${dockerImageTag}):"
        log.debug("${logPrefix} starting")

        String targetImageLabel = "${this.dockerRegEndpoint}/${this.dockerImageId}:${dockerImageTag}"

        String dockerCmd = """
            docker tag ${this.dockerImageId} ${targetImageLabel}
            docker push ${targetImageLabel}
        """

        dsl.sh(script: "${dockerCmd}")

        return this
    }

}
