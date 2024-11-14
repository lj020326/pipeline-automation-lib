#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import groovy.json.JsonOutput

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

timestamps {
node ('DEVCLD-LIN7') {

    Map config = [:]
    Map currentState = [:]

    config.jenkinsProjectName = env.JOB_NAME.split('/')[0]
    config.useBrowserstackProxy = true
    config.browserstackProxyHost = "outboundproxy.dettonville.int"
    config.browserstackProxyPort = "15768"
    config.forceCleanupBsBaseDir = false

    config.cleanupOrphanedBsAgents = true

    config.jenkinsBsCredId = "dcapi_browserstack_creds"
    config.jenkinsRepoCredId = "dcapi_ci_vcs_user"

    config.useBrowserstackProxy = false
//    config.debugPipeline = true
    config.debugPipeline = false
    config.useSimulationMode = false
//    config.browserstackLocalIdentifier="bsAgentTest"

    config.useBrowserstackLocalAgent = true
    config.bsAgentDistGitRepo = "https://gitrepository.dettonville.int/stash/scm/api/deployment_configs.git"
    config.bsAgentBinType = "linux-x64"
    config.bsAgentVersion = "7.5"

//    config.bsAgentBaseDir = "/var/tmp/dcapi/${config.browserstackLocalIdentifier}"
    config.bsAgentBaseDir = "tmp/bslocalagent"
    config.bsAgentBinDir = "tmp/bslocalagent/bin"
    config.bsAgentLogDir = "tmp/bslocalagent/logs"

    config.runBsAgentMethod = "PER_RUN"

    config.waitTime = 0

    config.jenkinsRunTestsLabel = "DEVCLD-LIN7 || QA-LINUX || PROD-LINUX"
//    config.jenkinsRunTestsLabel = "jnk4stl1"
//    config.jenkinsRunTestsLabel = "jnk4stl2"
//    config.jenkinsRunTestsLabel = "ech-10-157-152-198"
//    config.jenkinsRunTestsLabel = "ech-10-157-148-169"

    def agentLabelRunTests = getJenkinsAgentLabel(config.jenkinsRunTestsLabel)

    Map testRuns = [:]

    log.info("define 2 runs - one to start the agent and second waits and then should reuse the existing running agent")
    testRuns['startBsAgent'] = {
        Map runConfig = config.clone()
        runConfig.parallelRunNumber = 1

        node(agentLabelRunTests as String) {
            runTest(runConfig, currentState)
        }
    }
    testRuns['checkBsAgent'] = {
        Map runConfig = config.clone()
        runConfig.parallelRunNumber = 2
        runConfig.waitTime = 20

        node(agentLabelRunTests as String) {
            runTest(runConfig, currentState)
        }
    }

    log.info("run the 2 runs")

    stage("Run Tests") {
        parallel testRuns
    }

    node(agentLabelRunTests as String) {
        log.info("**** check if any browserstacklocal process still running...")
        try {
            String processInfoList = sh(script: "ps -ef | grep -i browserstacklocal | grep -v grep", returnStdout: true)
            log.debug("browserstacklocal process already running: [\n${processInfoList}]")
        } catch (Exception err) {
            log.debug("browserstacklocal process not running")
        }
    }
}

String getJenkinsAgentLabel(String jenkinsLabel) {
    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically
    return "${-> println 'Right Now the Jenkins Agent Label Name is ' + jenkinsLabel; return jenkinsLabel}"
}


String printToJsonString(Map config) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(config))
}

String getTestCaseLabel(Map config) {
    return (config?.testCaseId) ? "${config.testCaseId}-run${config.parallelRunNumber}" : "run${config.parallelRunNumber}"
}

def getResourceFile(String fileName) {
    def file = libraryResource fileName
    // create a file with fileName
    writeFile file: "./${fileName}", text: file
}

