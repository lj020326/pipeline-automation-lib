package com.dettonville.api.pipeline.utils


import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils

class ArtifactApiUtils implements Serializable {
    private static final long serialVersionUID = 1L

    com.dettonville.api.pipeline.utils.logging.Logger log = new com.dettonville.api.pipeline.utils.logging.Logger(this)
    def dsl

    String artifactoryApiCredId
    String artifactoryBaseUrl="https://artifacts.dettonville.int"

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    ArtifactApiUtils(def dsl, String artifactoryApiCredId="dcapi_ci_vcs_user") {
        this.dsl = dsl
        this.artifactoryBaseUrl = artifactoryBaseUrl
        this.artifactoryApiCredId = artifactoryApiCredId
    }

    void setArtifactoryApiCredId(String artifactoryApiCredId) {
        this.artifactoryApiCredId = artifactoryApiCredId
    }

    void setArtifactoryBaseUrl(String artifactoryBaseUrl) {
        this.artifactoryBaseUrl = artifactoryBaseUrl
    }

    // ref: https://stackoverflow.com/questions/36194316/how-to-get-the-build-user-in-artifactory-when-job-triggered-by-timer
    Map getLatestArtifactVersion(Map componentConfig) {
        String groupId = componentConfig.artifactGroupId
        String artifactId = componentConfig.artifactId
        String artifactVersion = componentConfig.componentVersion ?: componentConfig.version

        log.debug("starting")

        Map artifactVersonInfo = [:]
        artifactVersonInfo.componentVersion = artifactVersion

        log.debug("artifactVersonInfo.componentVersion=${artifactVersonInfo.componentVersion}")
        String repos = artifactVersion.contains('SNAPSHOT') ? 'snapshots' : 'releases'

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.artifactoryApiCredId,
                              usernameVariable: 'ARTIFACTORY_USERNAME', passwordVariable: 'ARTIFACTORY_TOKEN']]) {

            String ARTIFACTORY_CREDS = "${dsl.env.ARTIFACTORY_USERNAME}:${dsl.env.ARTIFACTORY_TOKEN}"

//            String classifierSearchUrl = "${this.artifactoryBaseUrl}/artifactory/api/search/latestVersion?g=${groupId}&a=${artifactId}&v=${artifactVersion}&repos=${repos}"
            String classifierSearchUrl = "${this.artifactoryBaseUrl}/artifactory/api/search/latestVersion?g=${groupId}&a=${artifactId}&repos=${repos}&v=${artifactVersion}"
            if (repos=="releases") {
                classifierSearchUrl += "*"
            }

            log.debug("Determine if artiface info exists")
            Integer responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${ARTIFACTORY_CREDS} '${classifierSearchUrl}'", returnStdout: true).toInteger()

            log.debug("responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("artifact info results not found for [${classifierSearchUrl}], returned responseStatus=${responseStatus}")
                return artifactVersonInfo
            }

            log.debug("artifact info exists, retrieving")

            artifactVersonInfo.fileVersion = dsl.sh(script: "curl -sSL -u ${ARTIFACTORY_CREDS} '${classifierSearchUrl}'", returnStdout: true)
            log.debug("artifactVersonInfo.fileVersion=${artifactVersonInfo.fileVersion}")

            String artifactSearchUrl = "${this.artifactoryBaseUrl}/artifactory/api/search/gavc?g=${groupId}&a=${artifactId}&v=${artifactVersion}&c=${artifactVersonInfo.fileVersion}&repos=${repos}"
            String artifactInfoJson = dsl.sh(script: "curl -sSL -u ${ARTIFACTORY_CREDS} '${artifactSearchUrl}'", returnStdout: true)

            log.debug("artifactInfoJson=${artifactInfoJson}")

            Map artifactInfoMap = dsl.readJSON text: artifactInfoJson

            log.debug("artifactInfoMap=${JsonUtils.printToJsonString(artifactInfoMap)}")

            artifactVersonInfo.artifactUrl = artifactInfoMap.results.findAll { it.uri.contains(".war") || it.uri.contains(".zip") }[0].uri
            log.debug("artifactVersonInfo.artifactUrl=${artifactVersonInfo.artifactUrl}")

            log.info("artifactVersonInfo=${JsonUtils.printToJsonString(artifactVersonInfo)}")
        }
        return artifactVersonInfo
    }


}
