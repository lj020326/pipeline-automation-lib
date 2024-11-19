#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")_

import groovy.json.*

node ("QA-LINUX || PROD-LINUX") {

    List defaultSiteList = []
    defaultSiteList.add("stage2.dcapiadmin.dettonville.org/dcapiadmin/DCAPIAdmin.html")
    defaultSiteList.add("stage.iapp.dettonville.int/devzone/cms/")
    defaultSiteList.add("stage.sso.iapp.dettonville.int/dcapi/stage/dashboard")
    defaultSiteList.add("stage.developer.dettonville.org")
    defaultSiteList.add("internal.cicd.stage.developer.dettonville.int")
    defaultSiteList.add("stage.api.dettonville.org")
    defaultSiteList.add("stage.proxy.api.dettonville.org")
    defaultSiteList.add("sbx.stage.api.dettonville.org")

    //defaultSiteList.add("developer.dettonville.org")
    //defaultSiteList.add("api.dettonville.org:443")
    //defaultSiteList.add("sandbox.api.dettonville.org")
    //defaultSiteList.add("sandbox.proxy.api.dettonville.org")
    //defaultSiteList.add("stage.dcapiadmin.dettonville.org")
    //defaultSiteList.add("stage2.dcapiadmin.dettonville.org")
    //defaultSiteList.add("stage.api.dettonville.org")
    //defaultSiteList.add("stage.proxy.api.dettonville.org")
    //defaultSiteList.add("dcapiadmin.dettonville.org")
    //defaultSiteList.add("sbx.stage.api.dettonville.org")
    //defaultSiteList.add("stage.iapp.dettonville.int")
    //defaultSiteList.add("iapp.dettonville.int")
    //defaultSiteList.add("stage.developer.dettonville.org")

    List defaultNodeList = []
    //defaultNodeList.add("jnk2stl0")
    //defaultNodeList.add("jnk2stl1")
    defaultNodeList.add("jnk4stl1")
    defaultNodeList.add("jnk4stl2")
    defaultNodeList.add("jnk4stl3")

    String defaultSiteListStr = defaultSiteList.join(",")
    String defaultNodeListStr = defaultNodeList.join(",")

    properties([
            parameters([
                    //        string(defaultValue: "developer.dettonville.org,api.dettonville.org:443,sandbox.api.dettonville.org,sandbox.proxy.api.dettonville.org,stage.dcapiadmin.dettonville.org,stage2.dcapiadmin.dettonville.org,stage.api.dettonville.org,stage.proxy.api.dettonville.org,dcapiadmin.dettonville.org,sbx.stage.api.dettonville.org,stage.iapp.dettonville.int,iapp.dettonville.int,stage.developer.dettonville.org", description: "Specify site list (if no port specified, defaults to 443)\nE.g., 'stage.api.dettonville.org,stage.sandbox.api.dettonville.org:10443', etc", name: 'SiteList'),
                    string(defaultValue: "${defaultSiteListStr}", description: "Specify site list (if no port specified, defaults to 443)\nE.g., 'stage.api.dettonville.org,stage.sandbox.api.dettonville.org:10443', etc", name: 'SiteList'),
                    string(defaultValue: "${defaultNodeListStr}", description: "Specify node list\nE.g., 'jnk2stl0', 'ech-10-157-132-251,ech-10-157-133-41,ech-10-157-135-27', etc", name: 'NodeList'),
                    string(defaultValue: "5", description: "Specify number of runs for each test\nE.g., '5', '10', '30', etc ", name: 'Runcount'),
                    string(defaultValue: "5", description: "Specify the Connection Timeout\nE.g., '5', '10', '30', etc ", name: 'ConnTimeout'),
                    string(defaultValue: "", description: "Http Proxy Host:\nE.g. 'outboundproxy.dettonville.int'", name: 'HttpProxyHost'),
                    string(defaultValue: "", description: "Http Proxy Port:\nE.g. '15768'", name: 'HttpProxyPort'),
                    string(defaultValue: "", description: "Send email report to (comma delimited if list):\nE.g. 'ljohnson@dettonville.com, someelse@dettonville.com'", name: 'AlwaysEmailList'),
                    booleanParam(defaultValue: false, description: "Run Simulated Mode?", name: 'UseSimulationMode'),
                    booleanParam(defaultValue: false, description: "Debug Pipeline?", name: 'DebugPipeline'),
                    booleanParam(defaultValue: true, description: "Run nslookup?", name: 'RunNslookup'),
                    booleanParam(defaultValue: false, description: "Run dig?", name: 'RunDig'),
                    booleanParam(defaultValue: false, description: "Run traceroute?", name: 'RunTraceroute'),
                    booleanParam(defaultValue: false, description: "Run mtr?", name: 'RunMtr'),
                    booleanParam(defaultValue: false, description: "Run ping?", name: 'RunPing'),
                    booleanParam(defaultValue: true, description: "Run telnet?", name: 'RunTelnet'),
                    booleanParam(defaultValue: true, description: "Run curl?", name: 'RunCurl'),
                    booleanParam(defaultValue: true, description: "Run curl with cert verification?", name: 'RunCurlVerifyCerts'),
                    booleanParam(defaultValue: false, description: "Run openssl?", name: 'RunOpenSSL'),
                    booleanParam(defaultValue: false, description: "Run sslpoke?", name: 'RunSslpoke'),
                    booleanParam(defaultValue: true, description: "Run httpclienttest?", name: 'RunHttpClientTest'),
                    booleanParam(defaultValue: true, description: "Run sslhandshake?", name: 'RunSslhandshake'),
            ])
    ])

    List nodeList = (params.NodeList.contains(',')) ? params.NodeList.split(',').sort().collect { it.trim() } : [params.NodeList]
    List siteList = (params.SiteList.contains(',')) ? params.SiteList.split(',').sort().collect { it.trim() } : [params.SiteList]
    List testList = []

    if (params.RunNslookup) testList.add(["command": "nslookup"])
    if (params.RunDig) testList.add(["command": "dig"])
    if (params.RunTraceroute) testList.add(["command": "traceroute"])
    if (params.RunMtr) testList.add(["command": "mtr"])
    if (params.RunPing) testList.add(["command": "ping"])
    if (params.RunTelnet) testList.add(["command": "telnet"])
    if (params.RunCurl) testList.add(["command": "curl"])
    if (params.RunCurlVerifyCerts) testList.add(["command": "curl-verifycerts"])
    if (params.RunOpenSSL) testList.add(["command": "openssl"])
    if (params.RunSslpoke) testList.add(["command": "sslpoke"])
    if (params.RunHttpClientTest) testList.add(["command": "httpclienttest"])
    if (params.RunSslhandshake) testList.add(["command": "sslhandshake"])

    //echo "nodeList=${nodeList}"
    //echo "siteList=${siteList}"

    if (params.UseFullSiteList) {
        siteList = fullSiteList
    }

    String testNetworkName = "testSites"
    nodeConfigList = []
    nodeList.each { String nodeLabel ->
        //    echo "it=${it}"
//        String nodeLabel = it
        Map nodeConfig = [:]
        nodeConfig.nodeLabel = nodeLabel
        nodeConfig.network = "testNodes"
        nodeConfig.testNetwork = testNetworkName
        nodeConfigList.add(nodeConfig)
    }

    Map config = [:]
    config.nodeList = nodeConfigList

    config.testList = testList

    siteConfigList = []
    siteList.each { String endpoint ->
        //    echo "it=${it}"
//        String endpoint = it
        Map siteConfig = [:]
        siteConfig.endpoint = endpoint
        //    siteConfig.network = testNetworkName
        siteConfigList.add(siteConfig)
    }

    Map networks = [:]
    networks[testNetworkName] = [:]
    networks[testNetworkName].siteList = siteConfigList

    config.networks = networks
    config.runcount = params.Runcount
    config.timeout = params.ConnTimeout
    config.useSimulationMode = params.UseSimulationMode
    config.debugPipeline = params.DebugPipeline
    config.alwaysEmailList = params.AlwaysEmailList

    config.httpProxyHost = params.HttpProxyHost
    config.httpProxyPort = params.HttpProxyPort

    List recipientList = (config.alwaysEmailList != "") ? (config.alwaysEmailList.contains(",")) ? config.alwaysEmailList.tokenize(',') : [config.alwaysEmailList] : []
    recipientList.add("ljohnson@dettonville.com")
    config.alwaysEmailList = recipientList.join(",")

    //echo "config=${config}"
    echo "config=${printToJsonString(config)}"

    runConnectivityTest(config)

}

String printToJsonString(Map config) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(config))
}

