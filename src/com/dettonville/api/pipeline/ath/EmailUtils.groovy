package com.dettonville.api.pipeline.ath

class EmailUtils implements Serializable {
    private static final long serialVersionUID = 1L

    com.dettonville.api.pipeline.utils.logging.Logger log = new com.dettonville.api.pipeline.utils.logging.Logger(this)
    def dsl

//    String template="html.jelly"
    String template="html-with-health-and-console.jelly"
    boolean attachLog=true
    String body = '${JELLY_SCRIPT, template="' + this.template + '"}'

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    EmailUtils(def dsl) {
        this.dsl = dsl
    }

    /**
     * Send Email Notification
     **/
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

    /**
     * Sends an email to culprits, developers who made changes in the build, and the user who initiated the build.
     * @param currentBuild Refers to the currently running build
     * @param env Environment variables applicable to the currently running build
     *
     * ref: https://www.cloudbees.com/blog/sending-notifications-pipeline
     * ref: https://stackoverflow.com/questions/43473159/jenkins-pipeline-emailext-emailextrecipients-can-i-also-add-specific-individua
     */
    void sendEmailTestReport(Map config, String fileName=null, String reportName="Test", String postJobEventType, Map currentState=null) {

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

        if (!emailInfoMap.containsKey(postJobEventType)) {
            log.warn("**** unknown post event type = [${postJobEventType}]")
            return
        }

        String subjectPrefix = emailInfoMap[postJobEventType].subjectPrefix
        String emailListString = emailInfoMap[postJobEventType].list

        if (!emailListString || emailListString=="") {
            log.info("no notification subscription recipients found for event")
            return
        }

        log.info("emailListString=[${emailListString}]")

//        String buildStatus = currentBuild.result
        String buildStatus = "${dsl.currentBuild.result ? dsl.currentBuild.result : 'SUCCESS'}"
        Date start = new Date((Long)dsl.currentBuild.timeInMillis)
        String summaryFile = "summary.txt"

        String jenkinsMaster=dsl.env.BUILD_URL.split('/')[2].split(':')[0]
//        boolean isStageJenkins = (jenkinsMaster.equals("stage.cd.dettonville.int")) ? true : false

        String summary = ""
        if (dsl.fileExists(summaryFile)) {
            summary += "<li>${dsl.readFile(summaryFile).replaceAll("\n", "</li>\n<li>").replaceAll("<li></li>\n", "<br>\n")}</li>"
        } else {
            log.info("summaryFile [${summaryFile}] not found")
        }
        String prevResult=(dsl.currentBuild?.previousBuild) ? dsl.currentBuild.previousBuild.result : ""
        String styleColor = (dsl.currentBuild.result.equals("SUCCESS")) ? "green" : "red"

        String emailBodyReportLinks="<div><a href=\"${dsl.env.BUILD_URL}Report\">Build Report</a></div>"

        if (reportName) {
            emailBodyReportLinks+="<div><a href=\"${dsl.env.BUILD_URL}${reportName}\">${reportName}</a></div>"
        }

        emailBodyReportLinks+="""
          <p></p>
          <p>Thank you</p> 
          <p>Automation Test Team</p>
          <p></p>
            <br><hr><br>
            <p>
                <h1>${reportName} Summary</h1>
        
                <table>
                <tbody>
                <tr>
                    <td>
                        <ul>
            """

        if (config.getJobCause && config?.jobCause) {
            emailBodyReportLinks+="""
                    <li>Cause: ${config.jobCause}</li>
            """
        }

        emailBodyReportLinks+="""
                    <li>Result: ${dsl.currentBuild.result}</li>
                    <li>Previous Result: ${prevResult}</li>
                    <li>Build: #${dsl.currentBuild.number}</li>
                    <li>Started at: ${start}</li>
                    <li>Description: ${dsl.currentBuild.description}</li>
                    </ul>
               </td>
               <td>&nbsp;</td>
               <td style="background-color: ${styleColor};">${summary}</td>
            </tr>
            </tbody>
            </table>
        </p>
    
        """

        String emailBody = """
        <p>Greetings,</p> <div>The ${dsl.env.JOB_NAME.replaceAll('%2F', '/')} Test Automation execution has completed with the following status:</div> \
        """

        emailBody += """
            <div><span style=color:${styleColor};font-weight:bold;font-size:24pt>${buildStatus}</span></div> \
            """
        if (config?.testSuiteName) {
            emailBody += """
            <div><span style=color:${styleColor};font-weight:bold;font-size:16pt>Test Suite: ${config.testSuiteName}</span></div> \
            """
        }
        if (config?.browserPlatform) {
            emailBody += """
            <div><span style=color:${styleColor};font-weight:bold;font-size:16pt>Browser: ${config.browserPlatform}</span></div> \
            """
        }
        if (config?.checkIfDeployJobsRan && currentState) {

            if (currentState?.componentDeployJobSnapshots) {
                emailBody += getDeployJobConfigInfo(config, currentState, styleColor)
            }

            if (currentState?.deployJobsRanDuringTest) {
                emailBody += getDeployJobRunInfo(config, currentState, styleColor)
            }

        }

//        if (isStageJenkins) {
        if (config.isStageJenkins) {
            emailBody += """
              <p>To access the reports below you need to login to the <a href=https://wngdtlidmz.dettonville.int/connect>Test Firewall</a> and <a href=https://mctestpassword.corp.dettonville.org/>create your MC Test Password</a> if not already done.\
            """
        } else {
            emailBody += """
              <p>To access the reports below you need to login to the <a href=http://10.154.18.132/connect>STL NZ Prod Firewall</a>.\
            """
        }

        emailBody += """
            <p>Follow the report links below to view the respective report:</p> \
            """
        emailBody += """\
        ${emailBodyReportLinks}
        """

        log.info("sendEmailTestReport(): buildStatus=[${buildStatus}] subjectPrefix=[${subjectPrefix}] emailListString=[${emailListString}]")

//    List imageTypes = ["image/png", "image/jpeg", "image/gif"]
        List imageTypes = ["png", "jpg", "jpeg", "gif"]
        boolean isFileImage = false

        if (fileName) {
            emailBody += "<br><hr><br><b>${reportName} Report:</b><br>"
            if (dsl.fileExists(fileName)) {
//            String fileType = URLConnection.guessContentTypeFromName(fileName)
                String fileType = fileName.substring(fileName.lastIndexOf('.')+1, fileName.length())
                if (imageTypes.contains(fileType)) {
                    isFileImage = true
                    String baseUrl = "${dsl.env.BUILD_URL}${reportName}/"
                    emailBody += createScreenSnapRpt(baseUrl, fileName)
                } else {
                    emailBody += dsl.readFile(fileName)
                }
            } else {
                emailBody += "<br>report file to be attached [${fileName}] not found<br>"
            }
        }

        String emailSubj = "[${buildStatus}]"
        if (config?.appEnvironment) emailSubj += "[ENV: ${config.appEnvironment}]"
        emailSubj += "${dsl.env.JOB_NAME.replaceAll('%2F', '/')} Test Run on ${jenkinsMaster}"

        if (subjectPrefix) {
            emailSubj = "${subjectPrefix}: ${emailSubj}"
        }

        String recipients = ""
        if (config?.sendCDREmail) {
            recipients = dsl.emailextrecipients([
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
            ])
        }

        List emailList = (emailListString.contains(",")) ? emailListString.tokenize(',') : [emailListString]

        for (String recipient : emailList) {
//        log.info("Checking if recipient [${recipient}] found in list [${recipients}]")
            if (!recipients.toLowerCase().contains(recipient.toLowerCase())) {
//            log.info("recipient [${recipient}] not found in list [${recipients}], adding...")
                recipients += ", ${recipient}"
            }
        }

        // ref: https://stackoverflow.com/questions/43043287/using-jenkins-email-ext-plugin-with-pipeline
//    body: '<img src="data:image/png;base64,iVBORw0K...shortened...rkJggg==">',
//    emailext attachmentsPattern: '%JENKINS_HOME%/changelog.xml',
//    emailext body: {FILE,path=$fileName},
//    emailext body: "Find the Serenity Report snapshot attached",

        if (isFileImage && fileName && dsl.fileExists(fileName)) {
            dsl.emailext(body: emailBody,
                    attachmentsPattern: "**/${fileName}",
                    mimeType: 'text/html',
                    subject: emailSubj,
                    to: recipients,
                    from: config.emailFrom)
        } else {
            dsl.emailext(body: emailBody,
                    mimeType: 'text/html',
                    subject: emailSubj,
                    to: recipients,
                    from: config.emailFrom)
        }

    }

