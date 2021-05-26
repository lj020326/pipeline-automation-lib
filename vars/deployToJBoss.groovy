/**
 * Deploy WAR file to JBoss
 * @param targetServer The hostname or IP address of the JBoss server
 * @param warName The name of the WAR file to be deployed
 * @param port The JBoss management interface port
 * @param isDevServer Determines which cacerts file to use
 */
void call(def targetServer, def warName, def port, boolean isDevServer = true) {

    String certificatesPath = "/apps_data_01/security/keystores/cacerts-${isDevServer ? "dev" : "stage"}/cacerts"

    try {
        timeout(time: 30, unit: 'SECONDS') {
            env.RELEASE_DEV = input parameters: [
                    [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: "Tick here to deploy to ${targetServer}"]]
        }
    } catch(err) {
        def user = err.getCauses()[0].getUser()
        echo "Timeout reached, or input false - initiated by: ${user}"
    }

    if ((Boolean)(env.RELEASE_DEV) == true) {
        echo "Going to release"
        sh """
            mvn -e \
                -Djavax.net.ssl.trustStore="${certificatesPath}" \
                jboss-as:deploy-only \
                -Djboss-as.hostname="${targetServer}" \
                -Djboss-as.port="${port}" \
                -Djboss-as.deployment.filename="${warName}" \
                -Ddeploy.force=true
        """
    } else {
        echo "Not going to release"
    }
}