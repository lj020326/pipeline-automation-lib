
import com.dettonville.api.pipeline.conntest.ConnectivitySummary
import com.dettonville.api.pipeline.conntest.SiteTestResults
import com.dettonville.api.pipeline.conntest.TestResult
import java.text.SimpleDateFormat

// https://support.cloudbees.com/hc/en-us/articles/217309497-Test-a-SSL-connection-from-Jenkins
//def call(Map config=[:]) {
def call(Map params=[:]) {

    def config=[:]

    // copy immutable params maps to mutable config map
//    config = params.clone()
    params.each { key, value ->
//        echo "params[${key}]=${value}"
        config[key]=value
    }

    // set default states
    config.NodeList = (config.NodeList) ? config.NodeList : env.AGENTS.split(',').sort()
    config.AddNodeList = (config.AddNodeList) ? config.AddNodeList : []
    config.SiteList = (config.SiteList) ? config.SiteList : ['www.google.com']
    config.Timeout = (config.Timeout) ? config.Timeout : 10
    config.EmailDist = (config.EmailDist) ? config.EmailDist : "lee.johnson@dettonville.com"
    config.AlwaysEmailDist = (config.EmailDist) ? config.EmailDist : "lee.johnson@dettonville.com"
    config.EmailFrom="DCAPI.Jenkins.Connectivity.Check@dettonville.com"

    config.each { key, value ->
        echo "config[${key}]=${value}"
    }

//    echo "config.NodeList=${config.NodeList}"
//    echo "config.AddNodeList=${config.AddNodeList}"
//    echo "config.SiteList=${config.SiteList}"
//    echo "config.Timeout=${config.Timeout}"
//    echo "config.EmailDist=${config.EmailDist}"

    def node_tests = [:]


    if (config.AddNodeList) {
        config.NodeList+=config.AddNodeList
        echo "config.NodeList (after appending AddNodeList)=${config.NodeList}"
    }

    ConnectivitySummary ConnectivitySummary = new ConnectivitySummary(this, config)

    pipeline {

//        agent any
        agent { label 'DEVCLD-LIN7' }
        tools {
            maven 'M3'
        }

        stages {

            stage("Setup") {
                agent {
                    label 'DEVCLD-LIN7'
                }
                steps {
                    deleteDir()
                    script {
                        git 'https://gitrepository.dettonville.int/stash/scm/api/testsslserver.git'
                        sh 'mvn clean compile'
                        stash name: 'connectivity-check'
                    }
                }
            }
            stage("Run Connectivity Tests") {
                steps {
                    script {

                        // ref: https://stage.cd.dettonville.int/jenkins/job/System/job/AgentCleaner/configure
                        for (String currAgent: config.NodeList) {
                            def agent=currAgent
//                            stage(agent) {
                                node_tests["split-${agent}"] = {

                                    figlet agent
                                    node(agent) {

                                        echo "env.NODE_NAME=${env.NODE_NAME}"
                                        script {
                                            unstash name: 'connectivity-check'

                                            // HOUSE KEEPING
                                            currentBuild.description = "Build @${env.NODE_NAME}[${env.EXECUTOR_NUMBER}]"

//                                            runNodeConnTest(ConnectivitySummary, config.SiteList, config.Timeout)
                                            runNodeConnTestParallel(ConnectivitySummary, config.SiteList, config.Timeout)

                                            def dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm")
//                                            def date = new Date()
//                                            def dateStr = dateFormat.format(date)
//                                            sh "mkdir -p logs/${env.NODE_NAME}/${dateStr}; mv *.log logs/${env.NODE_NAME}/${dateStr}"

                                            sh "mkdir -p logs/${env.NODE_NAME}; mv *.log logs/${env.NODE_NAME}"

//                                            archiveArtifacts artifacts: "logs/${env.NODE_NAME}/${dateStr}/*.log"
                                            archiveArtifacts artifacts: "logs/${env.NODE_NAME}/*.log"
                                            publishHTML([allowMissing         : true,
                                                         alwaysLinkToLastBuild: true,
                                                         keepAll              : false,
                                                         reportDir            : "logs/${env.NODE_NAME}/*",
                                                         reportFiles          : '*.log',
                                                         reportName           : "Connectivity Logs for ${env.NODE_NAME}"])
                                        }

                                    } // node
                                } // node_tests
//                            } // stage agent
                        } // for

                        parallel node_tests

                    } // script

                } // steps

            } // stage
            stage("Summary") {
                steps {
                    script {

                        dir('ConnectivitySummary') {
                            try {
                                echo "transforming results data to json and writing"
                                writeFile encoding: 'UTF-8', file: 'index.json', text: printToJsonString(resultMap)
                            } catch (Exception err) {
                                echo "writeFile(index.json): exception occurred [${err}]"
                            }

                            echo "getting report resources"
                            getResourceFile("conntest.zip.b64")
                            sh "base64 -d conntest.zip.b64 > conntest.zip"
                            sh "unzip -o conntest.zip"

                            echo "creating report"
                            ConnectivitySummary.createSummaryHtml()
                        }

                        sh 'find ConnectivitySummary/*.html -type f'

                        archiveArtifacts artifacts: "ConnectivitySummary/*.html"

                        publishHTML([allowMissing         : true,
                                     alwaysLinkToLastBuild: true,
                                     keepAll              : true,
                                     reportDir            : 'ConnectivitySummary',
                                     reportFiles          : "index.html",
                                     reportName           : "Pipeline Summary"])

                    } // script

                }

            }

        } // stages
        post {
            changed {
                script {
//                    sendEmailReport(currentBuild, env, config.EmailDist, null, 'ConnectivitySummary/index.html')
                    sendEmailReport(config.EmailFrom, config.EmailDist, currentBuild, 'ConnectivitySummary/index.html')
                }
            }
            always {

                script {
                    // HOUSE KEEPING
                    def duration = "${currentBuild.durationString.replace(' and counting', '')}"
                    currentBuild.description = "Build @${env.NODE_NAME}[${env.EXECUTOR_NUMBER}]<br>Build Duration: ${duration}"

                    sendEmailReport(config.EmailFrom, config.AlwaysEmailDist, currentBuild, 'ConnectivitySummary/index.html')
                }
            }
        } // post

    } // pipeline

} // call



