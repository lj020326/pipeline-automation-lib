#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

//import com.dettonville.api.pipeline.utils.BuildApiUtils
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.ath.EmailUtils


Logger.init(this, LogLevel.INFO)
//Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

EmailUtils emailUtils = new EmailUtils(this)

List paramList = [string(defaultValue: "", description: "Specify email recipient(s) (comma delimited)", name: 'alwaysEmailList')]

properties([parameters(paramList)])

String summaryText = '''
Serenity report generated 29-08-2019 08:07
3 test scenarios 

Passed:             3
Pending             0
Failed:             0
Failed with errors: 0
Compromised:        0
Pending:            0
Ignored:            0
Skipped:            0
'''

Map config = [:]

//config.debugPipeline = true

config.emailFrom = "DCAPI.TestAutomation@dettonville.org"
config.alwaysEmailList = "ljohnson@dettonville.org"

// copy immutable params maps to mutable config map
params.each { key, value ->
    if (value!="") {
        config[key]=value
    }
}

log.info("config=${JsonUtils.printToJsonString(config)}")

emailUtils.sendEmailNotification(config, config.alwaysEmailList)

node ('QA-LINUX || PROD-LINUX') {
    // Write an useful file, which is needed to be archived.
    writeFile file: "summary.txt", text: summaryText
    emailUtils.sendEmailTestReport(config, config.alwaysEmailList)
}
