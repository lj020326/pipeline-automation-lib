#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")_

node ("QA-LINUX || PROD-LINUX") {

    Map config = [:]
    config.globalConfigFile = "connectivity-check-defaults-test.yml"
    //config.useSimulationMode=true
    config.debugPipeline = true

    runConnectivityTest2(config)

}
