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

//import groovy.json.JsonOutput

import com.dettonville.api.pipeline.utils.logging.Logger

import com.cloudbees.groovy.cps.NonCPS

/**
 * Static Class for helpful http URL site util methods.
 */
class SiteUtils {
    static Logger log = new Logger(this)

    @NonCPS
    static Map getHostInfo(String endpoint) {
        String logPrefix="getHostInfo(${endpoint}):"
        Map hostInfo = [:]
        hostInfo.scheme = "https"
        hostInfo.port = "443"
        hostInfo.context = ""

        try {
            if (endpoint.contains(":")) {
                List site_info = endpoint.split(":")
                log.debug("${logPrefix} endpoint=${endpoint} site_info.size()=${site_info.size()}")
                if (site_info.size() == 3) {
                    // assume the endpoint is one of the following variants:
                    // https://example.com:8443
                    // https://example.com:8443/something
                    // http://example.com:8080/something
                    hostInfo.scheme = site_info[0]
                    if (hostInfo.scheme=="http") {
                        hostInfo.port = "80"
                    }

                    hostInfo.host = site_info[1].split("//",2)[1]
                    hostInfo.port = site_info[2]

                    if (hostInfo.port.contains("/")) {
                        hostInfo.context = hostInfo.port.split("/",2)[1]
                        hostInfo.port = hostInfo.port.split("/")[0]
                    }

                } else if (site_info.size() == 2 && endpoint.contains("http")) {
                    log.debug("${logPrefix} endpoint contains http")
                    // assume the endpoint is one of the following variants:
                    // https://example.com
                    // http://example.com
                    // https://example.com/something
                    hostInfo.scheme = site_info[0]
                    if (hostInfo.scheme=="http") {
                        hostInfo.port = "80"
                    }

                    hostInfo.host = endpoint.split("//",2)[1]
                    if (hostInfo.host.contains("/")) {
                        hostInfo.context = hostInfo.host.split("/",2)[1]
                        hostInfo.host = hostInfo.host.split("/")[0]
                    }
                } else if (site_info.size() == 2 && !endpoint.contains("http")) {
                    log.debug("${logPrefix} endpoint does not contain http")
                    // assume the endpoint is one of the following variants:
                    // example.com:8443
                    // example.com:8443/something
                    hostInfo.scheme = "https"
                    hostInfo.host = site_info[0]
                    hostInfo.port = site_info[1]
                    log.debug("${logPrefix} hostInfo.host=${hostInfo.host} hostInfo.port=${hostInfo.port}")
                    if (hostInfo.port.contains("/")) {
                        hostInfo.context = hostInfo.port.split("/",2)[1]
                        hostInfo.port = hostInfo.port.split("/")[0]
                    } else if (hostInfo.host.contains("/")) {
                        hostInfo.context = hostInfo.host.split("/",2)[1]
                        hostInfo.host = hostInfo.host.split("/")[0]
                    }
                    if (['80','8080'].contains(hostInfo.port)) {
                        hostInfo.scheme = "http"
                    }
                }
            } else {
                // assume the endpoint is one of the following variants:
                // example.com
                // example.com/something
                hostInfo.scheme = "https"
                hostInfo.port = "443"
                hostInfo.host = endpoint
                if (hostInfo.host.contains("/")) {
                    hostInfo.context = hostInfo.host.split("/",2)[1]
                    hostInfo.host = hostInfo.host.split("/")[0]
                }
            }

            hostInfo.targetUrl="${hostInfo.scheme}://${hostInfo.host}:${hostInfo.port}/${hostInfo.context}"
            log.debug("${logPrefix} hostInfo=${hostInfo}")

        } catch (Exception err) {
            log.error("${logPrefix} exception occurred [${err}]")
            throw new Exception("${logPrefix} exception occurred: [${err}] ")
        }

        return hostInfo
    }

}