    String getDeployJobConfigInfo(Map config, Map currentState, String styleColor) {
        log.debug("started")

        Map deployJobInfo = currentState.componentDeployJobSnapshots.before

        String emailBody = ""
        if (config.hierarchicalDeployJobs) {
//            emailBody += "<br><div><span style=color:${styleColor};font-size:12pt>Component:Version:DeployBuild# (Cause) [Deployed Artifact Revision] Configuration under test:</span></div>\n"
            emailBody += "<br><div><span style=color:${styleColor};font-size:12pt>Component:Version:DeployBuild# [Deployed Artifact Revision] (Cause) Configuration under test:</span></div>\n"
        } else if (config.getLatestArtifactVersion) {
//            emailBody += "<br><div><span style=color:${styleColor};font-size:12pt>Component:Version:DeployBuild# (Cause) [Latest Artifact Revision] Configuration under test:</span></div>\n"
            emailBody += "<br><div><span style=color:${styleColor};font-size:12pt>Component:Version:DeployBuild# [Latest Artifact Revision] (Cause) Configuration under test:</span></div>\n"
        } else {
            emailBody += "<br><div><span style=color:${styleColor};font-size:12pt>Component:Version:DeployBuild# (Cause) Configuration under test:</span></div>\n"
        }

        deployJobInfo.each { String componentName, Map deployInfo ->
            log.info("componentName=${componentName}")

//            String componentBuildVersion = deployInfo.actions.findResult { it.parameters }.findResult { it.name==componentConfig.jobVersionParamName ? it.value : null }
            String componentBuildVersion = deployInfo.componentVersion

            String componentDeployUrl = deployInfo.url

            String componentBuildVersionAndCauseLink = getDeployVersionAndCauseLinks(config, deployInfo)

            emailBody += "<div style=\"padding-left: 30px;font-size:12pt\">"
            emailBody += componentBuildVersionAndCauseLink

//            String fileVersion = ""
//            String artifactUrl = ""
//            if (deployInfo?.deployResults) {
//                log.debug("deployInfo.deployResults=${JsonUtils.printToJsonString(deployInfo.deployResults)}")
//                fileVersion = deployInfo.deployResults.fileVersion
//                artifactUrl = deployInfo.deployResults.artifactUrl
//            } else if (config.getLatestArtifactVersion) {
//                log.debug("deployInfo.latestArtifactVersionInfo=${JsonUtils.printToJsonString(deployInfo.latestArtifactVersionInfo)}")
//                fileVersion = deployInfo.latestArtifactVersionInfo.fileVersion
//                artifactUrl = deployInfo.latestArtifactVersionInfo.artifactUrl
//            }
//            log.info("fileVersion=${fileVersion} artifactUrl=${artifactUrl}")
//            emailBody += " <a href=\"${artifactUrl}\">[${fileVersion}]</a>"
            emailBody += "</div>\n"
        }
        emailBody += "<br>\n"
        return emailBody
    }

