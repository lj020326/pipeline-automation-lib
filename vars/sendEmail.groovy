import com.dettonville.api.pipeline.utils.JsonUtils

/**
 * Sends an email to culprits, developers who made changes in the build, and the user who initiated the build.
 * @param currentBuild Refers to the currently running build
 * @param env Environment variables applicable to the currently running build
 *
 * NOTE: this function supports mixing named and positional parameters per following method
 * ref: https://docs.groovy-lang.org/latest/html/documentation/#_mixing_named_and_positional_parameters
 */
void call(Map args=[:], java.lang.Object currentBuild, java.lang.Object env) {
    String logPrefix="sendEmail():"

    // ref: https://stackoverflow.com/a/46512017/2791368
    List emailAdditionalDistList = args.get('emailAdditionalDistList', [])
    echo("${logPrefix} emailAdditionalDistList=${JsonUtils.printToJsonString(emailAdditionalDistList)}")

    List recipientProvidersDefault = [
        [$class: 'CulpritsRecipientProvider'],
        [$class: 'DevelopersRecipientProvider'],
        [$class: 'RequesterRecipientProvider']
    ]
//     List recipientProvidersDefault = [
//         [$class: 'CulpritsRecipientProvider'],
//         [$class: 'RequesterRecipientProvider']
//     ]

    List recipientProviders = args.get('recipientProviders', recipientProvidersDefault)

    echo("${logPrefix} recipientProviders=${JsonUtils.printToJsonString(recipientProviders)}")

    String emailSubject = "Job '${env.JOB_NAME.replaceAll('%2F', '/')}' (${currentBuild.displayName}) has finished with ${currentBuild.result ? currentBuild.result : "SUCCESS"}"
    String recipients = emailAdditionalDistList.join(', ')
    echo("${logPrefix} recipients=${recipients}")

    emailext (
        mimeType: 'text/html',
        recipientProviders: recipientProviders,
        to: recipients,
        subject: emailSubject,
        body: '${SCRIPT, template="groovy-html.template"}'
    )

}
