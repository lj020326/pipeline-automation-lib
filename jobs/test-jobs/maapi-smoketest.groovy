#!/usr/bin/env groovy

@Library("pipeline-automation-lib")


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

//config.changedEmailList = "DST_Open_API_Development_Team@dettonville.org, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com"
config.changedEmailList = "ljohnson@dettonville.org"
config.alwaysEmailList = "ljohnson@dettonville.org"

params.each { key, value ->
    config[key]=value
}

echo "config=${config}"

runATH(config)

