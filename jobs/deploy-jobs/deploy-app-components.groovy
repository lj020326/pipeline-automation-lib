#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.org, conor.dixon@dettonville.org"

runAppDeployEnvJob(config)
