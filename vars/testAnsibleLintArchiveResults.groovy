#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.versioning.ComparableSemanticVersion

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)

    pipeline {
        agent {
            label "ansible"
        }
        tools {
           ansible "ansible-venv"
        }
        options {
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30'))
            skipDefaultCheckout(config.skipDefaultCheckout)
            timeout(time: config.timeout, unit: config.timeoutUnit)
        }
        stages {
            stage('ansible-lint test') {
                steps {
                    script {
                        sh "mkdir -p ${config.testResultsDir}"

                        testResultJunitXml = libraryResource config.testCaseXmlFile
                        writeFile(
                            file: "${config.testResultsDir}/${config.testResultsJunitFile}",
                            text: testResultJunitXml
                        )

                        sh("tree ${config.testResultsDir}")

//                             sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")
                        sh("head -20 ${config.testResultsDir}/${config.testResultsJunitFile}")
                        echo "..."
                        sh("tail -20 ${config.testResultsDir}/${config.testResultsJunitFile}")

                        String sedCmd = "sed -i 's/<testsuites>/<testsuites name=\"ansible-lint test\">/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                        sedCmd += "&& sed -i 's/<testsuite errors=.* failures=.* \\(.*\\)\\/>/<testcase name=\"no linting errors found\"\\/>/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                        sedCmd += "&& sed -i 's/<testcase name=\"\\(.*\\)-\\([0-9]\\+\\)\">/<testcase name=\"\\1-\\2\" classname=\"\\1\">/' ${config.testResultsDir}/${config.testResultsJunitFile}"
                        sh(sedCmd)

//                             sh("cat ${config.testResultsDir}/${config.testResultsJunitFile}")
                        sh("head -20 ${config.testResultsDir}/${config.testResultsJunitFile}")
                        echo "..."
                        sh("tail -20 ${config.testResultsDir}/${config.testResultsJunitFile}")

                        archiveArtifacts(
                            allowEmptyArchive: true,
                            artifacts: "${config.testResultsDir}/**",
                            fingerprint: true)

                        junit(testResults: "${config.testResultsDir}/${config.testResultsJunitFile}",
                              skipPublishingChecks: true,
                              allowEmptyResults: true)
                    }
                }
            }
        }
    }

} // body

//@NonCPS
Map loadPipelineConfig(Map params) {
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
        key=Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }

    config.jenkinsNodeLabel = config.get('jenkinsNodeLabel',"ansible")
    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    config.timeout = config.get('timeout', 3)
    config.timeoutUnit = config.get('timeoutUnit', 'HOURS')
    config.skipDefaultCheckout = config.get('skipDefaultCheckout', false)

//    config.emailDist = config.emailDist ?: "lee.james.johnson@gmail.com"
    config.emailDist = config.get('emailDist',"lee.james.johnson@gmail.com")
    config.deployEmailDistList = [
        'lee.johnson@dettonville.com',
        'lee.james.johnson@gmail.com'
    ]
    config.alwaysEmailDistList = ["lee.johnson@dettonville.com"]

    config.emailFrom = config.emailFrom ?: "admin+ansible@dettonville.com"

//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-01.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-02.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-03.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-04.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-05.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-06.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-07.xml')
//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-08.xml')
//      config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-09.xml')
//      config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-10.xml')

//     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-50.xml')
//      config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-51.xml')
     config.testCaseXmlFile = config.get('testCaseXmlFile', 'testdata/test-results/ansible-lint/ansible-lint-junit.tc-99.xml')

    config.testResultsDir = config.get('testResultsDir', 'test-results')
    config.testResultsJunitFile = config.get('testResultsJunitFile', 'ansible-lint-junit.xml')

//     config.lintConfigFile = config.get('lintConfigFile', ".ansible-lint")

    config.get("gitRemoteBuildKey", 'Ansible Lint Tests')
	config.get("gitRemoteBuildName", 'Ansible Lint Tests')
    config.get("gitRemoteBuildSummary", "${config.gitRemoteBuildName} update")

    log.debug("params=${params}")
    log.debug("config=${JsonUtils.printToJsonString(config)}")

    return config
}
