#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.CredentialParser
import net.sf.json.JSON

String jsonStr='''
{
  "name": "Stage",
  "properties": {
    "sbxLocationUrl1": "https://stage.api.dettonville.org/atms/v1/atm?PageOffset=0&Longitude=-99.2036859&City=usa&Latitude=19.4421888&PageLength=10&Format=xml",
    "sbxLocationUrl2": "https://stage.api.dettonville.org/atms/v1/country?Format=xml",
    "sbxLocationUrl3": "https://stage.api.dettonville.org/atms/v1/countrysubdivision??Country=CAN&Format=xml",
    "locationUrl1": "https://stage.api.dettonville.org/atms/v1/atm?PageOffset=0&Longitude=-99.2036859&City=usa&Latitude=19.4421888&PageLength=10&Format=xml",
    "locationUrl2": "https://stage.api.dettonville.org/atms/v1/country?Format=xml",
    "locationUrl3": "https://stage.api.dettonville.org/atms/v1/countrysubdivision??Country=CAN&Format=xml",
    "QA Sandbox URL": "https://stage.api.dettonville.org/atms/v1/atm?PageOffset=0&Longitude=-99.2036859&City=usa&Latitude=19.4421888&PageLength=10&Format=xml",
    "QA Service": "Locations",
    "Change Email Subject": "[Stage] Dettonville Developers Email Address Change",
    "byPassCaptcha": "test_no_human_intervention",
    "byPassCaptchaValue": "true",
    "expectedResponse": "401 - Failed",
    "MD5_PRIVATE_KEY": "$password.MD5_PRIVATE_KEY"
  },
  "components": [
    {
      "name": "developer.dettonville.org",
      "pageFolder" : "developerPages",
      "baseUrl" : "https://stage.developer.dettonville.org",
      "defaultTimeout": "60",
      "properties": {
        "waitForHumanCaptcha": "60",
        "reactpicklist": "true"
      },
      "roles": [
        {
          "name": "Anonymous User",
          "properties": {

          }
        },
        {
          "name": "Anonymous Developer1",
          "properties": {
            "emailAddress": "testdcapi.${random}@dcapi.mailinator.com",
            "userName": "autoDcApi${random}",
            "password": "testDcApi${random}",
            "profile.firstName": "DCAPI",
            "profile.lastName": "TestAutomation",
            "profile.companyName": "Dettonville",
            "profile.country": "INDIA",
            "profile.streetAddress": "817",
            "profile.city": "Jersey City",
            "profile.state": "NJ",
            "profile.postalCode": "07306",
            "profile.phoneNumber": "8192471248941",
            "update.profile.firstName": "Test",
            "update.profile.lastName": "Automation",
            "update.profile.companyName": "Companyname",
            "update.profile.country": "UNITED STATES",
            "update.profile.streetAddress": "718",
            "update.profile.city": "Brooklyn",
            "update.profile.state": "NY",
            "update.profile.postalCode": "11214",
            "update.profile.phoneNumber": "7181234567"
          }
        },
        {
          "name": "Registered User",
          "properties": {
            "emailAddress": "gregorycoburn@yahoo.com",
            "password": "$password.STAGE_USER_PWD1"
          }
        },
        {
          "name": "Registered User2",
          "properties": {
            "emailAddress": "sun326+stageusermigration02@gmail.com",
            "password": "$password.STAGE_USER_PWD2"
          }
        },
        {
          "name": "Registered User3",
          "properties": {
            "userName": "Akashvaniqrqwr",
            "emailAddress": "akash.vangani@dcapi.mailinator.com",
            "tmpEmailAddress": "test007@dcapi.mailinator.com",
            "password": "$password.STAGE_USER_PWD3"
          }
          },
        {
          "name": "Registered User4",
          "properties": {
            "emailAddress": "musheev@dcapi.mailinator.com",
            "userName": "musheev",
            "password": "$password.STAGE_USER_PWD4",
            "securityAnswer": "test"
          }
        },
        {
          "name": "Registered User5",
          "properties": {
            "emailAddress": "tester@dcapi.mailinator.com",
            "password": "$password.STAGE_USER_PWD5"
          }
        }
      ]
    },
    {
      "name": "Admin Tool",
      "pageFolder" : "adminToolPages",
      "baseUrl" : "https://stage2.dcapiadmin.dettonville.org/dcapiadmin/DCAPIAdmin.html",
      "defaultTimeout": "22",
      "properties": {
      	"reactpicklist": ""
      },
      "roles": [
        {
          "name": "Admin User",
          "properties": {
            "userName": "dcapi_stage_test_adm",
            "pin": "$password.stage_admin_pin"
          }
        }
      ]
    },
    {
      "name": "CMS",
      "pageFolder" : "cmsPages",
      "baseUrl" : "https://stage.iapp.dettonville.int/devzone/cms/",
      "defaultTimeout": "22",
      "properties": {
      },
      "roles": [
        {
          "name": "Admin User",
          "properties": {
            "userName": "dcapi_stage_test_adm",
            "pin": "$password.stage_admin_pin"
          }
        }
      ]
    }
  ]
}
'''


//Logger.init(this, LogLevel.INFO)
Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

//log.info("jsonTxt.getClass()= ${jsonTxt.getClass()}")

log.info("parsing credentials")

node ('QA-LINUX || PROD-LINUX') {
    CredentialParser credentialParser = new CredentialParser(this)

//    List secretVars = credentialParser.getCredentialId(jsonStr)

    JSON envConfigs = readJSON(text: jsonStr)
    List secretVars = credentialParser.getCredentialIdListFromJson(envConfigs)
    log.info("secretVars=${secretVars}")

//    secretVars=[@string(credentialsId=MD5_PRIVATE_KEY,variable=MD5_PRIVATE_KEY),
//                @string(credentialsId=STAGE_USER_PWD1,variable=STAGE_USER_PWD1),
//                @string(credentialsId=STAGE_USER_PWD2,variable=STAGE_USER_PWD2),
//                @string(credentialsId=STAGE_USER_PWD3,variable=STAGE_USER_PWD3),
//                @string(credentialsId=STAGE_USER_PWD4,variable=STAGE_USER_PWD4),
//                @string(credentialsId=STAGE_USER_PWD5,variable=STAGE_USER_PWD5),
//                @string(credentialsId=stage_admin_pin,variable=stage_admin_pin)]

    withCredentials(secretVars) {
        log.setLevel(LogLevel.DEBUG)
        log.debug("**********************")
        log.debug("pipeline env variables:")
        sh('printenv | sort')
        log.debug("**********************")

    }
}

