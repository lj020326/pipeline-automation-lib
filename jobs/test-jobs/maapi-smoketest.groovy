#!/usr/bin/env groovy

@Library("pipelineAutomationLib")


List paramList = [
        choice(choices: "STAGE\nDEV\nPROD", description: "Choose App Environment", name: 'AppEnvironment'),
        string(defaultValue: "", description: "Specify failed email dist", name: 'alwaysEmailList'),
        string(defaultValue: "", description: "Specify failed email dist", name: 'failedEmailList'),
]

properties([
        parameters(paramList)
])

Map config = [:]

config.athGitBranch = "develop"
config.metaFilterTags =  "+smoke"
config.batchCount = 11

//config.debugPipeline = true

//config.changedEmailList = "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com"
config.changedEmailList = "ljohnson@dettonville.com"
config.alwaysEmailList = "ljohnson@dettonville.com"

params.each { key, value ->
    config[key]=value
}

echo "config=${config}"

runATH(config)

