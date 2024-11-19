#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

Map config = [:]
config.enableBranchParam=true
config.jobBaseFolderLevel = 3

config.runPostDeployTests = false
config.useSimulationMode = true
config.alwaysEmailList="ljohnson@dettonville.com"

config.enabledParamList = ['artifactVersion','runPostDeployTests','useSimulationMode','debugReleaseScript']

runAppDeployEnvJob(config)


