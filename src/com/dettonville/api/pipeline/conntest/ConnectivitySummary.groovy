/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
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

package com.dettonville.api.pipeline.conntest

import groovy.json.JsonOutput

import java.text.SimpleDateFormat
import com.dettonville.api.pipeline.utils.logging.Logger

import com.cloudbees.groovy.cps.NonCPS

/**
 * Class for keeping tabs on the pipeline progression.
 * It can then be used to create a summary at the of the pipeline via its toString method.
 *
 * The createSummaryHtml method generates a html summary page.
 */
class ConnectivitySummary implements Serializable {
    String result
    String previousResult
//    long duration
    String duration
    Date start

    Logger log = new Logger(this)

    Map pipelineNodes = [:]

    def headerResults

    def stages
    Map config

    ConnectivitySummary(stages, config){
        this.stages = stages
        this.config = config
    }

    /**
     * Add a TestResultList to the list.
     * @param step
     */
    void addSiteTestResultsList(List siteResultsList) {
        String logPrefix = "ConnectivitySummary.addSiteTestResultsList():"
        for (SiteTestResults siteResults : siteResultsList) {
            addSiteTestResults(siteResults)
        }
    }

    /**
     * Add a TestResult to the list.
     * @param step
     */
    void addSiteTestResults(SiteTestResults siteResults) {
        String logPrefix = "ConnectivitySummary.addSiteTestResults():"
        if (! headerResults) {
            headerResults=siteResults
        }

        log.info("${logPrefix} siteResults=${siteResults}")
//        def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm")
//        def date = new Date()
//        siteResults.endTime = dateFormat.format(date)

        String endpoint = siteResults.endpoint
        String nodeLabel = siteResults.nodeLabel
        String nodeName = siteResults.nodeName
        List siteResultsList=[]
        if (pipelineNodes[nodeLabel]) {
            siteResultsList=pipelineNodes[nodeLabel]
            siteResultsList.add(siteResults)
        } else {
            siteResultsList.add(siteResults)
        }

        stages.echo "ConnectivitySummary.addSiteTestResults(): endpoint=${endpoint} nodeLabel=${nodeLabel} nodeName=${nodeName} siteResults.order=${siteResults.order} siteResultsList.size=${siteResultsList.size()}"
        pipelineNodes[nodeLabel]=siteResultsList
    }

    @NonCPS
    List sortList(List list) {
        // ref: http://www.tothenew.com/blog/groovy-sort-list-of-objects-on-the-basis-of-more-than-one-field/
        list.sort{it.order}
        return list
    }

    @Override
    String toString() {
        String logPrefix = "ConnectivitySummary.toString():"
        stages.echo "${logPrefix} started"

        String report=""
        def nodeList=(pipelineNodes.keySet()).sort()
        stages.echo "${logPrefix} nodeList=${nodeList}"

        for (String nodeLabel : nodeList) {
            report+="Node: ${node}\n"

            stages.echo "${logPrefix} nodeLabel=${nodeLabel}"
            def siteResultsList=pipelineNodes[nodeLabel]

            siteResultsList = sortList(siteResultsList)
            siteResultsList.eachWithIndex { siteResults, i ->
                stages.echo "${logPrefix} idx=${i} siteResults=${siteResults}\n"
                report += "siteResults=${siteResults}\n"
            }
            report += "***********\n"
        }
        return report
    }

    String toHtml() {
        String logPrefix = "ConnectivitySummary.toHtml():"
        stages.echo "${logPrefix} started"

        def report=""
        def nodeList=(pipelineNodes.keySet()).sort()
        log.debug("${logPrefix} nodeList=${nodeList}")

        for (String nodeLabel : nodeList) {
//            report+="<br><br><h2 style=\"background-color:#fff; margin-bottom:10px; padding:5px 10px; border-color:#ffcd13\" bgcolor=\"#ffffff\">Node: ${nodeLabel}</h2><br>" + createReportHeader()
            report+="<br><br><h2 style=\"background-color:#fff; margin-bottom:10px; padding:5px 10px; border-color:#ffcd13\" bgcolor=\"#ffffff\">Node: ${nodeLabel}</h2><br>${headerResults.createHeader()}"

            log.debug("${logPrefix} nodeLabel=${nodeLabel}")
            def siteResultsList=pipelineNodes[nodeLabel]

            siteResultsList = sortList(siteResultsList)

            siteResultsList.eachWithIndex { it, i -> stages.echo "siteResults: order=${it.order} endpoint=${it.endpoint} idx=${i}"}

            for (SiteTestResults siteResults : siteResultsList) {
                def siteResultsString=siteResults.toHtml()
                report+=siteResultsString
                log.debug("${logPrefix} nodeLabel=${nodeLabel} siteResults.order=${siteResults.order} siteResultsString=${siteResultsString}")
            }
            report += "</tbody></thead></table>"
        }
        return report
    }

    private void processEndOfPipeline(){
        result = stages.currentBuild.result
        if (result == null) {
            result = 'SUCCESS'
        }
        start = new Date((Long)stages.currentBuild.timeInMillis)
//        duration = (System.currentTimeMillis() - stages.currentBuild.startTimeInMillis) / 1000
        duration = "${stages.currentBuild.durationString.replace(' and counting', '')}"

        if (stages.currentBuild.previousBuild != null) {
            previousResult = stages.currentBuild.previousBuild.result
        } else {
            previousResult = "Unknown"
        }
    }

