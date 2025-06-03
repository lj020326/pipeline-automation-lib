/**
 * Stop and remove a running Docker container.
 * Note: this must be called from a Jenkins agent with Docker on the path.
 * @param containerName The name of the container to stop and remove
 */
void call(String containerName) {

    final String logPrefix = "[${containerName}]:" as String

    try {
        sh "docker stop ${containerName}"
    } catch (Exception err) {
        echo "${logPrefix} exception occurred [${err}]"
    }

    try {
        sh "docker rm ${containerName}"
    } catch (Exception err) {
        echo "${logPrefix} exception occurred [${err}]"
    }
}
