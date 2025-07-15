#!/usr/bin/env groovy

@Library("pipelineAutomationLib@beta")_

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.com"

runATHParamWrapper(config)