String createBrowserstackLocalIdentifier(Map config) {
    String logPrefix="[${config.testCaseLabel}-${config.nodeId}] createBrowserstackLocalIdentifier():"
    String browserstackLocalIdentifier
    if (config.runBsAgentMethod == "PER_RUN") {
//        browserstackLocalIdentifier = "${nodeId}-${config.jobName}-${config.testCaseLabel}-${config.browserstackLocalIdentifier}"
//        browserstackLocalIdentifier = "run-${config.jobName}-${env.BUILD_NUMBER}-${config.nodeId}-${config.testCaseLabel}"
        browserstackLocalIdentifier="${config.jenkinsProjectName}-${UUID.randomUUID()}"
    } else if (config.runBsAgentMethod == "PER_JOB_RUN") {
        browserstackLocalIdentifier="jobrun-${config.jobName}-${env.BUILD_NUMBER}-${config.nodeId}"
    } else if (config.runBsAgentMethod == "PER_JOB") {
//        browserstackLocalIdentifier="${env.NODE_NAME}-${config.jobName}-${config.browserstackLocalIdentifier}"
//        browserstackLocalIdentifier="${nodeId}-${config.jobName}-${config.browserstackLocalIdentifier}"
        browserstackLocalIdentifier="job-${config.jobName}-${config.nodeId}"
    } else {
        // PER_NODE
        browserstackLocalIdentifier="node-${config.jenkinsProjectName}-${config.nodeId}"
    }

    browserstackLocalIdentifier=browserstackLocalIdentifier.toLowerCase()
    log.info("${logPrefix} -> created browserstackLocalIdentifier=${browserstackLocalIdentifier}")
    return browserstackLocalIdentifier
}

void runTest(Map config, Map currentState) {
    config.nodeId=env.NODE_NAME.replaceAll('-', '')
    config.testCaseLabel=getTestCaseLabel(config)

    String logPrefix="[${config.testCaseLabel}-${config.nodeId}] runTest():"
    log.info("${logPrefix} run ${config.parallelRunNumber} started")

    if (config.waitTime>0) {
        log.info("${logPrefix} waiting for ${config.waitTime} seconds before checking to see that the BS agent is still running")
        sleep(time: config.waitTime, unit: 'SECONDS')
    }

    if (config.useBrowserstackLocalAgent) {
        config.browserstackLocalIdentifier=createBrowserstackLocalIdentifier(config)
        log.info("${logPrefix} *** ASSIGNED browserstackLocalIdentifier=${config.browserstackLocalIdentifier}")
    }

    log.info("${logPrefix} running BS agent action() closure for run ${config.parallelRunNumber}")

    dir(config.testCaseLabel) {
        withBsLocalAgent log, config, currentState, {
            log.info("${logPrefix} we are here")
        }
    }

    log.info("${logPrefix} run ${config.parallelRunNumber} finished")
}

// COPY AND PASTE FROM runATH

def getBSAgent(Map config) {
    String logPrefix="getBSAgent():"
    if (config.runBsAgentMethod!='PER_RUN') {
        logPrefix="[${config.testCaseLabel}-${config.nodeId}] ${logPrefix}"
    }

    log.info("${logPrefix} starting")

    boolean installBsAgent = false
    if (!fileExists("${config.bsAgentBinDir}/BrowserStackLocal")) {
        installBsAgent = true
        log.info("${logPrefix} binary not found, fetching...")
    } else {
        String bsAgentVersion = sh(script: "${config.bsAgentBinDir}/BrowserStackLocal --version", returnStdout: true).trim()
        if (!bsAgentVersion.contains(config.bsAgentVersion)) {
            installBsAgent = true
            log.info("${logPrefix} binary version ${bsAgentVersion} does not match ${config.bsAgentVersion}, fetching...")
        } else {
            log.info("${logPrefix} agent binary with version ${config.bsAgentVersion} already exists at ${config.bsAgentBinDir}")
        }
    }

    if (installBsAgent) {
        log.info("${logPrefix} getting agent binary")
        sh "mkdir -p ${config.bsAgentBinDir}"

        log.info("${logPrefix} Fetching Browserstack agent")
        dir('deploy_configs') {
            checkout scm: [
                    $class: 'GitSCM',
                    branches: [[name: "main"]],
                    userRemoteConfigs: [[credentialsId: config.jenkinsRepoCredId, url: config.bsAgentDistGitRepo]]
            ]
        }
        String archivePath="deploy_configs/resources/BrowserStackLocal-${config.bsAgentBinType}.zip"

//        sh 'find deploy_configs -type f'

        sh "cp -r ${archivePath} ${config.bsAgentBinDir}"

        sh "unzip -o ${config.bsAgentBinDir}/BrowserStackLocal-${config.bsAgentBinType}.zip -d ${config.bsAgentBinDir}"
        sh "chmod +x ${config.bsAgentBinDir}/BrowserStackLocal"

        log.info("${logPrefix} Browserstack agent deployed to ${config.bsAgentBinDir}")
    }

}


