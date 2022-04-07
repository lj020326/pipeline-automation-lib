#!/usr/bin/env groovy

@Library("pipeline-automation-lib")_

Map config = [:]
config.enableBranchParam=true

runATHParamWrapper(config)

