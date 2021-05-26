/**
 * Stop and remove a running Docker container.
 * Note: this must be called from a Jenkins agent with Docker on the path.
 * @param containerName The name of the container to stop and remove
 */
void call(String cleanAction, String containerName, String imageName=null) {

    final String logPrefix = "cleanupDocker(${containerName})" as String

    try {
        sh "docker stop ${containerName}"
    } catch (Exception err) {
        echo "${logPrefix}: exception occurred [${err}]"
    }

    try {
        sh "docker rm ${containerName}"
    } catch (Exception err) {
        echo "${logPrefix}: exception occurred [${err}]"
    }
    if (cleanAction=="image") {
        try {
            sh "docker rmi ${imageName}"
        } catch (Exception err) {
            echo "${logPrefix}: exception occurred [${err}]"
        }
    }
}
