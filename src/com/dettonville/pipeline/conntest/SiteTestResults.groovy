/*-
 * #%L
 * apps.dettonville.org
 * %%
 * Copyright (C) 2025 Dettonville
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

package com.dettonville.pipeline.conntest

import com.cloudbees.groovy.cps.NonCPS

import java.text.SimpleDateFormat
import com.dettonville.pipeline.utils.logging.Logger

/**
 * Class for keeping tabs on the pipeline progression.
 * It can then be used to create a summary at the of the pipeline via its toString method.
 *
 * The createSummaryHtml method generates a html summary page.
 */
class SiteTestResults implements Serializable {
//    private String name
//    private String node
    public String endpoint
    public String network
    public String description
    public String proxyUrl
    private int order
    public int status
    public String nodeLabel
    public String nodeName
    private String startTime
    private String endTime

    public Map hostInfo

    Logger log = new Logger(this)

    def testResults = []
    def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm")

    SiteTestResults(Map siteConfig) {
        log.info("started for siteConfig.targetUrl=${siteConfig.targetUrl}")
        log.debug("siteConfig=${siteConfig}")

        this.endpoint = siteConfig.endpoint
//        this.description = "Connect to ${siteConfig.endpoint}"
        this.description = "Connect to ${siteConfig.targetUrl}"

        Map hostInfo=SiteUtils.getHostInfo(endpoint)
        this.hostInfo=hostInfo

        if (siteConfig.httpProxyHost) {
            String proxyUrl="${siteConfig.httpProxyHost}"
            if (siteConfig?.httpProxyPort) {
                proxyUrl += ":${siteConfig.httpProxyPort}"
            }
            this.proxyUrl = proxyUrl
        }

        this.order = siteConfig.siteOrder
        this.nodeLabel = siteConfig.nodeLabel
        this.nodeName = siteConfig.nodeName
        this.network = siteConfig.network
        this.status = 0
        Date date = new Date()
        this.startTime = dateFormat.format(date)
    }

    /**
     * Add a TestResult to the list.
     * @param step
     */
    void addStep(TestResult step) {
//        def date = new Date()
//        this.startTime = dateFormat.format(date)

        Date date = new Date()
        this.endTime = dateFormat.format(date)

        this.status+=(step.status) ? step.status : 1

        log.info("this.endTime=${this.endTime} step=${step}")
        testResults.add(step)
    }

    /**
     * Add a TestResult to the list.
     * @param step
     */
    void addStep(Map stepData) {

        TestResult step = new TestResult(stepData)
        this.addStep(step)
//        this.status+=(stepData.status) ? stepData.status : 1
//        this.testResults.add(step)
    }

    @NonCPS
    List sortList(list) {
        // ref: http://www.tothenew.com/blog/groovy-sort-list-of-objects-on-the-basis-of-more-than-one-field/
        list.sort{it.order}
        return list
    }

    @Override
    String toString() {

        testResults = sortList(testResults)

//        testResults.eachWithIndex { it, i -> println "stage: order=${it.order} name=${it.name} idx=${i}"}

//        def tests=0
        int failedSSLConnTests=0
        int sslTestCount=0
        int failedConnTests=0
        int testCount=0
        String stepsString = ""
        for (TestResult step : testResults) {
            stepsString += " [${step}]"
            failedSSLConnTests += (step.requiredToConnectSSL) ? ((step.status) ? 1 : 0) : 0
            sslTestCount += (step.requiredToConnectSSL) ? 1 : 0

            failedConnTests += (!step.requiredToConnectSSL) ? ((step.status) ? 1 : 0) : 0
            testCount += (!step.requiredToConnectSSL) ? 1 : 0
        }

        String httpsResult=""

        log.info("nodeLabel=${nodeLabel} network=${network} endpoint=${endpoint} testCount=${testCount} failedConnTests=${failedConnTests} sslTestCount=${sslTestCount} failedSSLConnTests=${failedSSLConnTests}")
        if (hostInfo.scheme=="http") {
            httpsResult = "NOT APPLICABLE"
        } else if (sslTestCount>0) {
            if (failedSSLConnTests == 0) {
                httpsResult = "YES"
            } else if (failedSSLConnTests < sslTestCount) {
                httpsResult = "MAYBE"
            } else {
                httpsResult = "NO"
            }
        } else if (testCount>0) {
            if (failedConnTests == 0) {
                httpsResult = "MAYBE"
            } else if (failedConnTests < testCount) {
                httpsResult = "MAYBE"
            } else {
                httpsResult = "NO"
            }
        }

        String report="description: ${description}"
        report+=" startTime: ${startTime}"
        report+=" endTime: ${endTime}"
        report+=" httpsResult: ${httpsResult}"

        report+=" ${stepsString}"
        report+="\n"

        return report
    }

