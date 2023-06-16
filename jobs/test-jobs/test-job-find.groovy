#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger


node ('DEVCLD-LIN7') {
    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    log.info("checkout test repo for find")
    checkout scm: [
        $class: 'GitSCM',
        branches: [[name: "main"]],
        userRemoteConfigs: [[credentialsId: 'dcapi_ci_vcs_user', url: 'https://gitrepository.dettonville.int/stash/scm/api/infra-test.git']]
    ]

    log.info("test find")
    sh 'find . -name *.\\*ml -type f -printf "%TY-%Tm-%Td %TH:%TM:%.2Ts %p\\n"'

}

