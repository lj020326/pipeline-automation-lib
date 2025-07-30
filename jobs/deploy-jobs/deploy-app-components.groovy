#!/usr/bin/env groovy

@Library("pipelineAutomationLib")

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.com, conor.dixon@dettonville.com"

runAppDeployEnvJob(config)
