#!/usr/bin/env groovy

@Library("pipelineAutomationLib@develop")_

node ("QA-LINUX || PROD-LINUX") {

    runConnectivityTest()

}