    String getDeployVersionAndCauseLinks(Map config, Map buildResults) {
        log.debug("started")

        Map buildCause = buildResults.actions.findResult { it.causes }[0]

        String componentBuildVersion = buildResults.componentVersion
        String componentDeployUrl = buildResults.url
        String componentDeployBuildNumber = buildResults.number
        String componentName = buildResults.componentName

        String componentBuildVersionAndCauseLink = "<a href=\"${componentDeployUrl}\">${componentName}:${componentBuildVersion}:${componentDeployBuildNumber}</a>"

        String fileVersion = ""
        String artifactUrl = ""
        if (buildResults?.deployResults) {
            log.debug("buildResults.deployResults=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(buildResults.deployResults)}")
            fileVersion = buildResults.deployResults.fileVersion
            artifactUrl = buildResults.deployResults.artifactUrl
        } else if (config.getLatestArtifactVersion) {
            log.debug("buildResults.latestArtifactVersionInfo=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(buildResults.latestArtifactVersionInfo)}")
            fileVersion = buildResults.latestArtifactVersionInfo.fileVersion
            artifactUrl = buildResults.latestArtifactVersionInfo.artifactUrl
        }
        log.info("fileVersion=${fileVersion} artifactUrl=${artifactUrl}")
        componentBuildVersionAndCauseLink += " <a href=\"${artifactUrl}\">[${fileVersion}]</a>"

        if (buildCause?.upstreamProject) {
            String componentBuildNumber = buildCause.upstreamBuild
            String upstreamProject = buildCause.upstreamProject
            String upstreamUrl = buildCause.upstreamUrl

            String componentBuildCause = "Upstream: ${upstreamProject.split("/")[-2]} >> ${upstreamProject.split("/")[-1].replaceAll("%2F","/")} Build:${componentBuildNumber}"
            String componentBuildCauseUrl = "${dsl.env.HUDSON_URL}${upstreamUrl}${componentBuildNumber}"
            componentBuildVersionAndCauseLink += " <a href=\"${componentBuildCauseUrl}\">(${componentBuildCause})</a>"
        } else if (buildCause?.userName) {
            String componentBuildCause = "User: ${buildCause.userName}"
            componentBuildVersionAndCauseLink += " <a href=\"${componentDeployUrl}\">(${componentBuildCause})</a>"
//            componentBuildVersionAndCauseLink += " (${componentBuildCause})"
        }

        log.debug("componentBuildVersionAndCauseLink=${componentBuildVersionAndCauseLink}")
        return componentBuildVersionAndCauseLink
    }

