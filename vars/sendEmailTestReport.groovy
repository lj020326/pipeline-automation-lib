/**
 * Sends an email to culprits, developers who made changes in the build, and the user who initiated the build.
 * @param currentBuild Refers to the currently running build
 * @param env Environment variables applicable to the currently running build
 */

// ref: https://www.cloudbees.com/blog/sending-notifications-pipeline
// ref: https://stackoverflow.com/questions/43473159/jenkins-pipeline-emailext-emailextrecipients-can-i-also-add-specific-individua
void call(Map config, String emailDist, def currentBuild, String fileName=null, String reportName="Test", String notifyAction=null, Map currentState=null) {
    String buildStatus = currentBuild.result
    Date start = new Date((Long)currentBuild.timeInMillis)
    String duration = "${currentBuild.durationString.replace(' and counting', '')}"
    String summaryFile = "summary.txt"

    String jenkinsMaster=env.BUILD_URL.split('/')[2].split(':')[0]
    boolean isStageJenkins = (jenkinsMaster.equals("stage.cd.dettonville.int")) ? true : false

    String summary = ""
    if (fileExists(summaryFile)) {
        summary += "<li>${readFile(summaryFile).replaceAll("\n", "</li>\n<li>").replaceAll("<li></li>\n", "<br>\n")}</li>"
    } else {
        echo "summaryFile [${summaryFile}] not found"
    }
    String prevResult=(currentBuild?.previousBuild) ? currentBuild.previousBuild.result : ""
    String styleColor = (buildStatus.equals("SUCCESS")) ? "green" : "red"

    GString emailBodyReportLinks="""
          <div><a href="${BUILD_URL}Report">Build Report</a></div> \
        """

    if (reportName) {
        emailBodyReportLinks+="""
              <div><a href="${BUILD_URL}${reportName}">${reportName}</a></div> \
            """
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
                <li>Result: ${currentBuild.result}</li>
                <li>Previous Result: ${prevResult}</li>
                <li>Build: #${currentBuild.number}</li>
                <li>Started at: ${start}</li>
                <li>Description: ${currentBuild.description}</li>
                </ul>
           </td>
           <td>&nbsp;</td>
           <td style="background-color: ${styleColor};">${summary}</td>
        </tr>
        </tbody>
        </table>
    </p>

"""

    GString emailBody = """
        <p>Greetings,</p> <div>The ${JOB_NAME.replaceAll('%2F', '/')} Test Automation execution has been completed.</div> \
        """

    emailBody += """
            <p>Please follow the reports</p> <div><span style=color:${styleColor};font-weight:bold;font-size:24pt>${buildStatus}</span></div> \
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
            emailBody += getDeployJobConfigInfo(currentState, styleColor)
        }

        if (currentState?.deployJobsRanDuringTest) {
            emailBody += getDeployJobRunInfo(currentState, styleColor)
        }

    }

    emailBody += "<br><br>${emailBodyReportLinks}"

    // ref: https://www.cloudbees.com/blog/sending-notifications-pipeline

//    echo "config=$[config}"

//    def emailBody=""

    echo "sendEmailTestReport(): buildStatus=[${buildStatus}] notifyAction=[${notifyAction}] emailDist=[${emailDist}]"

//    List imageTypes = ["image/png", "image/jpeg", "image/gif"]
    List imageTypes = ["png", "jpg", "jpeg", "gif"]
    boolean isFileImage = false

    if (fileName) {
        emailBody += "<br><hr><br><b>${reportName} Report:</b><br>"
        if (fileExists(fileName)) {
//            String fileType = URLConnection.guessContentTypeFromName(fileName)
            String fileType = fileName.substring(fileName.lastIndexOf('.')+1, fileName.length())
            if (imageTypes.contains(fileType)) {
                isFileImage = true
                String baseUrl = "${BUILD_URL}${reportName}/"
                emailBody += createScreenSnapRpt(baseUrl, fileName)
            } else {
                emailBody += readFile fileName
            }
        } else {
            emailBody += "<br>report file to be attached [${fileName}] not found<br>"
        }
    }

    build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
