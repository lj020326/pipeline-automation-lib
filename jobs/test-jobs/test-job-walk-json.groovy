#!/usr/bin/env groovy

//import hudson.EnvVars
//import hudson.model.Environment
import java.io.*;
import groovy.util.IndentPrinter

//def jsonTxt = libraryResource 'environment.json'

def jsonTxt='''
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

//ByteArrayOutputStream baos = new ByteArrayOutputStream()
//PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos))
//IndentPrinter indentPrinter = new IndentPrinter(pw, "    ")
//Json2GroovyPrinter jsonPrinter = new Json2GroovyPrinter(indentPrinter)

Json2GroovyPrinter jsonPrinter = new Json2GroovyPrinter()

node ('DEVCLD-LIN7') {
    def envConfigs = readJSON text: "${jsonTxt}"

    echo "envConfigs.getClass().toString()=${envConfigs.getClass().toString()}"
    jsonPrinter.printJson(envConfigs)

}

//void printJson(def jsonObj) {
//
//
//    if (jsonObj.getClass() instanceof Map) {
//        printMap(jsonObj as Map)
//    } else if (jsonObj.getClass() instanceof List) {
//        printList(jsonObj as List)
//    } else if (jsonObj instanceof String) {
//        printString(jsonObj as String)
//    } else {
//        println(jsonObj as String)
//    }
////    if (json instanceof Map) {
////        printMap(json as Map)
////    } else if (json instanceof List) {
////        printList(json as List)
////    } else if (json instanceof String) {
////        printString(json as String)
////    } else {
////        indentPrinter.print(json as String)
////    }
//}
//
//void printString(String string) {
//    final String escaped = string
//            .replace('\'', '\\\'')
//            .replace('\$', '\\\$')
//    indentPrinter.print("'$escaped'")
//}
//
//void printList(List list) {
//
//    if(list.isEmpty()) {
//        indentPrinter.print('[]')
//    } else {
//        indentPrinter.println('[')
//        indentPrinter.incrementIndent()
//        list.eachWithIndex {
//            v, i ->
//                indentPrinter.printIndent()
//                printJson(v)
//                if (i != (list.size() - 1)) {
//                    indentPrinter.println(',')
//                } else {
//                    indentPrinter.println()
//                }
//        }
//        indentPrinter.decrementIndent()
//        indentPrinter.printIndent()
//        indentPrinter.print(']')
//    }
//}
//
//void printMap(Map map) {
//
//    if(map.isEmpty()) {
//        indentPrinter.print('[:]')
//    } else {
//        indentPrinter.println('[')
//        indentPrinter.incrementIndent()
//        map.eachWithIndex {
//            k, v, i ->
//                indentPrinter.printIndent()
//                indentPrinter.print(k)
//                indentPrinter.print(': ')
//                printJson(v)
//                if (i != (map.size() - 1)) {
//                    indentPrinter.println(',')
//                } else {
//                    indentPrinter.println()
//                }
//        }
//        indentPrinter.decrementIndent()
//        indentPrinter.printIndent()
//        indentPrinter.print(']')
//    }
//}


public class Json2GroovyPrinter {

    final IndentPrinter indentPrinter

    public Json2GroovyPrinter() {

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)
        IndentPrinter indentPrinter = new IndentPrinter(pw)

        this.indentPrinter = indentPrinter
    }

    public Json2GroovyPrinter(IndentPrinter indentPrinter) {

        this.indentPrinter = indentPrinter
    }

    public void printJson(def json) {
        if (json instanceof Map) {
            printMap(json as Map)
        } else if (json instanceof List) {
            printList(json as List)
        } else if (json instanceof String) {
            printString(json as String)
        } else {
            indentPrinter.print(json as String)
        }
    }

    public void printString(String string) {
        final String escaped = string
                .replace('\'', '\\\'')
                .replace('\$', '\\\$')
        indentPrinter.print("'$escaped'")
    }

    public void printList(List list) {

        if(list.isEmpty()) {
            indentPrinter.print('[]')
        } else {
            indentPrinter.println('[')
            indentPrinter.incrementIndent()
            list.eachWithIndex {
                v, i ->
                    indentPrinter.printIndent()
                    printJson(v)
                    if (i != (list.size() - 1)) {
                        indentPrinter.println(',')
                    } else {
                        indentPrinter.println()
                    }
            }
            indentPrinter.decrementIndent()
            indentPrinter.printIndent()
            indentPrinter.print(']')
        }
    }

    public void printMap(Map map) {

        if(map.isEmpty()) {
            indentPrinter.print('[:]')
        } else {
            indentPrinter.println('[')
            indentPrinter.incrementIndent()
            map.eachWithIndex {
                k, v, i ->
                    indentPrinter.printIndent()
                    indentPrinter.print(k)
                    indentPrinter.print(': ')
                    printJson(v)
                    if (i != (map.size() - 1)) {
                        indentPrinter.println(',')
                    } else {
                        indentPrinter.println()
                    }
            }
            indentPrinter.decrementIndent()
            indentPrinter.printIndent()
            indentPrinter.print(']')
        }
    }

}


///// old
//node ('DEVCLD-LIN7') {
//    def envConfigs = readJSON text: "${jsonTxt}"

//    printJsonObject(envConfigs)
//    printJson(envConfigs)

//
//}

//void printJsonObject(JSONObject jsonObj) {
//    jsonObj.each { key, value ->
//        //Print key and value
//        echo("key: "+ key + " value: " + value);
//
//        //for nested objects iteration if required
//        if (value instanceof JSONObject)
//            printJsonObject((JSONObject)value);
//    }
//}

//public class Json2GroovyPrinter {
//
//    final IndentPrinter indentPrinter
//
//    public Json2GroovyPrinter(IndentPrinter indentPrinter) {
//        this.indentPrinter = indentPrinter
//    }

//public void printJson(def json) {
//    if (json instanceof Map) {
//        printMap(json as Map)
//    } else if (json instanceof List) {
//        printList(json as List)
//    } else if (json instanceof String) {
//        printString(json as String)
//    } else {
//        indentPrinter.print(json as String)
//    }
//}
//
//public void printString(String string) {
//    final String escaped = string
//            .replace('\'', '\\\'')
//            .replace('\$', '\\\$')
//    indentPrinter.print("'$escaped'")
//}
//
//public void printList(List list) {
//
//    if(list.isEmpty()) {
//        indentPrinter.print('[]')
//    } else {
//        indentPrinter.println('[')
//        indentPrinter.incrementIndent()
//        list.eachWithIndex {
//            v, i ->
//                indentPrinter.printIndent()
//                printJson(v)
//                if (i != (list.size() - 1)) {
//                    indentPrinter.println(',')
//                } else {
//                    indentPrinter.println()
//                }
//        }
//        indentPrinter.decrementIndent()
//        indentPrinter.printIndent()
//        indentPrinter.print(']')
//    }
//}
//
//public void printMap(Map map) {
//
//    if(map.isEmpty()) {
//        indentPrinter.print('[:]')
//    } else {
//        indentPrinter.println('[')
//        indentPrinter.incrementIndent()
//        map.eachWithIndex {
//            k, v, i ->
//                indentPrinter.printIndent()
//                indentPrinter.print(k)
//                indentPrinter.print(': ')
//                printJson(v)
//                if (i != (map.size() - 1)) {
//                    indentPrinter.println(',')
//                } else {
//                    indentPrinter.println()
//                }
//        }
//        indentPrinter.decrementIndent()
//        indentPrinter.printIndent()
//        indentPrinter.print(']')
//    }
//}
//
////}
