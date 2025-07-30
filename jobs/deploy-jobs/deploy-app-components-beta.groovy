#!/usr/bin/env groovy

@Library("pipelineAutomationLib@beta")

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.com"

runAppDeployEnvJob(config)


