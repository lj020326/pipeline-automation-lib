/**
 * Pause the pipeline and prompt the user to confirm whether or not a deployment should happen
 */
void call() {

    final String branch = env.BRANCH_NAME
    final String branchType = (branch.contains('/') ? branch.substring(0, branch.indexOf('/')) : branch).toUpperCase()

    def timeoutValue = env."RELEASE_USER_INPUT_TIMEOUT_${branchType}" ?: env.RELEASE_USER_INPUT_TIMEOUT_DEFAULT

    if (!timeoutValue) {
        error 'No value set for RELEASE_USER_INPUT_TIMEOUT_DEFAULT in environment variables'
    }

    try {
        timeout(time: timeoutValue as Integer, unit: 'SECONDS') {
            env.RELEASE = input parameters: [
                    [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: "Tick here to deploy"]]
        }
    } catch(err) {
        env.RELEASE = false
        if (branch.contains('release/') || branch.contains('master')) {
            error "Timeout reached on ${branch}. This WILL cause the pipeline to fail: ${err}"
        } else {
            echo "Timeout reached. This will not cause the pipeline to fail on this branch: ${branch}."
        }
    }

    if (!Boolean.valueOf(env.RELEASE as String) && (branch.contains('release/') || branch.contains('master'))) {
        error "User chose not to deploy on ${branch}. This WILL cause the pipeline to fail."
    }

    echo "Releasing? : ${env.RELEASE}"
}
