#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.com, conor.dixon@dettonville.com"

runAppDeployEnvJob(config)
