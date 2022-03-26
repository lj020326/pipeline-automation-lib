#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")_

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.org"

runATHParamWrapper(config)

