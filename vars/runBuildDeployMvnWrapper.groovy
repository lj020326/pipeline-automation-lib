#!/usr/bin/env groovy

def call(Map config=[:], boolean testMode=false) {

    //paramList = []
    //
    //properties([
    //        parameters(paramList)
    //])
    List paramList = [
            booleanParam(defaultValue: false, description: "Release to DEV?", name: 'ReleaseDev'),
            booleanParam(defaultValue: false, description: "Release to STAGE?", name: 'ReleaseStage')
    ]

    properties([
            parameters(paramList)
    ])

    params.each { key, value ->
        config[key]=value
    }

    runBuildDeployMvn(config)

}
