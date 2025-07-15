#!/usr/bin/env groovy

@Library("pipelineAutomationLib")_

Map config = [:]
config.enableBranchParam=true

runATHParamWrapper(config)

