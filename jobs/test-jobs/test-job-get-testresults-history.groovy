#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import groovy.json.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

Map config=[:]
config.maxTestResultsHistory=3
config.testResultsHistory="aggregateTestResults.json"
config.emailList="ljohnson@dettonville.com"
config.emailFrom="DCAPI.TestAutomation@dettonville.com"


pipeline {

    agent {
        label "QA-LINUX || PROD-LINUX"
    }

    options {
        timestamps()
    }

    stages {
        stage("Run Test") {
            steps {
                script {
                    cleanWs()

                    log.info("Run Tests")
                    runTest(log)

                }
            }
        }
        stage("Aggregate Results") {
            steps {
                script {

                    log.info("getting prior build testResults")
                    updateAggregateTestResults(config)

                }
            }
        }
        stage("Archive Results") {
            steps {
                archiveArtifacts artifacts: '**'
            }
        }
    }
    post {
        always {
            sendEmailNotification(config.emailList as String, config)
        }
    }
}

String printToJsonString(Map mapVar) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(mapVar))
}

def runTest(Logger log) {
    String logPrefix="runTest():"


    try {

        getTestResultFiles(log)

        junit 'testdata/testResults/*.xml'
        log.info("Test Results submitted to junit plugin")

    } catch (Exception err) {
        log.error("exception: [${err}]")
    }

}


def getResourceFile(String fileName) {
    def file = libraryResource fileName
    // create a file with fileName
    writeFile file: "./${fileName}", text: file
}

def getTestResultFiles(Logger log) {
    String logPrefix = "${scriptName}->getTestResultFiles():"
    log.info("${logPrefix} starting")

    LinkedList statusList = ["good","bad"]

    // ref: https://stackoverflow.com/questions/27117193/random-integers-between-2-values
    int max = 1
    int min = 0
//    int idx = (new Random().nextInt(2))
    int idx = (new Random().nextInt(max-min+1)+min)
    log.debug("${logPrefix} idx=${idx}")

    String randomStatus = statusList.get(idx)
    log.debug("${logPrefix} randomStatus=${randomStatus}")

    sh "find . -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"

    LinkedList testResultFiles = ["testdata/testResults/testResults.${randomStatus}.json",
                                  "testdata/testResults/SERENITY-JUNIT.${randomStatus}.xml"]

    testResultFiles.each { String resultsFile ->
        String resultsFileFinal=resultsFile.replace(".bad.",".").replace(".good.",".")
        getResourceFile(resultsFile)
        if (resultsFileFinal!=resultsFile) {
            sh "mv ${resultsFile} ${resultsFileFinal}"
        }
    }

    log.info("${logPrefix} Test Result Files retrieved")
    sh "find . -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"

}