void runNodeConnTest(ConnectivitySummary, siteList, timeout) {

//    nodeId=env.NODE_NAME
    int order = 0
    for (String site : siteList) {

        site_info = site.split(":")
        def host = ""
        def port = ""
        try {
            host = site_info[0]
            port = site_info[1]
        } catch (Exception err) {
            echo "**** Starting tests: siteList=${siteList} ${env.NODE_NAME} *****"
            echo "exception occurred [${err}]"
            return
        }
        echo "**** Starting tests: site=${site} host=${host} port=${port} ${env.NODE_NAME} *****"

        SiteTestResults pipeStage = new SiteTestResults(site, env.NODE_NAME, order)

        echo "test1:DNS:${env.NODE_NAME}=>${host}"
        testDNSLookup(host, port, timeout, pipeStage)
        echo "test2:Bash:${env.NODE_NAME}=>${host}:${port}"
        testConnection(host, port, timeout, pipeStage)
        echo "test3:Telnet:${env.NODE_NAME}=>${host}:${port}"
        testConnectionTelnet(host, port, timeout, pipeStage)
        echo "test4:Curl:${env.NODE_NAME}=>${host}:${port}"
        testConnectionCurl(host, port, timeout, pipeStage)
        echo "test5:SSLPoke:${env.NODE_NAME}=>${host}:${port}"
        testConnectionSSLPoke(host, port, timeout, pipeStage)
        echo "test6:SSLHandshake:${env.NODE_NAME}=>${host}:${port}"
        testConnectionSSL(host, port, timeout, pipeStage)

        ConnectivitySummary.addSiteTestResults(pipeStage)
        echo "**** Finished tests: site=${site} host=${host} port=${port} ${env.NODE_NAME} *****"
        order += 1
    }
}


