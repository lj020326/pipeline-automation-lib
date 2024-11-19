#!/usr/bin/env groovy
import groovy.json.JsonOutput

def call(Map config=[:]) {

    List paramList = [
            booleanParam(defaultValue: true, description: "Run Smoke?", name: 'RunSmoke'),
            booleanParam(defaultValue: true, description: "Run Sanity?", name: 'RunSanity'),
            booleanParam(defaultValue: true, description: "Run Regression?", name: 'RunRegression'),
            booleanParam(defaultValue: false, description: "Continue if Failed?", name: 'ContinueIfFailed'),
            booleanParam(defaultValue: true, description: "Run Smoke in Parallel?  Otherwise, sequentially", name: 'RunSmokeInParallel'),
            booleanParam(defaultValue: false, description: "Run Sanity in Parallel?  Otherwise, sequentially", name: 'RunSanityInParallel'),
            booleanParam(defaultValue: false, description: "Run Regression in Parallel?  Otherwise, sequentially", name: 'RunRegressionInParallel'),
            booleanParam(defaultValue: false, description: "Run Tests using Chrome?", name: 'RunChrome'),
            booleanParam(defaultValue: true, description: "Run Tests using Firefox?", name: 'RunFirefox'),
            booleanParam(defaultValue: false, description: "Run Tests using Safari?", name: 'RunSafari'),
            booleanParam(defaultValue: false, description: "Run Tests using Edge?", name: 'RunEdge'),
            booleanParam(defaultValue: false, description: "Run Tests using Android?", name: 'RunAndroid'),
            booleanParam(defaultValue: false, description: "Run Tests using iPhone?", name: 'RunIPhone'),
    ]

    properties([
            parameters(paramList)
    ])

    //    config.debugPipeline = true

    String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
    echo "jobFolder=${jobFolder}"
    List jobList = []

    if (params.RunSmoke) {
        List testJobList = []
        String testStage = "SMOKE"
        if (params.RunFirefox) testJobList.add(["job": "${jobFolder}/${testStage}/Firefox"])
        if (params.RunChrome) testJobList.add(["job": "${jobFolder}/${testStage}/Chrome"])
        if (params.RunSafari) testJobList.add(["job": "${jobFolder}/${testStage}/Safari"])
        if (params.RunEdge) testJobList.add(["job": "${jobFolder}/${testStage}/Edge"])
        if (params.RunAndroid) testJobList.add(["job": "${jobFolder}/${testStage}/Android"])
        if (params.RunIPhone) testJobList.add(["job": "${jobFolder}/${testStage}/IPhone"])
        jobList.add(["jobs": testJobList, "runInParallel": params.RunSmokeInParallel, "testStage": testStage])
    }

    if (params.RunSanity) {
        List testJobList = []
        String testStage = "SANITY"
        if (params.RunFirefox) testJobList.add(["job": "${jobFolder}/${testStage}/Firefox"])
        if (params.RunChrome) testJobList.add(["job": "${jobFolder}/${testStage}/Chrome"])
        if (params.RunSafari) testJobList.add(["job": "${jobFolder}/${testStage}/Safari"])
        if (params.RunEdge) testJobList.add(["job": "${jobFolder}/${testStage}/Edge"])
        if (params.RunAndroid) testJobList.add(["job": "${jobFolder}/${testStage}/Android"])
        if (params.RunIPhone) testJobList.add(["job": "${jobFolder}/${testStage}/IPhone"])
        jobList.add(["jobs": testJobList, "runInParallel": params.RunSanityInParallel, "testStage": testStage])
    }

    if (params.RunRegression) {
        List testJobList = []
        String testStage = "REGRESSION"
        if (params.RunFirefox) testJobList.add(["job": "${jobFolder}/${testStage}/Firefox"])
        if (params.RunChrome) testJobList.add(["job": "${jobFolder}/${testStage}/Chrome"])
        if (params.RunSafari) testJobList.add(["job": "${jobFolder}/${testStage}/Safari"])
        if (params.RunEdge) testJobList.add(["job": "${jobFolder}/${testStage}/Edge"])
        if (params.RunAndroid) testJobList.add(["job": "${jobFolder}/${testStage}/Android"])
        if (params.RunIPhone) testJobList.add(["job": "${jobFolder}/${testStage}/IPhone"])
        jobList.add(["jobs": testJobList, "runInParallel": params.RunRegressionInParallel, "testStage": testStage])
    }

    config.testJobList = jobList
    config.runInParallel = false

    //config.debugPipeline = true

    //    config.changedEmailList = "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com"

    params.each { key, value ->
        if (value != "") {
            config[key] = value
        }
    }

    List recipientList = (config?.alwaysEmailList && config.alwaysEmailList != "") ? (config.alwaysEmailList.contains(",")) ? config.alwaysEmailList.tokenize(',') : [config.alwaysEmailList] : []
    recipientList.add("lee.johnson@dettonville.com")
    //    recipientList.add("SIT-engineer@dettonville.com")
    config.alwaysEmailList = recipientList.join(",")

    //echo "config=${config}"
    echo "config=${printToJsonString(config)}"

    runTestJobs(config)

}

String printToJsonString(Map config) {
    return JsonOutput.prettyPrint(JsonOutput.toJson(config))
}

