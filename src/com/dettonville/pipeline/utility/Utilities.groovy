package com.dettonville.pipeline.utility

import groovy.json.JsonSlurperClassic


def parseJSON(String jsonString) {
  new JsonSlurperClassic().parseText(jsonString)
}

def getStringFromFile(String filename) {
  readFile filename
}


def notifyBuild(String buildStatus, String emailList) {

    def sendMail = false
    def lastBuildResult = currentBuild?.getPreviousBuild()?.getResult()
    buildStatus = buildStatus ?: 'SUCCESS'

    if(!lastBuildResult) {
        sendMail = true
    } else {
        if(!'SUCCESS'.equals(lastBuildResult)) {
          if('SUCCESS'.equals(buildStatus)) {
            buildStatus = 'FIXED'
            sendMail = true
          }
        }
    }

    if(!'SUCCESS'.equals(buildStatus)) sendMail = true

    if(sendMail) {
      def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
      def details = """${subject} (${env.BUILD_URL})

      STARTED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}]:

      Check console output at ${env.BUILD_URL}${env.JOB_NAME} [${env.BUILD_NUMBER}]"""
      def hostname = sh (returnStdout: true, script: 'hostname')
      def emailFrom = "${hostname.trim()}@dettonville.com"

      mail bcc: '', body: details, cc: '', from: emailFrom, replyTo: '', subject: subject, to: emailList
    }
}
