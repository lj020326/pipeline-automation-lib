import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.JsonUtils

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

void call(Map config, String status) {
    String recipientList = ""
    if (status == "always") recipientList = config.alwaysEmailList
    if (status == "changed") recipientList = config.changedEmailList
    if (status in ["aborted", "failed"]) recipientList = config.failedEmailList
    if (status == "success") recipientList = config.successEmailList

    if (!recipientList) {
        log.debug("No recipients defined for email trigger condition: ${status}")
        return
    }
    log.info("recipientList=${recipientList}")

    String emailSubject = "Jenkins Build Notification [${status.toUpperCase()}]: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    String emailBody = "The pipeline execution task finished with status '${status.toUpperCase()}'. Check details at ${env.BUILD_URL}"

    log.info("Sending status notification tracking info to: ${recipientList}")
    emailext subject: emailSubject, body: emailBody, to: recipientList
}