Map getTestResults(Integer buildNumber) {
    String logPrefix = "${scriptName}->getTestResults():"

    log.info("${logPrefix} starting")

    Map testResults = [:]

    String testResultFile = "testResults.${buildNumber}.json"
    String buildUrl = currentBuild.absoluteUrl.substring(0, currentBuild.absoluteUrl.lastIndexOf("/"))
    String testResultsUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
    testResultsUrl += "/${buildNumber}/testReport/api/json"
    log.info("${logPrefix} testResultsUrl=${testResultsUrl}")
    Integer responseStatus

    withCredentials([[$class          : 'UsernamePasswordMultiBinding', credentialsId: 'jenkins-rest-api-user',
                      usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

        String JENKINS_CREDS = "${JENKINS_USERNAME}:${JENKINS_TOKEN}"

        log.info("${logPrefix} Determine if test results exists")
        responseStatus=sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${testResultsUrl}", returnStdout: true).toInteger()

        log.info("${logPrefix} responseStatus: ${responseStatus}")
        if (responseStatus==200) {
            log.info("${logPrefix} prior test results exists, retrieving")

            sh "curl -sSL -u ${JENKINS_CREDS} ${testResultsUrl} 2>&1 | sed 's/null/\"\"/g' | tee ${testResultFile}"
            //    sh "curl -sSL -u ${JENKINS_CREDS} ${testResultsUrl} -o ${testResultFile}"

//            sh "find . -maxdepth 1 -name ${testResultFile} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"
            testResults = readJSON file: "${testResultFile}"
            //    log.info("${logPrefix} testResults=${testResults}")
            log.debug("${logPrefix} testResults=${printToJsonString(testResults)}")

        } else {
            log.warn("${logPrefix} prior test results not found at ${testResultsUrl}, returned responseStatus=${responseStatus}")
        }

    }
    testResults.buildNumber=buildNumber
    return testResults
}


Integer getJobArtifact(String artifactPath, Integer buildNumber=null) {
    String logPrefix = "${scriptName}->getJobArtifact():"

    log.info("${logPrefix} starting")

    Map testResults = [:]

    String buildUrl = currentBuild.absoluteUrl.substring(0, currentBuild.absoluteUrl.lastIndexOf("/"))
    String artifactUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
    if (buildNumber) {
        artifactUrl += "/${buildNumber}/artifact/${artifactPath}"
    } else {
        artifactUrl += "/lastCompletedBuild/artifact/${artifactPath}"
    }

    log.info("${logPrefix} artifactUrl=${artifactUrl}")

    Integer responseStatus

    withCredentials([[$class          : 'UsernamePasswordMultiBinding', credentialsId: 'jenkins-rest-api-user',
                      usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

        String JENKINS_CREDS = "${JENKINS_USERNAME}:${JENKINS_TOKEN}"

        log.info("${logPrefix} Determine if test results exists")
        responseStatus=sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${artifactUrl}", returnStdout: true).toInteger()

        log.info("${logPrefix} responseStatus: ${responseStatus}")
        if (responseStatus==200) {
            log.info("${logPrefix} artifact exists, retrieving")

            sh "curl -sSL -u ${JENKINS_CREDS} ${artifactUrl} -o ${artifactPath}"

            sh "find . -maxdepth 1 -name ${artifactPath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"

        } else {
            log.info("${logPrefix} artifact not found at ${artifactUrl}, returned responseStatus=${responseStatus}")
        }

    }

    return responseStatus
}

Map updateAggregateTestResults(Map config) {
    String logPrefix = "${scriptName}->updateAggregateTestResults():"
    log.info("${logPrefix} starting")

    String testResultsFile = config.testResultsHistory

    def priorBuildInfo = currentBuild.getPreviousBuild()
//    Integer priorBuildNumber = priorBuildInfo.number
    Integer buildNumber = currentBuild.number

    log.info("${logPrefix} get current test results")
    Map currentTestResults = getTestResults(buildNumber)
    log.debug("${logPrefix} currentTestResults=${printToJsonString(currentTestResults)}")

    log.info("${logPrefix} get prior results aggregate ${testResultsFile}")
    Map testResults
//    if (getJobArtifact(testResultsFile, priorBuildNumber)==200) {
    if (getJobArtifact(testResultsFile)==200) {
        log.info("${logPrefix} ${testResultsFile} retrieved")
        testResults = readJSON file: testResultsFile
        log.debug("${logPrefix} testResults=${printToJsonString(testResults)}")
    } else {
        log.info("${logPrefix} prior aggregate results does not exists, creating new one")
        testResults=[:]
        testResults.history=[]
    }

    log.info("${logPrefix} appending current test results to aggregate results")
    if (currentTestResults) {
        testResults.history.add(currentTestResults)
    }

    if (testResults.history.size()>config.maxTestResultsHistory) {
        int startIdx = testResults.history.size()-config.maxTestResultsHistory

        // the following worked until today (4/12/2019)
        //   testResults.history = testResults.history[startIdx..-1]
        //
        // now getting following error so using workaround:
        //
        //      Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods getAt java.util.List java.util.Collection.
        //      Administrators can decide whether to approve or reject this signature.
        //
        for (int i = 1; i  <= startIdx; i++) {
            testResults.history.remove(0)
        }
        log.info("${logPrefix} history reduced to last ${testResults.history.size()} results")
        log.debug("${logPrefix} truncated testResults=${printToJsonString(testResults)}")
    }

    def jsonOut = readJSON text: JsonOutput.toJson(testResults)
    writeJSON file: testResultsFile, json: jsonOut, pretty: 2

    log.info("${logPrefix} aggregate results saved to ${testResultsFile}")

    archiveArtifacts artifacts: testResultsFile
    log.info("${logPrefix} ${testResultsFile} archived")

    return testResults

}

/**
 * Send Email Notification
 **/
void sendEmailNotification(String emailDist, Map config) {

//    getResourceFile("email-templates/myTemplateFile.jelly")

    String recipients = ""

    // ref: https://github.com/jenkinsci/email-ext-plugin/tree/master/src/main/resources/hudson/plugins/emailext/templates
    // ref: https://wiki.jenkins.io/display/JENKINS/Email-ext+plugin
    // ref: https://groups.google.com/forum/#!topic/jenkinsci-users/Dodiik4el9A

//    String scriptType = "SCRIPT"
//    String templateName = "groovy-html.template"

    String scriptType = "JELLY_SCRIPT"
    String templateName = "html-with-health-and-console.jelly"

    if (config.SendCDREmail) {
        recipients = emailextrecipients([
                [$class: 'CulpritsRecipientProvider'],
                [$class: 'DevelopersRecipientProvider'],
                [$class: 'RequesterRecipientProvider']
        ])
    }

    List emailList = (emailDist.contains(",")) ? emailDist.tokenize(',') : [emailDist]

    for (String recipient : emailList) {
        log.debug("Checking if recipient [${recipient}] found in list [${recipients}]")
        if (!recipients.toLowerCase().contains(recipient.toLowerCase())) {
            log.debug("recipient [${recipient}] not found in list [${recipients}], adding...")
            recipients += ", ${recipient}"
        }
    }

    if (recipients) {
        emailext (
                mimeType: 'text/html',
                from: config.emailFrom,
                to: recipients,
                subject: "Job '${env.JOB_NAME.replaceAll('%2F', '/')}' (${currentBuild.displayName}) has finished with ${currentBuild.result ? currentBuild.result : "SUCCESS"}",
                body: '${JELLY_SCRIPT, template="html-with-health-and-console.jelly"}'
        )
//        body: '${JELLY_SCRIPT,template="${WORKSPACE}/email-templates/myTemplateFile.jelly"}'
//        body: '${SCRIPT, template="groovy-html.template"}',
//        body: '${JELLY_SCRIPT, template="html-with-health-and-console.jelly"}'
//        body: '${"${scriptType}", template="${templateName}}"'
    }

}
