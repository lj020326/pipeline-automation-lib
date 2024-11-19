#!/usr/bin/env groovy

/*-
 *
 * For info on how to configure pipeline - see here:
 * https://fusion.dettonville.int/confluence/display/MAPI/How+to+use+the+Acceptance+Test+Harness+Pipeline
 *
 * OR here:
 * ref: https://fusion.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
 *
 * OR here:
 * ref: https://gitrepository.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
 *
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2024 dettonville.org DevOps
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
 *
*/

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
//import groovy.json.*

import com.dettonville.api.pipeline.conntest.ConnectivitySummary
import com.dettonville.api.pipeline.conntest.SiteTestResults
import com.dettonville.api.pipeline.conntest.SiteUtils
import java.text.SimpleDateFormat

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

// https://support.cloudbees.com/hc/en-us/articles/217309497-Test-a-SSL-connection-from-Jenkins
def call(Map params=[:]) {

//    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this, LogLevel.INFO)

    log.info("Loading Default Configs")
    Map config=loadPipelineConfig(params)

    ConnectivitySummary connectivitySummary

    pipeline {

        agent { label "QA-LINUX || PROD-LINUX" }

        tools {
            maven 'M3'
        }

        options {
            timestamps()
            disableConcurrentBuilds()
            timeout(time: 4, unit: 'HOURS') //Should not take longer than 2 hours to run
        }

        stages {

            stage("Setup") {
                steps {
                    deleteDir()
                    script {
                        git config.gitRepoUrl
                        if (config.useSimulationMode) {
                            figlet "SIMULATION MODE"

                            log.info('SIMULATION MODE - following command not run')
                            log.info("mvn clean ${config.mvnLogOptions} compile")
                        } else {
                            sh "mvn clean ${config.mvnLogOptions} compile"
                        }

                        testConnTelnet = libraryResource 'testConnTelnet.sh'
                        writeFile file: 'testConnTelnet.sh', text: testConnTelnet

                        stash name: 'connectivity-check'
                    }
                }
            }
            stage("Run Connectivity Tests") {
                steps {
                    script {
                        log.info("Running tests")
                        connectivitySummary = runTests(config)
                    }
                }
            }

            stage("Summary") {
                steps {
                    script {

                        String duration = currentBuild.durationString.replace(' and counting', '')
                        currentBuild.description = "Test Duration: ${duration}<br>Node Count: ${config.nodeList.size()}<br>Tests Count: ${config.testList.size()}"
//                        Map resultMap = connectivitySummary.createJsonReport(config)
                        Map resultMap = connectivitySummary.createJsonReport()

                        dir('ConnectivitySummary') {
                            try {
                                log.debug("transforming results data to json and writing")
                                writeFile encoding: 'UTF-8', file: 'index.json', text: printToJsonString(resultMap)
                            } catch (Exception err) {
                                log.error("writeFile(): exception occurred [${err}]")
                            }

                            log.debug("getting report resources")
                            getResourceFile("conntest.zip.b64")
                            sh "base64 -d conntest.zip.b64 > conntest.zip"
                            sh "unzip -o conntest.zip"

                            log.debug("creating report")
                            connectivitySummary.createSummaryHtml()
                        }

                        sh 'find ConnectivitySummary/*.html -type f'

//                        archiveArtifacts artifacts: "ConnectivitySummary/*.html"
                        archiveArtifacts artifacts: "ConnectivitySummary/**"

                        publishHTML([allowMissing         : true,
                                     alwaysLinkToLastBuild: true,
                                     keepAll              : true,
                                     reportDir            : 'ConnectivitySummary',
                                     reportFiles          : "index.html",
                                     reportName           : config.reportName])

                        currentBuild.result=connectivitySummary.result

                    } // script

                }

            }

        } // stages
        post {
            changed {
                script {
                    sendEmailTestReport(config, config.emailDist, currentBuild, 'ConnectivitySummary/emailable.index.html', config.reportName, "CHANGED")
                }
            }
            always {

                script {
                    // HOUSE KEEPING
                    String duration = currentBuild.durationString.replace(' and counting', '')
                    currentBuild.description = "Test Duration: ${duration}<br>Node Count: ${config.nodeList.size()}<br>Tests Count: ${config.testList.size()}"

                    sendEmailTestReport(config, config.alwaysEmailDist, currentBuild, 'ConnectivitySummary/emailable.index.html', config.reportName)
                }
            }
        } // post

    } // pipeline

} // call


