import com.dettonville.api.pipeline.utils.JsonUtils

/**
 * Sends an email to culprits, developers who made changes in the build, and the user who initiated the build.
 * @param currentBuild Refers to the currently running build
 * @param env Environment variables applicable to the currently running build
 */
void call(def currentBuild,
        def env,
        List emailAdditionalDistList=[],
        List recipientProviders=[]) {
    String logPrefix="sendEmail():"

    List recipientProvidersDefault = [
        [$class: 'CulpritsRecipientProvider'],
        [$class: 'DevelopersRecipientProvider'],
        [$class: 'RequesterRecipientProvider']
    ]
//     List recipientProvidersDefault = [
//         [$class: 'CulpritsRecipientProvider'],
//         [$class: 'RequesterRecipientProvider']
//     ]

    echo("${logPrefix} recipientProviders=${JsonUtils.printToJsonString(recipientProviders)}")
    echo("${logPrefix} emailAdditionalDistList=${JsonUtils.printToJsonString(emailAdditionalDistList)}")

    emailDistList = []
    if (!emailAdditionalDistList.isEmpty()) {
        emailDistList.addAll(emailAdditionalDistList)
    }
    echo("${logPrefix} emailDistList=${JsonUtils.printToJsonString(emailDistList)}")

    recipientProvidersList = recipientProviders
    if (recipientProvidersList.isEmpty()) {
//         emailDistList = emailextrecipients(recipientProvidersDefault)
        recipientProvidersList = recipientProvidersDefault
    }

    String emailSubject = "Job '${env.JOB_NAME.replaceAll('%2F', '/')}' (${currentBuild.displayName}) has finished with ${currentBuild.result ? currentBuild.result : "SUCCESS"}"

    if (!emailDistList.isEmpty()) {
        def recipients = emailDistList.join(', ')
        echo("recipients=${recipients}")

        // ref: https://stackoverflow.com/questions/43473159/jenkins-pipeline-emailext-emailextrecipients-can-i-also-add-specific-individua
        emailext (
            mimeType: 'text/html',
            to: recipients,
            recipientProviders: recipientProvidersList,
            subject: emailSubject,
            body: '${SCRIPT, template="groovy-html.template"}'
        )
    } else {
        emailext (
            mimeType: 'text/html',
            recipientProviders: recipientProvidersList,
            subject: emailSubject,
            body: '${SCRIPT, template="groovy-html.template"}'
        )
    }
}