void runNodeConnTestParallel(ConnectivitySummary, siteList, timeout) {

    def site_tests = [:]

//    nodeId=env.NODE_NAME
    int currOrder = 0
    for (String currSite : siteList) {
        def site=currSite
        def order=currOrder

        site_tests["split-${order}-${site}"] = {
            site_info = site.split(":")
            def host = ""
            def port = ""
            try {
                host = site_info[0]
                port = site_info[1]
            } catch (Exception err) {
                echo "**** Starting tests: siteList=${siteList} ${env.NODE_NAME} *****"
                echo "exception occurred [${err}]"
                return
            }
            echo "**** Starting tests: site=${site} host=${host} port=${port} ${env.NODE_NAME} order=${order} *****"

            SiteTestResults pipeStage = new SiteTestResults(site, env.NODE_NAME, order)

            def tasks = [:]
            tasks["test1:DNS:${env.NODE_NAME}=>${host}"] = {
                testDNSLookup(host, port, timeout, pipeStage)
            }
            tasks["test2:Bash:${env.NODE_NAME}=>${host}:${port}"] = {
                testConnection(host, port, timeout, pipeStage)
            }
            tasks["test3:Telnet:${env.NODE_NAME}=>${host}:${port}"] = {
                testConnectionTelnet(host, port, timeout, pipeStage)
            }
            tasks["test4:Curl:${env.NODE_NAME}=>${host}:${port}"] = {
                testConnectionCurl(host, port, timeout, pipeStage)
            }
            tasks["test5:SSLPoke:${env.NODE_NAME}=>${host}:${port}"] = {
                testConnectionSSLPoke(host, port, timeout, pipeStage)
            }
            tasks["test6:SSLHandshake:${env.NODE_NAME}=>${host}:${port}"] = {
                testConnectionSSL(host, port, timeout, pipeStage)
            }
            parallel tasks

            ConnectivitySummary.addSiteTestResults(pipeStage)
            echo "**** Finished tests: site=${site} host=${host} port=${port} ${env.NODE_NAME} order=${order} *****"
        }

        currOrder += 1
    }

    parallel site_tests

}


Boolean testDNSLookup(host, port, timeout, SiteTestResults) {

    // use command group to suppress spurious error messages
    // ref: https://superuser.com/questions/1065870/how-to-suppress-output-of-bash-command
//    cmd="timeout ${timeout} bash -c nslookup ${host}"
    cmd="timeout ${timeout} nslookup ${host}"

    return runTestScript("HTTP/HTTPS: nslookup", "nslookup-${host}-${port}", cmd, SiteTestResults, 1)
}

// ref: https://stackoverflow.com/questions/4922943/test-from-shell-script-if-remote-tcp-port-is-open
// ref: https://gist.github.com/Khoulaiz/41b387883a208d6e914b
Boolean testConnection(host, port, timeout, SiteTestResults) {

    // use command group to suppress spurious error messages
    // ref: https://superuser.com/questions/1065870/how-to-suppress-output-of-bash-command
//    cmd="timeout ${timeout} bash -c \"{ </dev/tcp/${host}/${port}; } 2>/dev/null\""
    cmd="timeout ${timeout} bash -c \"</dev/tcp/${host}/${port}\""

    return runTestScript("HTTP/HTTPS: Bash", "bash-${host}-${port}", cmd, SiteTestResults, 2)
}

// ref: https://stackoverflow.com/questions/4922943/test-from-shell-script-if-remote-tcp-port-is-open
// ref: https://gist.github.com/Khoulaiz/41b387883a208d6e914b
Boolean testConnectionTelnet(host, port, timeout, SiteTestResults) {

    // use command group to suppress spurious error messages
    // ref: https://superuser.com/questions/1065870/how-to-suppress-output-of-bash-command
    testConnTelnet = libraryResource 'testConnTelnet.sh'
    writeFile file: 'testConnTelnet.sh', text: testConnTelnet
//    cmd="timeout ${timeout} bash ./testConnTelnet.sh ${host} ${port} ${timeout} 2>/dev/null"
    cmd="timeout ${timeout} bash ./testConnTelnet.sh ${host} ${port} ${timeout}"

    return runTestScript("HTTP/HTTPS: Telnet", "telnet-${host}-${port}", cmd, SiteTestResults, 3)
}


Boolean testConnectionCurl(host, port, timeout, SiteTestResults) {

    // ref: https://fusion.dettonville.int/stash/projects/PIPE/repos/pcf_connection_check/browse/Jenkinsfile
    cmd="curl --connect-timeout ${timeout} -kI https://${host}:${port}"

    return runTestScript("HTTP/HTTPS: Curl", "curl-${host}-${port}", cmd, SiteTestResults, 4)
}


