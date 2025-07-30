#!/usr/bin/env groovy

@Library("pipelineAutomationLib@develop")

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils

Logger.init(this, LogLevel.INFO)
//Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

String configYmlStr = '''
---
#appEnvironment: "PROD_EXTERNAL"
appEnvironment: "STAGE_EXTERNAL"
#appEnvironment: "STAGE"
#testSuite: "SMOKE"
#metaFilterTags: "+smoke"
metaFilterTags: "+TestId TC1025418"
#browserstackBrowser: "Firefox"
browserstackBrowser: "MultiPlatform"

athGitBranch: "develop"

#useBrowserstackLocalAgent: false
#useBrowserstackProxy: true
#useBrowserstackLocalProxy: false

alwaysEmailList: "ljohnson@dettonville.com"

#checkIfDeployJobsRan: false

#useDryRun: true
#parallelRunCount: 2
#parallelRunCount: 4
parallelRunCount: 1

#logLevel: DEBUG
#debugPipeline: true
'''

// set job config settings
Map config = readYaml text: configYmlStr

log.info("config=${JsonUtils.printToJsonString(config)}")

runATHEnv(config)
