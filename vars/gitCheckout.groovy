/**
 * Checks out code from a specific named branch. This is a preferred alternative to the default checkout which checks out by revision,
 * because our Maven Enforcer Plugin requires the branch name to do its checks.
 * @param scm Represents the SCM configuration in a multibranch project build
 */
void call(def scm) {

    checkout scm
}
