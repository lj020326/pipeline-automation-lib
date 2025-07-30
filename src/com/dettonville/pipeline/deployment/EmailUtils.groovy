package com.dettonville.pipeline.deployment

import com.dettonville.pipeline.utils.logging.Logger

class EmailUtils implements Serializable {
    private static final long serialVersionUID = 1L

    Logger log = new Logger(this)
//    DSL dsl
    def dsl

//    String template="html.jelly"
    String template="html-with-health-and-console.jelly"
    boolean attachLog=true
    String body = '${JELLY_SCRIPT, template="' + this.template + '"}'

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    EmailUtils(dsl) {
        this.dsl = dsl
    }

    /**
     * Send Email Notification
     **/
//    void sendEmailNotification(Map config, String emailListString) {
    void sendEmailNotification(Map config, String notifyAction) {

        if (!config) {
            log.error("**** config not found to derive email recipients")
            return
        }
        Map emailInfoMap = [
                "success": [ list: config.successEmailList, subjectPrefix: "SUCCEEDED"],
                "failure": [ list: config.failedEmailList, subjectPrefix: "FAILED"],
                "aborted": [ list: config.abortedEmailList, subjectPrefix: "ABORTED"],
                "changed": [ list: config.changedEmailList, subjectPrefix: "CHANGED"],
                "always": [ list: config.alwaysEmailList, subjectPrefix: null]
        ]

        if (!emailInfoMap.containsKey(notifyAction)) {
            log.warn("**** unknown post notify result = [${notifyAction}]")
            return
        }

        String jobNotifyResult = emailInfoMap[notifyAction].subjectPrefix
        String emailListString = emailInfoMap[notifyAction].list

        if (!emailListString || emailListString=="") {
            log.debug("no notification subscription recipients found for event, finished here")
            return
        }

        log.info("emailListString=[${emailListString}]")

        String recipients = ""

        // ref: https://github.com/jenkinsci/email-ext-plugin/tree/master/src/main/resources/hudson/plugins/emailext/templates
        // ref: https://wiki.jenkins.io/display/JENKINS/Email-ext+plugin
        // ref: https://groups.google.com/forum/#!topic/jenkinsci-users/Dodiik4el9A

        if (config.sendCDREmail) {
            recipients = emailextrecipients([
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
            ])
        }

        List emailList = (emailListString.contains(",")) ? emailListString.tokenize(',') : [emailListString]

        for (String recipient : emailList) {
            log.debug("Checking if recipient [${recipient}] found in list [${recipients}]")
            if (!recipients.toLowerCase().contains(recipient.toLowerCase())) {
                log.debug("recipient [${recipient}] not found in list [${recipients}], adding...")
                recipients += ", ${recipient}"
            }
        }

        String subject = "Job '${dsl.env.JOB_NAME.replaceAll('%2F', '/')}' (${dsl.currentBuild.displayName}) has finished with ${dsl.currentBuild.result ? dsl.currentBuild.result : "SUCCESS"}"

        if (jobNotifyResult) {
            subject = "[${jobNotifyResult}] ${subject}"
        }

        if (recipients) {
            dsl.emailext (
                    mimeType: 'text/html',
                    from: config.emailFrom,
                    to: recipients,
                    subject: subject,
                    body: this.body,
                    attachLog: this.attachLog
            )
        }

    }


}
