/**
 * Initiate a deployment via ARA
 * @param envSpecFile path to the environment spec file in Biz Ops Git repo. Contains environment instance specific information.
 * @param releaseSpecFile path to the release spec file in Biz Ops Git repo. Contains release specific version and component information.
 * @param workflow The name of the release workflow to execute.
 * @param version The version number of the application to be deployed.
 */
void call(String envSpecFile, String releaseSpecFile, String workflow, String version) {

    echo "Downloading ARA CLI"
    releaseCliUrl = env.ARA_CLI
    sh "/usr/bin/curl '${releaseCliUrl}' | tar -x"
    if (!fileExists('release.sh')) {
        error "Release CLI script not found.  Please ensure that the script exists at ${releaseCliUrl}"
    }

    String environmentSpec
    String releaseSpec

    echo "Cloning ARA spec files from DFS Biz Ops repository"
    dir('ara-spec-files') {
        checkout scm: [
                $class           : 'GitSCM',
                branches         : [[name: 'main']],
                userRemoteConfigs: [[credentialsId: 'dcapi_ci_vcs_user', url: 'https://gitrepository.dettonville.int/stash/scm/dfsbizops/ara_spec_files.git']]
        ]

        environmentSpec = readFile envSpecFile
        releaseSpec = readFile releaseSpecFile
        echo "Replacing application version in the release spec with ${version}"
        releaseSpec = releaseSpec.replaceAll("(?m)^.*\"version\".*\$", "\"version\": \"${version}\", ")
    }

    try {
        withCredentials([[$class: 'UsernamePasswordMultiBinding',
                          credentialsId   : 'dfs-automation-ara-cred',
                          usernameVariable: 'ARA_USER',
                          passwordVariable: 'ARA_PASSWORD']]) {

            if (releaseSpec != "") {
                releaseParams = """{
                      \"env_spec\": ${environmentSpec},
                      \"rel_spec\": ${releaseSpec}
                  }"""

                echo "Initiating release"
                sh """./release.sh \
                  --api https://dev-stage.techorch.dettonville.int \
                  --action '${workflow}' \
                  --params '${releaseParams}'"""
            }
        }
    } catch (err) {
        error "The deployment failed: ${err}"
    }
}