//
// ref: https://janmolak.com/jenkins-2-0-pipelines-and-browserstack-bd5a4ed3010d
//
def withBsLocalAgent(Map config, Map currentState, def actions) {

    String logPrefix = "${scriptName}->[${config.testCaseLabel}-${config.nodeId}] withBsLocalAgent():"
    log.info("${logPrefix} config.browserstackLocalIdentifier=${config.browserstackLocalIdentifier}")

    config.bsPidFile = "${config.bsAgentLogDir}/bsagent-${config.browserstackLocalIdentifier}.pid"
    config.bsLogFile = "${config.bsAgentLogDir}/bsagent-${config.browserstackLocalIdentifier}.log"
//    config.bsDiagLogFile = "${config.bsAgentLogDir}/bs-diagnostics-${config.testCaseLabel}-${config.nodeId}.log"
    config.bsDiagLogFile = "${config.bsAgentLogDir}/bs-diagnostics-${config.browserstackLocalIdentifier}.log"

    if (config.useSimulationMode) {
        try {
            actions()
        } catch (Exception err) {
            log.error("${logPrefix} actions(): exception occurred [${err}]")
            sh "cat ${config.bsLogFile}"
        }
        return
    }

    if (!currentState.containsKey("nodes")) {
        log.info("${logPrefix} initializing currentState.nodes map")
        currentState.nodes = [:]
    }
    if (!currentState.nodes.containsKey(env.NODE_NAME)) {
        log.info("${logPrefix} initializing currentState.nodes[${env.NODE_NAME}] map")
        currentState.nodes[env.NODE_NAME] = [:]
    }
    if (!currentState.nodes[env.NODE_NAME].containsKey("bsLocalAgents")) {
        log.info("${logPrefix} initializing currentState.nodes[${env.NODE_NAME}].bsLocalAgents map")
        currentState.nodes[env.NODE_NAME].bsLocalAgents = [:]
    }
    if (!currentState.nodes[env.NODE_NAME].bsLocalAgents.containsKey(config.browserstackLocalIdentifier)) {
        log.info("${logPrefix} initializing currentState.nodes[${env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}] map")
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier] = [:]
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning = false
    }

    Map currentAgentState = currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]
    log.debug("${logPrefix} started with currentAgentState[node=[${env.NODE_NAME}], browserstackLocalIdentifier=${config.browserstackLocalIdentifier}]=${printToJsonString(currentAgentState)}")
    if (currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning == false) {
        log.info("${logPrefix} browserstacklocal agent for ${config.browserstackLocalIdentifier} not running, starting")

        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount = 1

        getBSAgent(config)

        startBSAgent(config, currentState)

//        log.info("${logPrefix} starting -> currentState.nodes[${env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}]=${currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]}")
        log.debug("${logPrefix} started AGENT -> currentState=${printToJsonString(currentState)}")

//        log.info("${logPrefix} run find after starting BS agent")
//        sh "find /var/tmp/${config.jenkinsProjectName} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
        log.info("${logPrefix} browserstacklocal agent for ${config.browserstackLocalIdentifier} started with pid = [${currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid}]")
    } else {
        if (!currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("bsAgentPid")) {
            log.info("${logPrefix} Bs Agent process status assigned but not started yet for ${config.browserstackLocalIdentifier}")

            int bsAgentWaitTime = 5
            int bsAgentMaxWaits = 10
            for (int i = 1; i  <= bsAgentMaxWaits; i++) {
                log.info("${logPrefix} waiting ${bsAgentWaitTime} seconds for agent to start...")
                sleep(time: bsAgentWaitTime, unit: 'SECONDS')
                if (currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("bsAgentPid")) {
                    log.info("${logPrefix} discovered Bs Agent process started with pid=[${currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid}]")
                    break
                } else {
                    log.info("${logPrefix} Bs Agent process status not yet started after sleep #${i} for ${config.browserstackLocalIdentifier}")
                }
            }
            if (!currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].containsKey("bsAgentPid")) {
                String message = "${logPrefix} Bs Agent process status assigned but never started within ${bsAgentWaitTime * bsAgentMaxWaits} seconds for ${config.browserstackLocalIdentifier}"
                log.info("${message}")
                throw message
            }
        }
        log.info("${logPrefix} checking Bs Agent process status for pid=[${currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid}]")
        runBsAgentPsCheck(config, currentState, false, true)
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount += 1
    }

    if (config.debugPipeline) {
        log.info("${logPrefix} run find before running actions()")
        sh "find ${config.bsAgentLogDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
    }

    try {
        actions()
    } catch (Exception err) {
        log.error("${logPrefix} actions(): exception occurred [${err}]")
        log.info("${logPrefix} checking Bs Agent process status")
        runBsAgentPsCheck(config, currentState, true, true)

//        sh "cat ${config.bsLogFile}"
        sh "tail -30 ${config.bsLogFile}"

    } finally {
        if (config.debugPipeline) {
            log.debug("${logPrefix} post action find")
            sh "find ${config.bsAgentLogDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
        }

        if (config.runBSCurlTest || config.runBsDiagnostics) {
            runBSCurlTest(config)
        }

        if (config.runBsDiagnostics) {
            log.info("${logPrefix} Including process info in diagnostics")
            runBsAgentPsCheck(config, currentState, true, true)
        }

        if (currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount == 1) {
            if (["PER_RUN", "PER_JOB_RUN"].contains(config.runBsAgentMethod)) {
                log.info("${logPrefix} last run complete, stopping agent")
                stopRunningBsAgent(config, currentState)
            } else if (config.forceShutdownBsAgent) {
                log.info("${logPrefix} last run complete, forceably stopping agent")
                stopRunningBsAgent(config, currentState)
            } else {
                archiveBsAgentLogs(config, currentState)
            }
        }

        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].runningRunCount -= 1
        log.debug("${logPrefix} finished -> currentState=${printToJsonString(currentState)}")

    }
}