def getResourceFile(String fileName) {
    def file = libraryResource fileName
    // create a file with fileName
    writeFile file: "./${fileName}", text: file
}

String printToJsonString(Map config) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(config))
}

// ref: https://stackoverflow.com/questions/4052840/most-efficient-way-to-make-the-first-character-of-a-string-lower-case
//@NonCPS
String decapitalize(String string) {
    if (string == null || string.length() == 0) {
        return string;
    }
    return string.substring(0, 1).toLowerCase() + string.substring(1);
}

Map loadPipelineConfig(Map params, String configFile=null) {
    String logPrefix="loadPipelineConfig():"
    Map config = [:]

    // handle config yml
    if (params?.yml) {
//        Map configSettings = parseUtil.parseYaml(this,params.yml)
        Map ymlConfig = readYaml text: params.yml
        log.info("${logPrefix} ymlConfig=${printToJsonString(ymlConfig)}")
        config=config + ymlConfig.pipeline
    } else if (configFile == null) {
        config.globalConfigFile = params.globalConfigFile ?: "connectivity-check-defaults.yml"
        // set default pipeline config settings for unspecified params
//        def configDefaultsTxt = libraryResource 'connectivity-check-defaults.yml'
        def configDefaultsTxt = libraryResource config.globalConfigFile
        def configSettings = readYaml text: configDefaultsTxt
        config=configSettings.pipeline + config
    } else {
        def configSettings = readYaml file: "${configFile}"
//        config=configSettings.pipeline
        config=configSettings.pipeline + config
    }

    // copy immutable params maps to mutable config map
    params.each { key, value ->
//        log.debug("params[${key}]=${value}")
        key=decapitalize(key)
        config[key]=value
    }

    config.logLevel = config.get('logLevel', "INFO")
    config.debugPipeline = config.get('debugPipeline', false)
    log.info("params=${params}")

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    } else {
        log.setLevel(config.logLevel)
    }

    // ref: https://stackoverflow.com/questions/21638697/disable-maven-download-progress-indication
    // ref: https://stackoverflow.com/questions/17979685/disable-maven-execution-debug-output
    // ref: https://books.sonatype.com/mvnref-book/reference/running-sect-options.html
    config.mvnLogOptions=config.get('mvnLogOptions', "-B -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn")
//    config.mvnLogOptions=config.get('mvnLogOptions', "-B -Dorg.slf4j.simpleLogger.defaultLogLevel=warn")

    config.debugMvn=config.get('debugMvn', false)
    if (config.debugMvn) {
        config.mvnLogOptions="-B -X"
    }

    //
    // essential/minimal params
    //
    log.info("env.JOB_NAME = ${env.JOB_NAME}")
    // set default states
    config.useSimulationMode = config.get('useSimulationMode', false)
    config.nodeList = config.nodeList ?: env.AGENTS.split(',').sort()
    config.timeout = config.timeout ?: 10
    config.runcount = config.runcount ?: 1
    config.runParallelNodeSiteTests = config.get('runParallelNodeSiteTests', true)
//    config.runParallelNodeSiteTests = config.get('runParallelNodeSiteTests', false)
//    config.runParallelTests = config.get('runParallelTests', false)
    config.runParallelTests = config.get('runParallelTests', true)

//    config.emailDist = config.emailDist ?: "lee.johnson@dettonville.com"
    config.emailDist = config.get('emailDist',"lee.johnson@dettonville.com")
    config.alwaysEmailDist = config.alwaysEmailDist ?: "lee.johnson@dettonville.com"
    config.emailFrom = config.emailFrom ?: "DCAPI.TestAutomation@dettonville.com"

    config.sendEmail = config.sendEmail ?: false
    config.sendCDREmail = config.sendCDREmail ?: false
    config.sendInlineReport = config.sendInlineReport ?: true
    config.reportName = "ConnectivityReport"

    log.info("${logPrefix} config=${printToJsonString(config)}")

    return config
}

