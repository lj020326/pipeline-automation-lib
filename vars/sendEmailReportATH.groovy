/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2018 dettonville.org DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * Sends an email to culprits, developers who made changes in the build, and the user who initiated the build.
 * @param currentBuild Refers to the currently running build
 * @param env Environment variables applicable to the currently running build
 */

// ref: https://www.cloudbees.com/blog/sending-notifications-pipeline
// ref: https://stackoverflow.com/questions/43473159/jenkins-pipeline-emailext-emailextrecipients-can-i-also-add-specific-individua
void call(String emailFrom, String emailDist, def currentBuild, String fileName, String reportName="Test") {
    String buildStatus = currentBuild.result
    Date start = new Date((Long)currentBuild.timeInMillis)
    String duration = "${currentBuild.durationString.replace(' and counting', '')}"

    String jenkinsMaster=env.BUILD_URL.split('/')[2].split(':')[0]
    boolean isStageJenkins = (jenkinsMaster.equals("stage.cd.dettonville.int")) ? true : false

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
        <ul>
            <li>Result: ${currentBuild.result}</li>
            <li>Previous Result: ${prevResult}</li>
            <li>Build: #${currentBuild.number}</li>
            <li>Started at: ${start}</li>
            <li>Duration: ${duration} seconds</li>
            <li>Description: ${currentBuild.description}</li>
    </p>

"""

    GString emailBody = """
        <p>Greetings,</p> <div>The ${JOB_NAME.replaceAll('%2F', '/')} Test Automation execution has been completed.</div> \
        """

    if (isStageJenkins) {
        emailBody += """
              <p>To access the report you need to login into the Test Firewall and create your MC Test Password if not already done:\
              <p> <div><a href=https://wngdtlidmz.dettonville.int/connect>Test Firewall</a><div> <div><a href=https://mctestpassword.corp.dettonville.org/>MC Test Password</a><div> \
            """
    } else {
//        emailBody += """
//              <p>To access the report you need to login into the STL OCC Prod Firewall:\
//              <p> <div><a href=https://10.154.18.50/connect/PortalMain>STL OCC Firewall</a><div> \
//            """
        emailBody += """
              <p>To access the report you need to login into the STL NZ Prod Firewall:\
              <p> <div><a href=http://10.154.18.132/connect>STL NZ Firewall</a><div> \
            """
    }

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

    emailBody += """\
        ${emailBodyReportLinks}
        """

    // ref: https://www.cloudbees.com/blog/sending-notifications-pipeline

//    echo "config=$[config}"

//    def emailBody=""

    echo "sendEmailReport(): buildStatus=[${buildStatus}]"

    emailBody += "<br><hr><br>${reportName} Report:<br>"
    emailBody += readFile fileName

    build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
    emailSubj = "[${build_status}]${JOB_NAME.replaceAll('%2F', '/')} - Build # ${BUILD_NUMBER} on ${jenkinsMaster}"

    String recipients = ""
    recipients = emailextrecipients([
            [$class: 'CulpritsRecipientProvider'],
            [$class: 'DevelopersRecipientProvider'],
            [$class: 'RequesterRecipientProvider']
    ])

    List emailList = (emailDist.contains(",")) ? emailDist.tokenize(',') : [emailDist]

    for (String recipient : emailList) {
        if (!recipients.toLowerCase().contains(recipient.toLowerCase())) {
            recipients += ", ${recipient}"
        }
    }

    // always send to repo DL
    mail(to: recipients,
            from: emailFrom,
            mimeType: 'text/html',
            subject: emailSubj,
            body: emailBody
    )

}