boolean startBSAgent(Map config, Map currentState) {
    String logPrefix="[${config.testCaseLabel}-${config.nodeId}] startBSAgent():"
    log.info("${logPrefix} starting agent for config.browserstackLocalIdentifier=${config.browserstackLocalIdentifier}")

    log.debug("${logPrefix} started -> currentState=${printToJsonString(currentState)}")

    if (config.debugPipeline) {
        log.debug("${logPrefix} run find before running actions() on ${config.bsAgentBaseDir}")
        sh "find ${config.bsAgentBaseDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
    }

    if (config.cleanupOrphanedBsAgents) {
        cleanupOrphanedBsAgents(config)
    }

    String pid
    if (currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]?.bsAgentPid) {
        pid = currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid
        log.debug("${logPrefix} runningAgents pid=[${pid}]")
    } else if (fileExists(config.bsPidFile)) {
        log.debug("${logPrefix} pid file exists, check if actually running or if its stale")
        pid = sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid = pid
    }

    if (!currentState.nodes.containsKey(env.NODE_NAME)) {
        currentState.nodes[env.NODE_NAME]=[:]
        currentState.nodes[env.NODE_NAME].isBsAgentRunning = false
    }

    if (config.runBSCurlTest || config.runBsDiagnostics) {
        log.info("${logPrefix} initializing directory and diagnostics logfile")
        sh script: "touch ${config.bsDiagLogFile}"

        log.debug("${logPrefix} checking Bs Agent process status across all running agents")
        runBsAgentPsCheck(config, currentState, true, true)
    }

    if (pid!=null) {
        runBsAgentPsCheck(config, currentState)
        log.info("${logPrefix} pid file exists and already running -> setting pid on runningAgent map")
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning == true
        return true
    }

    log.info("${logPrefix} initializing bs agent log directory ${config.bsAgentLogDir}")
    sh "mkdir -p ${config.bsAgentLogDir}"

    boolean result

    // Prepare the BrowserStackLocal client
    withCredentials([usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {

        // Start browserstacklocal agent
//        String browserStackCmd = "BUILD_ID=dontKillMe nohup"
        String browserStackCmdPrefix = "set -o pipefail; PROJECT=${config.jenkinsProjectName} BUILD_ID=dontKillMe nohup ${config.bsAgentBinDir}/"
        String browserStackCmd = "BrowserStackLocal --key ${BS_KEY} "

        // leave all ENV specific settings to runATHEnv wrapper - not to be done here
        if (config.useBrowserstackProxy) {
            if (config.browserstackProxyHost) {
                browserStackCmd += " --proxy-host ${config.browserstackProxyHost}"
            }
            if (config.browserstackProxyPort) {
                browserStackCmd += " --proxy-port ${config.browserstackProxyPort}"
            }
            if (config.forceBrowserstackProxy) {
                browserStackCmd += " --force-proxy"
            }
        }
        if (config.useBrowserstackLocalProxy) {
            browserStackCmd += " --local-proxy-host ${config.browserstackProxyHost} --local-proxy-port ${config.browserstackProxyPort}"
            if (config.forceBrowserstackLocalProxy) {
                browserStackCmd += " --force-local"
            }
        }

        // ref: https://www.browserstack.com/local-testing
        if (config.browserstackUseIdentifier) {
            browserStackCmd += " --local-identifier ${config.browserstackLocalIdentifier} "
        }

        browserStackCmd += " --verbose 3"
        //browserStackCmd += " --verbose"
//        String browserStackCmdMasked = browserStackCmd.replaceAll(/(\w+) --key (\w+) (\w+)/,'$1 --key *** $3')
        String browserStackCmdMasked = browserStackCmd.replaceAll("${BS_KEY}","***")

        browserStackCmd = "${browserStackCmdPrefix}${browserStackCmd} > ${config.bsLogFile} 2>&1 & echo \$! > ${config.bsPidFile}"

        int retstat = sh(script: "${browserStackCmd}", returnStatus: true)
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].browserStackCmd = browserStackCmdMasked

        pid = sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid = pid
        log.info("${logPrefix} process pid=[${pid}] return status = [${retstat}]")

        result = (retstat) ? false : true

        if (result) {
            currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning == true
        } else {
            currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning == false

            log.error("${logPrefix} process start failed for pid=[${pid}]")
            if (fileExists(config.bsLogFile)) {
                sh "tail -30 ${config.bsLogFile}"
            }

            log.error("${logPrefix} checking if running BS process actually exists...")

            log.info("${logPrefix} checking Bs Agent process status across running agent for pid")
            runBsAgentPsCheck(config, currentState, false, true)

        }
    }
    return result

}

