/**
 * Tag a commit in Git
 *
 * @param tag The name of the new tag
 */
void call(String tag, def env = null) {

    if (env?.SKIP_GIT_TAGGING == true) {
        sh 'echo "Skipping Git tagging. To enable this step, set this job\'s environment variable SKIP_GIT_TAGGING to false (or delete the variable)."'
        return
    }

    final String gitRepoUrl = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
    final String gitRepoHostname = gitRepoUrl.replace('https://', '')

    withCredentials([usernamePassword(credentialsId: 'dcapi_ci_vcs_user', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh """
            git tag -a "${tag}" -m "${tag}"
            git push "https://${GIT_USERNAME}:${GIT_PASSWORD}@${gitRepoHostname}" "${tag}"
        """
    }
}