ConnectivitySummary runTests(Map config) {
    String logPrefix="runTests():"
    if (config.debugPipeline) log.debug("runTests(config=${config}): started")
    log.debug("${logPrefix} started")

    Map nodeTests = [:]
    ConnectivitySummary connectivitySummary = new ConnectivitySummary(this, config)

    config.nodeList.eachWithIndex { Map it, i ->

        log.debug("${logPrefix} it=${printToJsonString(it)}")

        // set group to default and overlay any group settings
        Map nodeConfig = config.findAll { !["nodeList","networks"].contains(it.key) } + it
        nodeConfig.siteList = []
        if (nodeConfig?.testNetwork) {
            List siteList = getSiteList(config, nodeConfig.testNetwork)
            nodeConfig.siteList.addAll(siteList)
        }
        if (nodeConfig?.testNetworks) {
            nodeConfig.testNetworks.eachWithIndex { String nodeNetwork, i2 ->
                List siteList = getSiteList(config, nodeNetwork)
                nodeConfig.siteList.addAll(siteList)
            }
        }
        log.debug("${logPrefix} nodeConfig.siteList=${nodeConfig.siteList}")

        nodeConfig.nodeOrder=i

        if (nodeConfig.debugPipeline) {
            log.debug("${logPrefix} nodeConfig=${printToJsonString(nodeConfig)}")
            log.debug("${logPrefix} nodeConfig.siteList=${nodeConfig.siteList}")
        }

        nodeTests["split-${nodeConfig.nodeLabel}"] = {
            node(nodeConfig.nodeLabel) {
                script {
                    deleteDir()
                    step([$class: 'WsCleanup'])

//                    nodeConfig.nodeLabelOrig = nodeConfig.nodeLabel
//                    nodeConfig.nodeLabel = "${env.NODE_NAME} (${nodeConfig.nodeLabel}}"
                    nodeConfig.nodeName = env.NODE_NAME
                    log.debug("${logPrefix} env.NODE_NAME=${env.NODE_NAME}")

                    unstash name: 'connectivity-check'

                    log.debug("${logPrefix} nodeConfig=${printToJsonString(nodeConfig)}")
                    List siteResults = runNodeConnTest(nodeConfig)
                    connectivitySummary.addSiteTestResultsList(siteResults)

                    if (nodeConfig.debugPipeline) {
                        sh 'find . -maxdepth 1 -iname \\*.log -type f -printf "%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p\\n"'
                        sh 'find logs -printf "%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p\\n" | sort -k 3,3'
                    }
//                    sh "mkdir -p logs/${nodeConfig.nodeName}; mv ./\\*.log logs/${nodeConfig.nodeName}"
                    sh "mkdir -p logs/${nodeConfig.nodeName}"
                    sh "mv *.log logs/${nodeConfig.nodeName}"

                    if (nodeConfig.debugPipeline) sh "find logs/${nodeConfig.nodeName} -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p\\n\" | sort -k 3,3"

                    archiveArtifacts artifacts: "logs/${nodeConfig.nodeName}/*.log"

                    log.debug("**** ${logPrefix} Finished tests: nodeLabel=${nodeConfig.nodeLabel} nodeName=${nodeConfig.nodeName} nodeOrder=${nodeConfig.nodeOrder} *****")
                }
            }
        }
    }

    log.debug("nodeTests.size()=${nodeTests.size()}, nodeTests=${nodeTests}")
    parallel nodeTests

    return connectivitySummary
}

List getSiteList(Map config, network) {
    String logPrefix = "getSiteList():"
    log.info("${logPrefix} network=${network}")
    Map networkConfig = config.networks[network]
    List siteList = networkConfig.siteList
    List retSiteList = []
    siteList.each { it ->
        // add network config settings to siteConfig
        Map siteConfig = networkConfig.findAll { it.key != 'siteList' } + it
        if (!siteConfig?.network) siteConfig.network = network

        log.debug("${logPrefix} siteConfig=${siteConfig}")
        retSiteList.add(siteConfig)
    }
    log.debug("${logPrefix} siteList=${siteList}")
//    return siteList
    return retSiteList
}