def stopRunningBsAgent(Map config, Map currentState) {
    String logPrefix = "${scriptName}->[${config.testCaseLabel}-${config.nodeId}] stopRunningBsAgent():"

    log.info("${logPrefix} starting")

    log.info("${logPrefix} currentState.nodes[${env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}]= ${currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]}")

    String pid
    if (currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]?.bsAgentPid) {
        pid = currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid
        log.info("${logPrefix} runningAgents pid=[${pid}]")
    } else if (config?.bsPidFile) {
        log.info("${logPrefix} pid not found in running agents map, sourcing pid from ${config.bsPidFile}")
        pid = sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
        log.info("${logPrefix} ${config.bsPidFile} pid=[${pid}]")
    }

    if (config.debugPipeline) {
        log.info("${logPrefix} run find before archive/cleanup")
        sh "find ${config.bsAgentLogDir} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n\""
    }

    log.info("${logPrefix} show all BS agent running processes")
    runBsAgentPsCheck(config, currentState, true)

//    try {
//        cleanupOrphanedBsAgents(config)
//    } catch (Exception err) {
//        log.info("${logPrefix} exception when cleaning up orphaned BS agents [${err}]")
//    }

    log.info("${logPrefix} stopping browserstacklocal agent execution for ${config.browserstackLocalIdentifier}")
    // Stop the connection
    try {
//        sh "kill -9 `cat ${config.bsPidFile}`"
        sh "kill -9 ${pid}"
        currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].isBsAgentRunning == false
    } catch (Exception err) {
        log.error("${logPrefix} kill: browserstack cleanup exception occurred [${err}]")
        sh "tail -30 ${config.bsLogFile}"
    }

    archiveBsAgentLogs(config, currentState)

    if (["PER_RUN", "PER_JOB_RUN"].contains(config.runBsAgentMethod)
            || config.forceShutdownBsAgent)
    {
        log.info("${logPrefix} cleaning up agent bindir and any residue/artifacts for ${config.bsAgentLogDir}")
        sh "rm -fr ${config.bsAgentLogDir}"
    }

    if (config.forceCleanupBsBaseDir) {
        log.info("${logPrefix} cleaning browserstacklocal agent root dir ${config.bsAgentBaseDir}")
        sh "rm -fr ${config.bsAgentBaseDir}"
    }
}

