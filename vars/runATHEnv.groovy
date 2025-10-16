#!/usr/bin/env groovy


import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map inConfig=[:]) {

    Map config = inConfig.clone()

    def ymlTestEnvStr = """
---
testCaseConfigs:
    apivalidation: 
        browserstackBrowser: "httprestassuredclient"
        webPlatform: "API"
        testSuiteName: "API Tests"
        metaFilterTags: "+api"

    chrome: 
        browserstackBrowser: "chrome"
        browserstackChromeVersion: "74"
        browserstackWebOS: Windows
        browserstackWebOSVersion: "10"
        
    firefox: 
        browserstackBrowser: "firefox"
        browserstackFirefoxVersion: "66"
        browserstackWebOS: Windows
        browserstackWebOSVersion: "10"

    edge: 
        browserstackBrowser: "edge"
        browserstackEdgeVersion: "17.0"
        browserstackWebOS: Windows
        browserstackWebOSVersion: "10"

    ie: 
        browserstackBrowser: "ie"
        browserstackIEVersion: "11.0"
        browserstackWebOS: Windows
        browserstackWebOSVersion: "10"
        browserstackNetworkLogs: false

    safari:
        browserstackBrowser: "safari"
        browserstackSafariVersion: "12.0"
        browserstackWebOS: OSX
        browserstackWebOSVersion: "Mojave"

    locationsapivalidation: 
        browserstackBrowser: "chrome"
        metaFilterTags: "+locations"
        parallelRunCount: 4
        testSuiteName: "Location API Tests"
        alwaysEmailList: "dcapi_qa@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"

appTestSuites:
    HEALTHCHECKS/HOURLY:
        metaFilterTags: "+TestId TC1025418"
        parallelRunCount: 1
        cronCfg: "H(0-5) * * * *"
        collectTestResults: true
        maxTestResultsHistory: 24
        alwaysEmailList: "lee.johnson@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"

    HEALTHCHECKS/DAILY/API-GRID:
        metaFilterTags: "+APIGridProduction"
        parallelRunCount: 5
        cronCfg: "H 2 * * *"
        collectTestResults: true
        maxTestResultsHistory: 30
        alwaysEmailList: "lee.johnson@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"

    SIMPLETEST: 
        metaFilterTags: "+TestId TC1025418"
        browserstackBrowser: "chrome"
        parallelRunCount: 1
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"

    SMOKE: 
        metaFilterTags: "+smoke"
        parallelRunCount: 4
        waitingTime: 4
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"
        
    SANITY: 
        metaFilterTags: "+sanity"
        parallelRunCount: 12
        waitingTime: 10
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"

    API: 
        testSuiteName: "API Tests"
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"

    REGRESSION:
        metaFilterTags: "+regression"
        parallelRunCount: 12
        waitingTime: 10
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"

    GWCALLS: 
        testSuiteName: "GW Calls"
        metaFilterTags: "+gw"
        parallelRunCount: 1
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"
                
    LIVE-CHAT: 
        metaFilterTags: "+chat"
        parallelRunCount: 1
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
        changedEmailList: "dcapi_qa@dettonville.com"

    PRODUCT-OWNER: 


appEnvironments:
    DEV: 
        jenkinsRunTestsLabel: "QA-LINUX"
        athGitBranch: "develop"
        deployJobEnvName: "Dev"
        useBrowserstackLocalProxy: true
        alwaysEmailList: "dcapi_qa@dettonville.com"
    
    DEV_CLOUD: 
        jenkinsRunTestsLabel: "DEVCLD-LIN7"
        athGitBranch: "develop"
        useBrowserstackLocalProxy: false
#        browserstackProxyHost: "ech-10-170-129-105.dettonville.int"
#        browserstackProxyPort: "1080"
#        browserstackProxyHost: "ech-10-170-129-41.dettonville.int"
#        browserstackProxyPort: "80"
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
    STAGE:
        jenkinsRunTestsLabel: "QA-LINUX"
        athGitBranch: "develop"
        deployJobEnvName: "Stage"
        useBrowserstackLocalProxy: true
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
    STAGE_EXTERNAL:
        jenkinsRunTestsLabel: "DEVCLD-LIN7"
        athGitBranch: "develop"
        deployJobEnvName: "Stage"
        useBrowserstackLocalProxy: false
        useExecEnvJenkins: false
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
    PROD_KSC:
        jenkinsRunTestsLabel: "PROD-LINUX"
        athGitBranch: "main"
        useBrowserstackLocalProxy: true
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
    PROD_STL:
        jenkinsRunTestsLabel: "PROD-LINUX"
        athGitBranch: "main"
        useBrowserstackLocalProxy: true
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
    PROD_COPRO:
        jenkinsRunTestsLabel: "PROD-LINUX"
        athGitBranch: "main"
        useBrowserstackLocalProxy: true
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
    PROD_EXTERNAL:
        jenkinsRunTestsLabel: "DEVCLD-LIN7"
        athGitBranch: "main"
        useExecEnvJenkins: false
        useBrowserstackLocalProxy: false
#        changedEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com"
        alwaysEmailList: "dcapi_qa@dettonville.com, lee.johnson@dettonville.com, SIT-engineer@dettonville.com"
    
"""

    Map ymlConfig = readYaml text: ymlTestEnvStr
    List multiPlatformNames = ["multiplatform","multi-platform"]

    config.testCase = (config.testCase ?: config.browserstackBrowser).toLowerCase()

    log.debug(" 1) if multiplatform - set testgroups config")
    if (multiPlatformNames.contains(config.testCase)) {
        config = MapMerge.merge(getMultiPlatformSettings(), config)
    }
    log.debug("1) config=${JsonUtils.printToJsonString(config)}")

    log.debug("2) apply test case settings")
    Map testCaseSettings = ymlConfig.testCaseConfigs.get(config.testCase, [:])
    config = MapMerge.merge(testCaseSettings, config)
    log.debug("2) config=${JsonUtils.printToJsonString(config)}")

    log.debug("3) apply test suite settings")
    if (config?.testSuite) {
        Map testSuiteSettings = ymlConfig.appTestSuites.get(config.testSuite.toUpperCase(), [:])
        testSuiteSettings = (testSuiteSettings) ? testSuiteSettings : [:]
        config=MapMerge.merge(testSuiteSettings, config)
        log.debug("3) config=${JsonUtils.printToJsonString(config)}")
    }
    log.debug("3) config=${JsonUtils.printToJsonString(config)}")

    log.debug("4) apply appenv settings")
    Map envSettings = ymlConfig.appEnvironments.get(config.appEnvironment, [:])
    config=MapMerge.merge(envSettings, config)
    log.debug("4) config=${JsonUtils.printToJsonString(config)}")

    if (config?.cronCfg) {
        properties([pipelineTriggers([cron("${config.cronCfg}")])])
    }

    config.testSuiteName = config.testSuiteName ?: config.testSuite

    log.info("config=${JsonUtils.printToJsonString(config)}")

    if (config?.testSuite && config.testSuite=="API") {
        runApiTests(config)
    } else {
        runATH(config)
    }

}