List runNodeConnTest(Map nodeConfig) {
    if (nodeConfig.debugPipeline) log.debug("runNodeConnTestParallel(nodeConfig=${nodeConfig}): started")
    String logPrefix="runNodeConnTest():"
    log.debug("${logPrefix} started")

    List resultList = []
    Map siteTests = [:]

    log.debug("${logPrefix} nodeConfig=${printToJsonString(nodeConfig)}")

    nodeConfig.siteList.eachWithIndex { Map it, i ->

        log.debug("${logPrefix} it=${printToJsonString(it)}")

        // set group to default and overlay any group settings
        Map siteConfig = nodeConfig.findAll { it.key != 'siteList' } + it
        siteConfig.siteOrder=i

        String endpoint = siteConfig.endpoint
        Map hostInfo=SiteUtils.getHostInfo(endpoint)
        siteConfig.scheme=hostInfo.scheme
        siteConfig.host=hostInfo.host
        siteConfig.port=hostInfo.port
        siteConfig.context=hostInfo.context
        siteConfig.targetUrl=hostInfo.targetUrl

        log.info("${logPrefix} hostInfo=${hostInfo}")

        if (nodeConfig.runParallelNodeTests) {
            siteTests["split-${siteConfig.nodeLabel}->${siteConfig.endpoint}"] = {
                log.info("${logPrefix} starting tests: nodeLabel=${siteConfig.nodeLabel} nodeName=${siteConfig.nodeName} endpoint=${siteConfig.endpoint} network=${siteConfig.network} *****")
                SiteTestResults siteResults = runTestList(siteConfig)
                resultList.add(siteResults)
                log.debug("**** ${logPrefix} Finished tests: node=${siteConfig.nodeLabel} endpoint=${siteConfig.endpoint} siteOrder=${siteConfig.siteOrder} *****")
            }
        } else {
            log.info("${logPrefix} starting tests: nodeLabel=${siteConfig.nodeLabel} nodeName=${siteConfig.nodeName} endpoint=${siteConfig.endpoint} network=${siteConfig.network} *****")
            SiteTestResults siteResults = runTestList(siteConfig)
            resultList.add(siteResults)
            log.debug("**** ${logPrefix} Finished tests: node=${siteConfig.nodeLabel} endpoint=${siteConfig.endpoint} siteOrder=${siteConfig.siteOrder} *****")
        }

    }

    if (nodeConfig.runParallelNodeTests) {
        parallel siteTests
    }

    return resultList
}

SiteTestResults runTestList(Map siteConfig) {

    if (siteConfig.debugPipeline) log.debug("runTestListParallel(siteConfig=${siteConfig}): started")
    String logPrefix="runTestList(nodeLabel=${siteConfig.nodeLabel}):"
    log.debug("${logPrefix} started")

    Map testRuns = [:]
    log.debug("${logPrefix} siteTestResults = new SiteTestResults()")
    SiteTestResults siteTestResults = new SiteTestResults(siteConfig)

    log.debug("${logPrefix} iterate over siteConfig")
    siteConfig.testList.eachWithIndex { Map it, i ->

        log.info("${logPrefix} it=${printToJsonString(it)}")

        // set group to default and overlay any group settings
        Map testConfig = siteConfig.findAll { it.key != 'testList' } + it
        testConfig.testOrder=i

        if (siteConfig.runParallelTests) {
            testRuns["split-${testConfig.nodeLabel}->${testConfig.host}-${i}-${testConfig.command}"] = {
                Map returnStatus = runTestCommand(testConfig)
                log.info("${logPrefix} returnStatus.result=${returnStatus.result}")
                log.debug("${logPrefix} returnStatus=${returnStatus}")
                siteTestResults.addStep(returnStatus)
            }
        } else {
            Map returnStatus = runTestCommand(testConfig)
            log.info("${logPrefix} returnStatus.result=${returnStatus.result}")
            log.debug("${logPrefix} returnStatus=${returnStatus}")
            siteTestResults.addStep(returnStatus)
        }
    }

    if (siteConfig.runParallelTests) {
        parallel testRuns
    }
    return siteTestResults
}

def getYamlInt(Map config, String key) {
    def value
    if (config.containsKey(key)) {
        try {
            value = config[key].toInteger()
        } catch (Exception err) {
            value = config[key]
        }
    }
    return value
}