def runBsAgentPsCheck(Map config, Map currentState, boolean showAllBsAgents = false, boolean writeToBsDiagLog = false) {
    String logPrefix = "${scriptName}->[${config.testCaseLabel}-${config.nodeId}] runBsAgentPsCheck():"
    String processInfo
    String processCmd

    log.info("${logPrefix} started")
    log.debug("${logPrefix} currentState.nodes[${env.NODE_NAME}].bsLocalAgents[${config.browserstackLocalIdentifier}] = ${currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]}")
    if (!showAllBsAgents) {

        String pid
        if (currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier]?.bsAgentPid) {
            pid = currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid
            log.info("${logPrefix} runningAgents pid=[${pid}]")
        } else if (config?.bsPidFile) {
            log.info("${logPrefix} pid not found in running agents map, sourcing pid from ${config.bsPidFile}")
            pid = sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
            log.info("${logPrefix} ${config.bsPidFile} pid=[${pid}]")
        }
//    String pid = sh(script: "cat ${config.bsPidFile}", returnStdout: true).trim()
//    String pid = currentState.nodes[env.NODE_NAME].bsLocalAgents[config.browserstackLocalIdentifier].bsAgentPid

        log.info("${logPrefix} checking process status for pid=[${pid}]")
        try {
            processCmd = "ps -ef | grep ${pid} | grep -v grep"

            processInfo = sh(script: "ps -ef | grep ${pid} | grep -v grep", returnStdout: true).trim()
//            log.info("${logPrefix} Browserstack local agent already running with pid=[${pid}] processInfo = [${processInfo}]")
        } catch (Exception err) {
            log.warn("${logPrefix} browserstacklocal process does not exists")
            if (fileExists(config.bsPidFile)) {
                log.warn("${logPrefix} cleaning up pid file ${config.bsPidFile}")
                sh "rm ${config.bsPidFile}"
            }
        }
    } else {
        try {
            log.info("${logPrefix} checking process status for all BS agents")
//            processInfo = sh(script: "ps -ef | grep -v grep | grep -i browserstacklocal", returnStdout: true).trim()
            processInfo = sh(script: "ps axo pid=,stat=,ppid=,user=,lstart=,command= | grep -v grep | grep -i browserstacklocal", returnStdout: true).trim()
        } catch (Exception err) {
            log.info("${logPrefix} browserstacklocal process does not exist")
        }
    }

    if (processInfo) {
        withCredentials([usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {
            processInfo = processInfo.replaceAll("${BS_KEY}", "***")
        }
    }
    log.info("${logPrefix} processInfo=[${processInfo}]")

    if (writeToBsDiagLog) {
        sh script: "echo \"${processCmd}\" >> ${config.bsDiagLogFile}", returnStatus: true
//        sh script: "echo ${processCmd} >> ${config.bsDiagLogFile}"
        sh script: "echo Results: >> ${config.bsDiagLogFile}"
        sh script: "echo \"######\" >> ${config.bsDiagLogFile}"
        sh script: "echo \"${processInfo}\" >> ${config.bsDiagLogFile}"
    }

    return processInfo

}

def archiveBsAgentLogs(Map config, Map currentState) {
    String logPrefix = "${scriptName}->[${config.testCaseLabel}-${config.nodeId}] archiveBsAgentLogs():"

    log.info("${logPrefix} archiving agent log")
    dir(config.bsAgentLogDir) {
        def logFiles = findFiles glob: '**/*.log'
        //            def logFiles = findFiles glob: "**/${config.bsLogFile}"
        if (logFiles.length > 0) {
            //                    archiveArtifacts artifacts: "${config.bsAgentLogDir}/\\*.log"
            //                archiveArtifacts artifacts: "*.log"
            //                archiveArtifacts artifacts: "${config.bsLogFile}"
            //                archiveArtifacts artifacts: "\\*.log"
            archiveArtifacts artifacts: "*.log"
            //                sh "rm \\*.log"
        }
    }
}

def cleanupOrphanedBsAgents(Map config) {
    String logPrefix="[${config.testCaseLabel}-${config.nodeId}] cleanupOrphanedBsAgents():"
    log.info("${logPrefix} starting")

    String debugFlag=(config.debugPipeline) ? "-x" : ""
//    String script="scripts/cleanup-zombie-process.sh"
    String script="scripts/cleanup-zombie-process.sh"
    getResourceFile(script)
//    sh 'find scripts -type f'

    log.info("${logPrefix} running script ${script} jenkins ${config.jenkinsProjectName} browserstacklocal")
    sh "bash ${debugFlag} ${script} jenkins ${config.jenkinsProjectName} browserstacklocal"

}

def runBSCurlTest(Map config) {
    String logPrefix="[${config.testCaseLabel}-${config.nodeId}] runBSCurlTest():"
//    String logFile = "${config.bsAgentLogDir}/bs-diagnostics-${config.testCaseLabel}-${config.nodeId}.log"

    withCredentials([usernamePassword(credentialsId: config.jenkinsBsCredId, passwordVariable: 'BS_KEY', usernameVariable: 'BS_USER')]) {

        String curlTestCmd = "curl"
        String scheme = "https"
        if (config.useBrowserstackProxy) {
            curlTestCmd += " -x ${config.browserstackProxyHost}:${config.browserstackProxyPort}"
            scheme = "http"
        }
        curlTestCmdMasked = "${curlTestCmd} -L ${scheme}://****:****@${config.browserstackHubUrl}/wd/hub/status"
        curlTestCmd += " -L ${scheme}://${BS_USER}:${BS_KEY}@${config.browserstackHubUrl}/wd/hub/status"
//        curlTestCmd += " 2>&1 | tee -a ${config.bsDiagLogFile}"
        sh script: "echo ${curlTestCmdMasked} >> ${config.bsDiagLogFile}", returnStatus: true
        sh script: "echo Results: >> ${config.bsDiagLogFile}", returnStatus: true
        sh script: "echo \"######\" >> ${config.bsDiagLogFile}", returnStatus: true

        try {
            log.info("${logPrefix} browserstack curl test results:")
//            def curlResults = sh(script: "set -o pipefail; ${curlTestCmd} 2>&1 | tee -a ${config.bsDiagLogFile}", returnStdout: true)
            sh script: "set -o pipefail; ${curlTestCmd} 2>&1 | tee -a ${config.bsDiagLogFile}", returnStdout: true
//            log.info("${logPrefix} browserstack curl test results: [\n${curlResults}]")
        } catch (Exception err) {
            log.warn("${logPrefix} browserstack curl test exception occurred: [${err}]")
        }
        sh script: "echo \"\n\n\" >> ${config.bsDiagLogFile}", returnStatus: true

    }
}