    String getDeployJobRunInfo(Map config, Map currentState, String styleColor) {
        log.debug("started")

        String emailBody = ""
        emailBody += """
                <div><span style=color:red;font-weight:bold;font-size:14pt>Deploy Jobs were run during the test:</span></div> \
                """
        currentState.componentDeployJobDiffResults.each { Map component ->
            Map deployJobInfoBefore = currentState.componentDeployJobSnapshots.before[component.name]
            Map deployJobInfoAfter = currentState.componentDeployJobSnapshots.after[component.name]

            String componentName = component.name

            Map componentBuildCauseBefore = deployJobInfoBefore.actions.findResult { it.causes }[0]
            Map componentBuildCauseAfter = deployJobInfoAfter.actions.findResult { it.causes }[0]

            log.debug("componentBuildCauseBefore=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentBuildCauseBefore)}")
            log.debug("componentBuildCauseAfter=${com.dettonville.api.pipeline.utils.JsonUtils.printToJsonString(componentBuildCauseAfter)}")

//            String cvbLinkBefore = getDeployCauseLink(deployJobInfoBefore)
            String cvbLinkAfter = getDeployVersionAndCauseLinks(config, deployJobInfoAfter)

//            String fileVersionBefore = deployJobInfoBefore.deployResults.fileVersion
//            String artifactUrlBefore = deployJobInfoBefore.deployResults.artifactUrl
////            cvbLinkBefore += " <a href=\"${artifactUrlBefore}\">(${fileVersionBefore})</a>"
//
//            String fileVersionAfter = deployJobInfoBefore.deployResults.fileVersion
//            String artifactUrlAfter = deployJobInfoBefore.deployResults.artifactUrl
//            cvbLinkAfter += " <a href=\"${artifactUrlAfter}\">(${fileVersionAfter})</a>"

            emailBody += "<div style=\"padding-left: 30px;font-size:12pt\">"
            emailBody += "${componentName} deploy job was ran during test cycle:"
//            emailBody += "<br>At Test Start: ${cvbLinkBefore}"
//            emailBody += "<br>At Test End: ${cvbLinkAfter}"
            emailBody += "<br>At Test End: ${cvbLinkAfter}"
            emailBody += "</div>\n"
        }
        emailBody += "<br>\n"
        return emailBody
    }


// ref: https://github.com/buildit/jenkins-pipeline-libraries/blob/master/src/mavensettings.groovy
    String createScreenSnapRpt(baseUrl, snapFile) {
        return """
<html>
<head>
<title>Test Results</title>
</head>
<body>
<a href="${baseUrl}"><img src="cid:${snapFile}"></a>
</body>
</html>
"""
    }





}