Boolean testConnectionSSLPoke(host, port, timeout, SiteTestResults) {

    def timeout_with_mvn_cushion=timeout + 30
    // ref: https://gist.github.com/4ndrej/4547029
    // ref: https://fusion.dettonville.int/stash/projects/PIPE/repos/pcf_connection_check/browse/Jenkinsfile
    def mvnCmd="mvn -Djavax.net.debug=all -Dexec.mainClass=com.dettonville.api.SSLPoke -Dexec.args=\"${host} ${port} ${timeout}\" exec:java"
//    def cmd="timeout ${timeout_with_mvn_cushion} ${mvnCmd}"
    def cmd="${mvnCmd}"

    return runTestScript("HTTPS: SSL Poke", "sslpoke-${host}-${port}", cmd, SiteTestResults, 5)
}


// ref: https://stackoverflow.com/questions/4922943/test-from-shell-script-if-remote-tcp-port-is-open
// ref: https://gist.github.com/Khoulaiz/41b387883a208d6e914b
Boolean testConnectionSSL(host, port, timeout, SiteTestResults) {

    def timeout_with_mvn_cushion=timeout + 30
//    sh 'mvn -Djavax.net.debug=ssl:handshake -Dexec.mainClass=com.dettonville.api.TestSSLHandShake -Dexec.args="https://${targetHost}:${port} ${timeout}" exec:java'

    targetUrl=(port=="443") ? "https://${host}" : "https://${host}:${port}"
    echo "targetUrl=${targetUrl}"
    def mvnCmd="mvn -Djavax.net.debug=ssl:handshake -Dexec.mainClass=com.dettonville.api.TestSSLHandShake -Dexec.args=\"${targetUrl} ${timeout}\" exec:java"
//    def cmd="timeout ${timeout_with_mvn_cushion} ${mvnCmd}"
    def cmd="${mvnCmd}"

    return runTestScript("HTTPS: SSL Handshake", "sslhandshake-${host}-${port}", cmd, SiteTestResults, 6)
}

Boolean runTestScript(testCase, testId, script, SiteTestResults, order) {

    try {
        log_prefix="runTestScript(testId=${testId}):"
        def return_status = shellCommandOutput(testId,script)
        def ret = return_status['cmd_return_status']
        def filepath = return_status['filepath']

        if ( ret ) {
            echo "${log_prefix} Connection failed with retval=${ret}"

            TestResult pipeStep = new TestResult("${testCase}", order, "Test Failed on node [${env.NODE_NAME}]","FAILURE", filepath)
            SiteTestResults.addStep(pipeStep)

        } else {
            echo "${log_prefix} Connection success"

            TestResult pipeStep = new TestResult("${testCase}", order, "Test Succeeded on node [${env.NODE_NAME}]", "SUCCESS", filepath)
            SiteTestResults.addStep(pipeStep)

        }
        currentBuild.result = "SUCCESS"

    } catch (Exception err) {
        echo "${log_prefix} exception occurred [${err}] \n currentBuild.result=${currentBuild.result}"
        TestResult pipeStep = new TestResult("${testCase}", order, "Test Aborted on node [${env.NODE_NAME}]","FAILURE", null)
        SiteTestResults.addStep(pipeStep)
    }
    return currentBuild.result;
}


// ref: https://medium.com/garbage-collection/jenkins-pipelines-what-i-wish-i-knew-starting-out-6e3d4eb2ff5b
// ref: https://stackoverflow.com/questions/36547680/how-to-do-i-get-the-output-of-a-shell-command-executed-using-into-a-variable-fro

def shellCommandOutput(id, command) {
    return_status = [:]

    def runLabel=id.replace(' ', '-')
    runLabel=runLabel.replace(':', '_')
    def filename = "cmd-${runLabel}.log"
    def filepath = "${BUILD_URL}artifact/logs/${env.NODE_NAME}/${filename}"

    echo filename
    sh "echo ${command} > ${filename}"
    sh "echo Results: >> ${filename}"
    sh "echo \"\n\n######\" >> ${filename}"
    def retstat=sh (script: "${command} >> ${filename}", returnStatus: true)
    sh "echo \"\n\n######\" >> ${filename}"
    sh "echo return status=${retstat} >> ${filename}"

    return_status['cmd_return_status']=retstat
    return_status['filepath']=filepath

    return return_status
}