Map getMultiPlatformSettings() {
    def ymlTestGroupsStr = """
---
#groupWaitTime: 6
groupWaitTime: 3
parallelRunCount: 2
browserstackBrowser: "Multi-Platform"

testGroups:
    - name: Windows-Chrome
      browserstackBrowser: Chrome
      testCases: 
#        - browserstackChromeVersion: "76"
        - browserstackChromeVersion: "74"
#        - browserstackChromeVersion: "73"
#        - browserstackChromeVersion: "72"
#        - browserstackChromeVersion: "71"
#        - browserstackChromeVersion: "70"

    - name: Windows-Edge
      browserstackBrowser: Edge
      testCases: 
        - browserstackEdgeVersion: "17.0"
#        - browserstackEdgeVersion: "18.0"

    - name: Windows-IE
      browserstackBrowser: IE
      browserstackNetworkLogs: false
      testCases: 
        - browserstackIEVersion: "11.0"

    - name: Windows-Firefox
      browserstackBrowser: Firefox
      testCases: 
        - browserstackFirefoxVersion: "66"
#        - browserstackFirefoxVersion: "65"
#        - browserstackFirefoxVersion: "64"
#        - browserstackFirefoxVersion: "60"

    - name: MacOS-Safari
      browserstackBrowser: Safari
      browserstackWebOS: OSX
      browserstackWebOSVersion: "Mojave"
      testCases: 
        - browserstackSafariVersion: "12.0"
#        - browserstackSafariVersion: "11.0"

#    - name: iPhone-Safari
#      browserstackBrowser: iphone
#      testCases: 
#        - browserstackWebOS: "iPhone X"
#          browserstackWebOSVersion: "11"
#        - browserstackWebOS: "iPhone 7"
#          browserstackWebOSVersion: "10.3"

"""

    Map config = readYaml text: ymlTestGroupsStr
//    Map config=ymlConfig + inConfig

    return config
}
