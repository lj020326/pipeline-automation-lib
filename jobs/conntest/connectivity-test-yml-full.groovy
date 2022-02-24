#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")_

node ("QA-LINUX || PROD-LINUX") {

    runConnectivityTest()

}

