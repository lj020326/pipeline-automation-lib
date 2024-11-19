#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

Map config = [:]
config.enableBranchParam=true

config.runPostDeployTests = false
config.useSimulationMode = true
config.alwaysEmailList="ljohnson@dettonville.com, conor.dixon@dettonville.com"

runAppDeployEnvJob(config)