Map runTestCommand(Map testConfig) {

    if (testConfig.debugPipeline) log.debug("runTestCommand(testConfig=${testConfig}): started")

    String command = testConfig.command
    String timeout = testConfig.timeout

    String logPrefix="runTestCommand(${command}):"
    log.debug("${logPrefix} started")

    testConfig.runcount = getYamlInt(testConfig, "runcount")

    log.info("${logPrefix} testConfig.runcount=${testConfig.runcount}")

    testConfig.testCase=testConfig.testCase ?: command
    testConfig.testId="${command}-${testConfig.host}"
//    testConfig.runLabel="${command}-${endpoint}".replace(' ', '-').replace(':', '_')
    testConfig.runLabel="${command}-${testConfig.host}-${testConfig.port}".replace(' ', '-').replace(':', '_')
    if (testConfig?.httpProxyHost) {
        testConfig.runLabel += "-viaproxy-${testConfig.httpProxyHost}-${testConfig.httpProxyPort}"
    }

    testConfig.filename = "cmd-${testConfig.runLabel}.log"

    switch (command) {
        case "nslookup":
            testConfig.script="timeout ${timeout} nslookup ${testConfig.host}"
            testConfig.requiredToConnect=false
            testConfig.runcount=1
            return runTestScript(testConfig)
            break
        case "dig":
//            testConfig.script="timeout ${timeout} dig +short ${host}"
            testConfig.script="timeout ${timeout} dig ${testConfig.host}"
            testConfig.requiredToConnect=false
            testConfig.runcount=1
            return runTestScript(testConfig)
            break
        case "mtr":
            testConfig.script="timeout ${timeout} mtr --report ${testConfig.host}"
            testConfig.requiredToConnect=false
            return runTestScript(testConfig)
            break
        case "traceroute":
//            testConfig.script="timeout ${timeout} traceroute ${host}"
            testConfig.script="timeout ${timeout} traceroute -w3 -q1 ${testConfig.host}"
            testConfig.requiredToConnect=true
            return runTestScript(testConfig)
            break
        case "tracepath":
            testConfig.script="timeout ${timeout} tracepath ${testConfig.host}"
            testConfig.requiredToConnect=true
            return runTestScript(testConfig)
            break
        case "ping":
            testConfig.script="timeout ${timeout} ping -c1 ${testConfig.host}"
            testConfig.requiredToConnect=false
            return runTestScript(testConfig)
            break
        case "bash":
            testConfig.script="timeout ${timeout} bash -c \"</dev/tcp/${testConfig.host}/${testConfig.port}\""
            testConfig.requiredToConnect=true
            return runTestScript(testConfig)
            break
        case "telnet":
            testConfig.script="timeout ${timeout} bash ./testConnTelnet.sh ${testConfig.host} ${testConfig.port} ${timeout}"
            testConfig.requiredToConnect=true
            return runTestScript(testConfig)
            break
        case "curl":
            testConfig.script="curl --connect-timeout ${timeout} -vkIsSL ${testConfig.targetUrl}"
            if (testConfig.httpProxyHost) {
                String proxyUrl="${testConfig.httpProxyHost}"
                if (testConfig?.httpProxyPort) {
                    proxyUrl += ":${testConfig.httpProxyPort}"
                }
                testConfig.script+=" --proxy ${proxyUrl}"
            }
            testConfig.requiredToConnect=true
            return runTestScript(testConfig)
            break
        case "curl-verifycerts":
            testConfig.script="curl --connect-timeout ${timeout} -vIsSL ${testConfig.targetUrl}"
            if (testConfig.httpProxyHost) {
                String proxyUrl="${testConfig.httpProxyHost}"
                if (testConfig?.httpProxyPort) {
                    proxyUrl += ":${testConfig.httpProxyPort}"
                }
                testConfig.script+=" --proxy ${proxyUrl}"
            }
            testConfig.requiredToConnectSSL=true
            return runTestScript(testConfig)
            break
        case "openssl":
            testConfig.script="timeout ${timeout} openssl s_client -connect ${testConfig.host}:${testConfig.port}"
            // ref: https://stackoverflow.com/questions/3220419/openssl-s-client-using-a-proxy
            // ref: https://rt.openssl.org/Ticket/Display.html?id=2651&user=guest&pass=guest
            if (testConfig.httpProxyHost) {
                String proxyUrl="${testConfig.httpProxyHost}"
                if (testConfig?.httpProxyPort) {
                    proxyUrl += ":${testConfig.httpProxyPort}"
                }
                testConfig.script+=" -proxy ${proxyUrl}"
            }
            testConfig.requiredToConnectSSL=true
            return runTestScript(testConfig)
            break
        case "sslpoke":
            String mvnCmd="mvn ${testConfig.mvnLogOptions} -Djavax.net.debug=all"
            if (testConfig?.httpProxyHost) {
                mvnCmd+=" -Dhttp.proxyHost=${testConfig.httpProxyHost}"
                mvnCmd+=" -Dhttps.proxyHost=${testConfig.httpProxyHost}"
            }
            if (testConfig?.httpProxyPort) {
                mvnCmd+=" -Dhttp.proxyPort=${testConfig.httpProxyPort}"
                mvnCmd+=" -Dhttps.proxyPort=${testConfig.httpProxyPort}"
            }
            mvnCmd+=" -Dexec.mainClass=com.dettonville.api.SSLPoke -Dexec.args=\"${testConfig.host} ${testConfig.port} ${timeout}\" exec:java"

            testConfig.script="${mvnCmd}"
            testConfig.requiredToConnectSSL=true
            return runTestScript(testConfig)
            break
        case "httpclienttest":
            log.info("${logPrefix} testConfig.targetUrl=${testConfig.targetUrl}")
            String mvnCmd="mvn ${testConfig.mvnLogOptions}"
            // ref: https://hc.apache.org/httpcomponents-client-ga/logging.html
            mvnCmd+=" -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog"
            mvnCmd+=" -Dorg.apache.commons.logging.simplelog.showdatetime=true"
            mvnCmd+=" -Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG"
            mvnCmd+=" -Dorg.apache.commons.logging.simplelog.log.org.apache.http.wire=ERROR"
            if (testConfig?.httpProxyHost) {
                mvnCmd+=" -Dhttps.proxyHost=${testConfig.httpProxyHost}"
                mvnCmd+=" -Dhttp.proxyHost=${testConfig.httpProxyHost}"
            }
            if (testConfig?.httpProxyPort) {
                mvnCmd+=" -Dhttps.proxyPort=${testConfig.httpProxyPort}"
                mvnCmd+=" -Dhttp.proxyPort=${testConfig.httpProxyPort}"
            }
            mvnCmd+=" -Dexec.mainClass=com.dettonville.api.HttpClientTest -Dexec.args=\"${testConfig.targetUrl} ${timeout}\" exec:java"

            testConfig.script="${mvnCmd}"
            testConfig.requiredToConnectSSL=true
            return runTestScript(testConfig)
            break
        case "sslhandshake":
            log.debug("${logPrefix} testConfig.targetUrl=${testConfig.targetUrl}")
            String mvnCmd="mvn ${testConfig.mvnLogOptions} -Djavax.net.debug=ssl:handshake"
            if (testConfig?.httpProxyHost) {
                mvnCmd+=" -Dhttps.proxyHost=${testConfig.httpProxyHost}"
                mvnCmd+=" -Dhttp.proxyHost=${testConfig.httpProxyHost}"
            }
            if (testConfig?.httpProxyPort) {
                mvnCmd+=" -Dhttps.proxyPort=${testConfig.httpProxyPort}"
                mvnCmd+=" -Dhttp.proxyPort=${testConfig.httpProxyPort}"
            }
            mvnCmd+=" -Dexec.mainClass=com.dettonville.api.TestSSLHandShake -Dexec.args=\"${testConfig.targetUrl} ${timeout}\" exec:java"

            testConfig.script="${mvnCmd}"
            testConfig.requiredToConnectSSL=true
            return runTestScript(testConfig)
            break
        default: throw new Exception("${logPrefix} unknown command [${command}] ")
    }

}

