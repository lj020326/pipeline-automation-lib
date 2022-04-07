#!/usr/bin/env groovy

@Library("pipeline-automation-lib@beta")

Map config = [:]
config.enableBranchParam=true
config.alwaysEmailList="ljohnson@dettonville.org"

runAppDeployEnvJob(config)