    /**
     * Writes the summary information into a html.
     *
     * ref: http://www.testautomationguru.com/qtpuft-sending-out-email-with-test-results-using-jenkins/
     */
//    @NonCPS
    void createSummaryHtml() {
        processEndOfPipeline()

//        stages.echo "Found ${SiteTestResults.size()} stages"
        String reportBody = toHtml()

        stages.writeFile encoding: 'UTF-8', file: 'emailable.index.html', text: getHtmlReport(reportBody, true)
        stages.writeFile encoding: 'UTF-8', file: 'index.html', text: getHtmlReport(reportBody)
    }

    String getHtmlReport(String reportBody, boolean emailable=false) {
        String iframe = (!emailable) ? "<iframe src=\"graph.html\" style=\"border:none;height:600px;width:1200px;\" scrolling=\"no\"></iframe>" : ""

        def timeout = (config.Timeout) ? config.Timeout : 5

        return """
            <!DOCTYPE html>
            <html lang="en">
                <body>
                    <p>
                        <h1>Connectivity Test Summary</h1>
                        <ul>
                            <li>Result: ${result}</li>
                            <li>Previous Result: ${previousResult}</li>
                            <li>Build: #${stages.currentBuild.number}</li>
                            <li>Scheduled at: ${start}</li>
                            <li>Duration: ${duration}</li>
                            <li>Description: ${stages.currentBuild.description}</li>
                            <li>Node List: ${pipelineNodes.keySet()}</li>
                            <li>Timeout: ${timeout}</li>
                        </ul>
                    </p>
                    ${iframe}
                    <p>
                        ${reportBody}
                    </p>
                </body>
            </html>
            """
    }

//    @NonCPS
    String createReportHeader() {

        def report=headerResults.createHeader()

        return report
    }

    String getNodeName(nodeNetwork,nodeName) {
//        return "${nodeNetwork}|${nodeName}"
        return "${nodeName}.${nodeNetwork}"
    }

    /**
     * Writes the summary information into a html.
     *
     * ref: http://www.testautomationguru.com/qtpuft-sending-out-email-with-test-results-using-jenkins/
     */
    Map createJsonReport() {

        Map resultMap = [:]

        String logPrefix="ConnectivitySummary.createJsonReport():"
        List nodeList=(pipelineNodes.keySet()).sort()
        log.info("${logPrefix} nodeList=${nodeList}")
//        stages.echo "${logPrefix} config=${config}"

        resultMap.nodes=[]
        // print node list
        config.nodeList.eachWithIndex { node, i ->
            println "${logPrefix} i=${i} node=${node}"
            log.info("${logPrefix} i=${i} node=${node}")

            node.name=getNodeName(node.network,node.nodeLabel)
//            node.icon="./images/router.png"
            node.icon="./images/group1.png"
            resultMap.nodes.add(node)
        }

        // print add target sites to node list
        config.networks.each { networkName, network ->
            log.info("${logPrefix} networkName=${networkName}, network=${network}")
            network.siteList.eachWithIndex { nodeConfig, i ->
                Map node = network.findAll { it.key != 'siteList' } + nodeConfig
                String endpoint = nodeConfig.endpoint
//                List site_info = endpoint.split(":")
//                String targetName = site_info[0]

                Map hostInfo=SiteUtils.getHostInfo(endpoint)
                String targetName = "${hostInfo.host}:${hostInfo.port}"

                log.info("${logPrefix} hostInfo=${hostInfo}")

                node.network = node.network ?: networkName
                node.name=getNodeName(node.network,targetName)
                log.info("${logPrefix} node.network=${node.network}, nodeName=${targetName}, node.name=${node.name}")
                if (network == "external") {
                    node.icon="./images/ix.png"
                } else {
                    node.icon="./images/group2.png"
                }
//                node.icon="./images/ix.png"
                if (resultMap.nodes.contains(node)) {
                    log.info("${logPrefix} i=${i} node=${node} - node already found, skipping")
                } else {
                    log.info(" i=${i} node=${node} - node not found, adding");
                    resultMap.nodes.add(node)
                }

//                stages.echo "${logPrefix} i=${i} node=${node}"
            }
        }

        log.info("${logPrefix} adding links...")
        resultMap.links=[]
        config.nodeList.eachWithIndex { source, i ->
            log.info("${logPrefix} i=${i} source=${source}")

            List siteResultList=pipelineNodes[source.nodeLabel]

            siteResultList = sortList(siteResultList)
//            siteResultList.eachWithIndex { Map target, i2 ->
            siteResultList.eachWithIndex { SiteTestResults target, i2 ->
                log.info("${logPrefix} siteResult: i2=${i2}")
                log.info("${logPrefix} siteResult: target=${target}")
                Map link=[:]

                String endpoint = target.endpoint
//                List site_info = endpoint.split(":")
//                String targetName = site_info[0]
                Map hostInfo=SiteUtils.getHostInfo(endpoint)
//                String targetName = hostInfo.host
                String targetName = "${hostInfo.host}:${hostInfo.port}"

                link.source=getNodeName(source.network,source.nodeLabel)
                link.target=getNodeName(target.network,targetName)
                resultMap.links.add(link)

            }
        }

        return resultMap
    }

}