//    emailSubj = "[${build_status}][ENV: ${config.appEnvironment}]${JOB_NAME} - Build # ${BUILD_NUMBER} on ${jenkinsMaster}"
//    emailSubj = "[${build_status}][ENV: ${config.appEnvironment}]${JOB_NAME} Test Run on ${jenkinsMaster}"
    emailSubj = "[${build_status}]"
    if (config?.appEnvironment) emailSubj += "[ENV: ${config.appEnvironment}]"
    emailSubj += "${JOB_NAME.replaceAll('%2F', '/')} Test Run on ${jenkinsMaster}"

    if (notifyAction) {
        emailSubj = "${notifyAction}: ${emailSubj}"
    }

    String recipients = ""
    if (config.sendCDREmail) {
        recipients = emailextrecipients([
                [$class: 'CulpritsRecipientProvider'],
                [$class: 'DevelopersRecipientProvider'],
                [$class: 'RequesterRecipientProvider']
        ])
    }

    List emailList = (emailDist.contains(",")) ? emailDist.tokenize(',') : [emailDist]

    for (String recipient : emailList) {
//        echo "Checking if recipient [${recipient}] found in list [${recipients}]"
        if (!recipients.toLowerCase().contains(recipient.toLowerCase())) {
//            echo "recipient [${recipient}] not found in list [${recipients}], adding..."
            recipients += ", ${recipient}"
        }
    }

    // ref: https://stackoverflow.com/questions/43043287/using-jenkins-email-ext-plugin-with-pipeline
//    body: '<img src="data:image/png;base64,iVBORw0K...shortened...rkJggg==">',
//    emailext attachmentsPattern: '%JENKINS_HOME%/changelog.xml',
//    emailext body: {FILE,path=$fileName},
//    emailext body: "Find the Serenity Report snapshot attached",

    if (isFileImage && fileName && fileExists(fileName)) {
        emailext body: emailBody,
                attachmentsPattern: "**/${fileName}",
                mimeType: 'text/html',
                subject: emailSubj,
                to: recipients,
                from: config.emailFrom
    } else {
        emailext body: emailBody,
                mimeType: 'text/html',
                subject: emailSubj,
                to: recipients,
                from: config.emailFrom
    }

}

String getDeployJobConfigInfo(Map currentState, String styleColor) {

    Map deployJobInfo = currentState.componentDeployJobSnapshots.before

    String emailBody = ""
    emailBody += "<br><div><span style=color:${styleColor};font-weight:bold;font-size:14pt>Component-Version-Build Configuration under test:</span></div>\n"

//    emailBody += "<div style=display:block;margin-left: 70px>"
    deployJobInfo.each { String componentName, Map deployInfo ->
        String componentBuildNumber = deployInfo.actions[0].causes[0].upstreamBuild
        String componentBuildVersion = deployInfo.actions[2].parameters[0].value
        String componentBuildDeployUrl = deployInfo.url

        emailBody += "<div style=\"padding-left: 30px;color:${styleColor};font-weight:bold;font-size:12pt\">"
//            emailBody += "<span style=display:block;margin-left: 40px;color:${styleColor};font-weight:bold;font-size:12pt>"
        emailBody += "<a href=\"${componentBuildDeployUrl}\">${componentName}-${componentBuildVersion}-${componentBuildNumber}</a>"
        emailBody += "</div>\n"
    }
    emailBody += "<br>\n"
    return emailBody
}

String getDeployJobRunInfo(Map currentState, String styleColor) {

    String emailBody = ""
    emailBody += """
                <div><span style=color:${styleColor};font-weight:bold;font-size:14pt>Deploy Jobs were run during the test:</span></div> \
                """
    currentState.componentDeployJobDiffResults.each { Map component ->
        Map componentBuildDiffs = component.diffs
        String componentName = component.name
        Integer buildNumberBefore = component.buildNumberBefore
        Integer buildNumberAfter = component.buildNumberBefore

        String componentBuildNumberBefore = componentBuildDiffs["root.actions[0].causes[0].upstreamBuild"].value1
        String componentBuildNumberAfter = componentBuildDiffs["root.actions[0].causes[0].upstreamBuild"].value2

        String componentBuildVersionBefore = componentBuildDiffs["root.actions[2].parameters[0].value"].value1
        String componentBuildVersionAfter = componentBuildDiffs["root.actions[2].parameters[0].value"].value2

        String componentBuildDeployUrlBefore = componentBuildDiffs["root.url"].value1
        String componentBuildDeployUrlAfter = componentBuildDiffs["root.url"].value2

        String cvbBefore = "${componentName}-${componentBuildVersionBefore}-${componentBuildNumberBefore}"
        String cvbAfter = "${componentName}-${componentBuildVersionAfter}-${componentBuildNumberAfter}"

        emailBody += "<div style=\"padding-left: 30px;color:${styleColor};font-weight:bold;font-size:12pt\">"
        emailBody += "${componentName} deploy job was ran during test cycle:\n"
        emailBody += "<a href=\"${componentBuildDeployUrlBefore}\">${cvbBefore}</a> at test start and "
        emailBody += "<a href=\"${componentBuildDeployUrlAfter}\">job ${cvbAfter}</a>ran during the test cycle"
        emailBody += "</div>\n"
    }
    emailBody += "<br>\n"
    return emailBody
}

// ref: https://github.com/buildit/jenkins-pipeline-libraries/blob/master/src/mavensettings.groovy
def createScreenSnapRpt(baseUrl, snapFile) {
    """
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