Map runTestScript(Map testConfig) {

    if (testConfig.debugPipeline) log.debug("runTestScript(testConfig=${testConfig}): started")
    String logPrefix="runTestScript(${testConfig.command}):"
    log.debug("${logPrefix} started")

    Map returnStatus = testConfig.clone()
    try {
        returnStatus << shellCommandOutput(testConfig)
        log.debug("${logPrefix} returnStatus=${returnStatus}")
    } catch (Exception err) {
        returnStatus.status = returnStatus.status ?: 999
        log.error("${logPrefix} exception occurred [${err}] \n currentBuild.result=${currentBuild.result}")
    }
    return returnStatus
}


// ref: https://medium.com/garbage-collection/jenkins-pipelines-what-i-wish-i-knew-starting-out-6e3d4eb2ff5b
// ref: https://stackoverflow.com/questions/36547680/how-to-do-i-get-the-output-of-a-shell-command-executed-using-into-a-variable-fro
Map shellCommandOutput(Map testConfig) {
    Map returnStatus = [:]

    if (testConfig.debugPipeline) log.debug("shellCommandOutput(testConfig=${testConfig}): started")
    def logPrefix="shellCommandOutput(${testConfig.command}):"

    def filepath = "${BUILD_URL}artifact/logs/${testConfig.nodeName}/${testConfig.filename}"
    returnStatus.filepath=filepath

    def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm")
    // test start time
    def startDate = new Date()
    returnStatus.startDate = dateFormat.format(startDate)

//    log.debug("${logPrefix} testConfig.filepath=${testConfig.filepath}")
    log.debug("${logPrefix} testConfig.runcount=${testConfig.runcount}")

    log.debug("${logPrefix} filename=${testConfig.filename}")
    sh script: "echo ${testConfig.script} > ${testConfig.filename}", returnStatus: true
    sh script: "echo Results: >> ${testConfig.filename}", returnStatus: true
    sh script: "echo \"\n\n######\" >> ${testConfig.filename}", returnStatus: true
    int numSuccess=0
    int status=0
    for (int i = 1; i  <= testConfig.runcount; i++) {
        if (testConfig.runcount>1) sh script: "echo \"\n*** iteration ${i} STARTED *** \" >> ${testConfig.filename}", returnStatus: true
        log.debug("${logPrefix} [iter${i}]: ${testConfig.script} >> ${testConfig.filename}")
        int retstat
        if (testConfig.useSimulationMode) {
            retstat = sh(script: "echo 'SIMULATION MODE - not actually running command [${testConfig.script}]' >> ${testConfig.filename}", returnStatus: true)
        } else if (testConfig?.httpProxyHost && ["traceroute","mtr","ping","telnet","openssl"].contains(testConfig.command)) {
            // these commands cannot be proxied - so skip tests
            sh(script: "echo 'proxy [${testConfig.httpProxyHost}] specified for command that cannot be proxied - skipping command [${testConfig.script}]' >> ${testConfig.filename}", returnStatus: true)
            retstat = -1
        } else if (testConfig.scheme=="http" && ["openssl","curl-verifycerts","sslpoke","sslhandshake"].contains(testConfig.command)) {
            // these commands require SSL connection - so skip tests
            sh(script: "echo 'HTTP scheme specified for SSL command - skipping command [${testConfig.script}]' >> ${testConfig.filename}", returnStatus: true)
            retstat = -1
        } else {
//            retstat = sh(script: "${testConfig.script} >> ${testConfig.filename} 2>&1", returnStatus: true)
            retstat = sh(script: "set -o pipefail; ${testConfig.script} 2>&1 | tee -a ${testConfig.filename}", returnStatus: true)
        }
        log.debug("${logPrefix} return status=${retstat}")
        sh script: "echo return status=${retstat} >> ${testConfig.filename}", returnStatus: true

        numSuccess += (retstat==0) ? 1 : 0

        if (testConfig.runcount>1) {
//            status += (retstat) ? 1 : 0
            status += (retstat>0) ? 1 : (retstat<0) ? -1 : 0
        } else {
            status = retstat
        }
        if (testConfig.runcount>1) sh script: "echo \"numSuccess=${numSuccess}\" >> ${testConfig.filename}", returnStatus: true
        if (testConfig.runcount>1) sh script: "echo \"\n************************************************************************\" >> ${testConfig.filename}", returnStatus: true
        if (testConfig.runcount>1) sh script: "echo \"************************************************************************\" >> ${testConfig.filename}", returnStatus: true
    }

    // test end time
    def endDate = new Date()
    returnStatus.endDate = dateFormat.format(endDate)

//    returnStatus.successRate=numSuccess/testConfig.runcount*100.0
    float successRate=numSuccess/testConfig.runcount
    successRate*=100.0
    returnStatus.successRate=successRate

    sh script: "echo \"\n\n###### TEST SUMMARY ######\" >> ${testConfig.filename}", returnStatus: true
    sh script: "echo \"\nsuccessRate=${returnStatus.successRate}\" >> ${testConfig.filename}", returnStatus: true
    sh script: "echo final return status=${status} >> ${testConfig.filename}", returnStatus: true

    if ( status ) {
        def msg = "Connection failed with retval=${status}"
        if (testConfig.runcount>1) { msg += " and successRate=${returnStatus.successRate}" }
        log.info("${logPrefix} ${msg}")
    } else {
        log.info("${logPrefix} Connection success")
    }

//    returnStatus.cmd_return_status=status
    returnStatus.status = status
    returnStatus.result = (status>0) ? "FAIL" : (status<0) ? "SKIPPED" : "PASS"

    log.debug("${logPrefix} returnStatus=${returnStatus}")

    return returnStatus
}