    String toHtml() {
        String stepsString = ""

        // ref: http://www.tothenew.com/blog/groovy-sort-list-of-objects-on-the-basis-of-more-than-one-field/
        //this.testResults.sort(it.order)
//        testResults.sort{x,y->
//            x.order <=> y.order
//        }
        testResults = sortList(testResults)

//        testResults.eachWithIndex { it, i -> println "stage: order=${it.order} name=${it.name} idx=${i}"}

//        def tests=0
        int failedSSLConnTests=0
        int sslTestCount=0
        int failedConnTests=0
        int testCount=0
        for (TestResult step : testResults) {
            stepsString += step.toHtml()
            failedSSLConnTests += (step.requiredToConnectSSL) ? ((step.status) ? 1 : 0) : 0
            sslTestCount += (step.requiredToConnectSSL) ? 1 : 0

            failedConnTests += (!step.requiredToConnectSSL) ? ((step.status) ? 1 : 0) : 0
            testCount += (!step.requiredToConnectSSL) ? 1 : 0
        }

        def httpsResult=""

        log.debug("nodeLabel=${nodeLabel} network=${network} endpoint=${endpoint} testCount=${testCount} failedConnTests=${failedConnTests} sslTestCount=${sslTestCount} failedSSLConnTests=${failedSSLConnTests} proxyUrl=${proxyUrl}")
        if (hostInfo.scheme=="http") {
            httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFFFFF;'>" +
                    "<b>NOT APPLICABLE</b>" +
                    "</td>\n"
        } else if (sslTestCount>0) {
            if (failedSSLConnTests == 0) {
                httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #99FF99;'>" +
                        "<b>YES</b>" +
                        "</td>\n"
            } else if (failedSSLConnTests < sslTestCount) {
                httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFED0D;'>" +
                        "<b>MAYBE</b>" +
                        "</td>\n"
            } else {
                httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFB2B2;'>" +
                        "<b>NO</b>" +
                        "</td>\n"
            }
        } else if (testCount>0) {
            if (failedConnTests == 0) {
                httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #99FF99;'>" +
                        "<b>MAYBE: need to run SSL Tests to determine</b>" +
                        "</td>\n"
            } else if (failedConnTests < testCount) {
                httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFED0D;'>" +
                        "<b>MAYBE</b>" +
                        "</td>\n"
            } else {
                httpsResult = "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFB2B2;'>" +
                        "<b>NO</b>" +
                        "</td>\n"
            }
        }

        String report = "<tr style='margin: 0;padding: 0;'>"
        if (proxyUrl) {
            report+="<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFFFFF;'><b>${description} *</b>"
            report+="<br>*via proxy ${proxyUrl}</td>"
        } else {
            report+="<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFFFFF;'><b>${description}</b></td>"
        }
        report+="<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFFFFF;'>${startTime}</td>"
        report+="<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFFFFF;'>${endTime}</td>"
        report+=httpsResult
        report+="<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: #FFFFFF;'><b></b></td>"

        report+=stepsString
        report+="</tr>\n"

        return report
    }

    String createHeader() {

        def report="<table style='margin: 0;padding: 0;table-layout: fixed;border-collapse: collapse;font: 11px/1.4 Trebuchet MS;'>\n"
        report+="<thead style='margin: 0;padding: 0;'>\n"
        report+="<tr style='margin: 0;padding: 0;'>\n"
        report+="<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'>TCID</th>\n"
        report+="<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'>START TIME</th>\n"
        report+="<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'>END TIME</th>\n"
        report+="<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'>HTTPS Will Work?</th>\n"
        report+="<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'></th>\n"

//        this.testResults.sort{x,y->
//            x.order <=> y.order
//        }
        testResults = sortList(testResults)

        testResults.eachWithIndex { it, i -> println "stage: order=${it.order} name=${it.name} idx=${i}"}

        String stepsString = ""
        for (TestResult step : testResults) {
            report += step.createHeader()
        }

//        report+="<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'>Status</th>\n"

        report+="</tr>\n"
        report+="</thead><tbody style='margin: 0;padding: 0;'>\n"
        return report
    }

}
