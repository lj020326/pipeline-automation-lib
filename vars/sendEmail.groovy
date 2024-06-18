/**
 * Sends an email to culprits, developers who made changes in the build, and the user who initiated the build.
 * @param currentBuild Refers to the currently running build
 * @param env Environment variables applicable to the currently running build
 */
void call(def currentBuild, def env, List emailDistList=[], List emailAdditionalDistList=[]) {

    List emailDistListDefault = [
        [$class: 'CulpritsRecipientProvider'],
        [$class: 'DevelopersRecipientProvider'],
        [$class: 'RequesterRecipientProvider']
    ]

    if (emailDistList.isEmpty()) {
        emailDistList=emailDistListDefault
    }

    def recipients = emailextrecipients(emailDistList)

    if (!emailAdditionalDistList.isEmpty()) {
        for (String recipient : emailList) {
            if (!recipients.toLowerCase().contains(recipient.toLowerCase())) {
                recipients += ", ${recipient}"
            }
        }
    }

    if (recipients) {
        emailext (
                mimeType: 'text/html',
                to: recipients,
                subject: "Job '${env.JOB_NAME.replaceAll('%2F', '/')}' (${currentBuild.displayName}) has finished with ${currentBuild.result ? currentBuild.result : "SUCCESS"}",
                body: '${SCRIPT, template="groovy-html.template"}',
        )
    }
}
