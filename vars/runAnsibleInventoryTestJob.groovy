#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

def call(Map config=[:]) {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    config.testTagsParam = config.get('testTagsParam',[])
    config.testType = config.get('testType','module')

//     properties([
//         disableConcurrentBuilds()
//     ])

    config.ansibleInstallation = config.get('ansibleInstallation',"ansible-venv")
//     config.ansibleInventory = "${env.JOB_NAME.split('/')[-3]}"

    //     ref: https://stackoverflow.com/questions/62213910/run-only-tasks-with-a-certain-tag-or-untagged
    // config.ansibleTags = config.get('ansibleTags',"untagged,${env.JOB_NAME.split('/')[-2]}")

    log.info("config=${JsonUtils.printToJsonString(config)}")

    runAnsibleInventoryTests(config)

    log.info("finished")

}

