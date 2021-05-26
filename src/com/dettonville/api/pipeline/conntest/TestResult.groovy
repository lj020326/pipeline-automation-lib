/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2018 Dettonville DevOps
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

import com.dettonville.api.pipeline.utils.logging.Logger

class TestResult implements Serializable {
    private String name
    private int order
    private String filepath
    private int status = 999
//    private String successRate
//    private float successRate = 0
    private Float successRate
    private int runcount
    public boolean requiredToConnect
    public boolean requiredToConnectSSL

    Logger log = new Logger(this)

    TestResult(Map testConfig) {

        log.debug("TestResult(): testConfig=${testConfig}")

        this.name = testConfig.testCase
        this.order = testConfig.testOrder
        this.runcount = testConfig.runcount
        this.requiredToConnect = testConfig.requiredToConnect
        this.requiredToConnectSSL = testConfig.requiredToConnectSSL

        this.filepath = testConfig.filepath
        this.status = testConfig.status
        this.successRate = testConfig.successRate

    }

    String toHtml() {

        String report = ""
        String bgColor="#99FF99"

        String message="PASS"
        if (this.runcount>1) message+=" (100%)"

        if ( this.status > 0 && this.successRate>0) {    // FAILURE & SOME SUCCESS
            bgColor="#FFED0D" // YELLOW
            message="FAIL"

//            if (this.runcount>1) message+=" (${this.successRate}%)"
            if (this.runcount>1) message+=" (${String.format("%.1f", this.successRate)}%)"
        }
        else if ( this.status > 0 ) {    // TOTAL FAILURE
            bgColor="#FFB2B2" // RED
            message="FAIL"
//            if (this.runcount>1) message+=" (${this.successRate}%)"
            if (this.runcount>1) message+=" (${String.format("%.1f", this.successRate)}%)"
        }
        if ( this.status < 0 ) {    // SKIPPED
            bgColor="#FFFFFF" // WHITE
            message="SKIP"
//            if (this.runcount>1) message+=" (${this.successRate}%)"
            if (this.runcount>1) message+=" (${String.format("%.1f", this.successRate)}%)"
        }

        report += "<td style='margin: 0;padding: 6px;border: 1px solid #ccc;text-align: left;background: ${bgColor};'>" +
                "<a href=\"${this.filepath}\">${message}</a>" +
                "</td>\n"

        return report
    }

    String createHeader() {
        return "<th style='margin: 0;padding: 6px;background: #333;color: white;font-weight: bold;border: 1px solid #ccc;text-align: auto;'>${this.name}</th>\n"
    }

    @Override
    String toString() {

        String report = ""
        String message="PASS"
        if (this.runcount>1) message+=" (100%)"

        if ( this.status ) {    // FAILURE
            message="FAIL"
//            if (this.runcount>1) message+=" (${String.format("%.1f", this.successRate)}%)"
            if (this.runcount>1) message+=" (${this.successRate}%)"
        }

        report += "test=${name} filepath=${this.filepath} status=${message}"

        return report
    }


}
