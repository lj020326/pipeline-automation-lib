#!/usr/bin/env groovy

@Library("pipeline-automation-lib@beta")_

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.com"

runATHParamWrapper(config)

