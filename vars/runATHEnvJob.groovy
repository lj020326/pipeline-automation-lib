#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map config=[:]) {

    config.get('enabledParamList', ['alwaysEmailList','useDryRun']) as List
    config.get('enableDevParams', (config.enabledParamList.isEmpty()) ? true : false)
    config.enableBranchParam = config.enabledParamList.contains("athGitBranch") ? true : config.get('enableBranchParam', false)
    config.get('athGitRepo', "https://gitrepository.dettonville.int/stash/scm/api/infra-test.git")
    config.enableBranchSettings = config.enableBranchSettings ?: false

    Map configDefaultsMap

    List jobParts = JOB_NAME.split("/")
    log.info("jobParts=${jobParts}")

//        int jobBaseFolderLevel = 2
    config.get('jobBaseFolderLevel', 2)

//        int startIdx=jobBaseFolderLevel+1
    int startIdx = config.jobBaseFolderLevel + 1
    int endIdx = jobParts.size() - 1

//        config.appEnvironment=jobParts[jobBaseFolderLevel].toUpperCase()
    config.appEnvironment = jobParts[config.jobBaseFolderLevel].toUpperCase()
    String testCase = jobParts[-1].toLowerCase()

    //    String[] testSuiteParts = jobParts[startIdx..<jobParts.size()-1]
    List testSuiteParts = []
    for (int i = startIdx; i < endIdx; i++) {
        testSuiteParts.add(jobParts[i])
    }

    //    echo "${logPrefix} testSuiteParts=${testSuiteParts}"
    config.testSuite = testSuiteParts.join("/")

    log.info("config.appEnvironment=${config.appEnvironment}")
    log.info("config.athGitBranch=${config.athGitBranch}")
    log.info("config.testSuite=${config.testSuite}")
    log.info("testCase=${testCase}")

    String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
    String parentFolder = "${jobFolder.substring(0, jobFolder.lastIndexOf("/"))}"
    //    echo "jobFolder=${jobFolder}"
    //    echo "parentFolder=${parentFolder}"

    String configYmlStr = """
---
jobSettings:
    STAGE:
        SMOKE:
            alwaysEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com, dcapi_qa@dettonville.com"
        SANITY:
            testCases:
                chrome:
                    nextJob: "${parentFolder}/API/ApiValidation"

    STAGE_EXTERNAL:
        SMOKE:
            alwaysEmailList: "DST_Open_API_Development_Team@dettonville.com, infra-team@dettonville.flowdock.com, api-tech-talk@dettonville.flowdock.com, dcapi_qa@dettonville.com"

        SANITY:
            testCases:
                chrome:
                    nextJob: "${parentFolder}/API/ApiValidation"
            
    PROD_COPRO:
        SANITY: 
            parallelRunCount: 7
        REGRESSION:
            parallelRunCount: 7

    PROD_STL:
        SANITY: 
            parallelRunCount: 7
        REGRESSION:
            parallelRunCount: 7

    PROD_KSC:
        SANITY: 
            parallelRunCount: 7
        REGRESSION:
            parallelRunCount: 7
    

branchSettings:

    main:
    
    develop:
#        useSimulationMode: true

        deployConfig:
            jobPrefix: 
            jobBaseUri: "jenkins/job/DCAPI/job/Jobs/job/DeploymentJobs/job"
            hierarchicalDeployJobs: true
            jobVersionParamName: "ArtifactVersion"

        buildStatusConfig:
            getDeployBuildResults: false

    default:

"""

    List paramList = []

    List appEnvironmentList = ["STAGE_EXTERNAL",
                               "STAGE",
                               "DEV",
                               "DEV_CLOUD",
                               "PROD_EXTERNAL",
                               "PROD_COPRO",
                               "PROD_STL",
                               "PROD_KSC",
                               "MTF",
                               "Stage1",
                               "Stage2",
                               "Stage3",
                               "ITF"]

    Map paramMap = [
            appEnvironment      : choice(choices: appEnvironmentList.join('\n'), description: "Choose App Environment", name: 'AppEnvironment'),
            browserstackBrowser : choice(choices: "Chrome\nSafari\nFirefox\nEdge\nIE\nIphone\nAndroid\nAPI\nMultiPlatform", description: "Choose Web Platform", name: 'BrowserstackBrowser'),
            parallelRunCount    : string(defaultValue: "1", description: "Specify number of maven jobs to run in parallel\nE.g., 1, 4, 8,...'n'", name: 'ParallelRunCount'),
            metaFilterTags      : string(defaultValue: "+smoke", description: "Specify meta filter tags\nE.g., '-smoke', '+404'", name: 'MetaFilterTags'),
            alwaysEmailList     : string(defaultValue: "", description: "Specify always email dist", name: 'AlwaysEmailList'),
            useDryRun           : booleanParam(defaultValue: false, description: "Dry Run?", name: 'UseDryRun'),
            useSimulationMode   : booleanParam(defaultValue: false, description: "Use Simulation Mode?", name: 'UseSimulationMode'),
            debugMvn            : booleanParam(defaultValue: false, description: "Debug Maven?", name: 'DebugMvn'),
            debugPipeline       : booleanParam(defaultValue: false, description: "Debug Pipeline?", name: 'DebugPipeline'),
            logLevel            : choice(choices: "INFO\nDEBUG\nWARN\nERROR", description: "Choose Log Level", name: 'LogLevel'),
            jenkinsRunTestsLabel: string(defaultValue: "", description: "Specify the Jenkins Test node label\nIf empty - the env is set to default based on env\nE.g., '', 'DEVCLD-LIN7', 'QA-LINUX', 'PROD-LINUX',...'any'", name: 'JenkinsRunTestsLabel')
    ]

    if (config.enableDevParams) {
        config.enabledParamList = paramMap.keySet() as List
    }

//    node ("QA-LINUX || PROD-LINUX") {
    node {

        if (config.enableBranchParam) {
            String defaultBranch = "develop"
            List branchList = Utilities.getRepoBranchList(this, config.athGitRepo, defaultBranch)

            paramList.add(choice(choices: branchList.join('\n'), description: "Choose Branch", name: 'AthGitBranch'))
        }

        log.info("config.enabledParamList=${config.enabledParamList}")

        config.enabledParamList.each { String paramName ->
            log.debug("paramName=${paramName}")
            if (paramMap.containsKey(paramName)) {
                paramList.add(paramMap[paramName])
            } else {
                log.warn("paramName=${paramName} not found")
            }
        }

        properties([
            parameters(paramList),
            disableConcurrentBuilds()
        ])

        params.each { key, value ->
            key = Utilities.decapitalize(key)
            if (value != "") {
                config[key] = value
            }
        }

        configDefaultsMap = readYaml text: configYmlStr
    }

    // 1) set any env+testsuite+testCase job specific test settings. E.g., parallelRunCount
    if (configDefaultsMap.jobSettings.containsKey(config.appEnvironment) &&
            configDefaultsMap.jobSettings[config.appEnvironment].containsKey(config.testSuite) &&
            configDefaultsMap.jobSettings[config.appEnvironment][config.testSuite].containsKey('testCases') &&
            configDefaultsMap.jobSettings[config.appEnvironment][config.testSuite].testCases.containsKey(testCase))
    {
        Map jobSettings = configDefaultsMap.jobSettings[config.appEnvironment][config.testSuite].testCases.get(testCase, [:])
//        config=jobSettings + config
        config=MapMerge.merge(jobSettings, config)
    }

    // 2) set any env+testsuite job specific test settings. E.g., parallelRunCount
    if (configDefaultsMap.jobSettings.containsKey(config.appEnvironment) &&
            configDefaultsMap.jobSettings[config.appEnvironment].containsKey(config.testSuite))
    {
        Map jobSettings = configDefaultsMap.jobSettings[config.appEnvironment].get(config.testSuite, [:])
        config=jobSettings.findAll { it.key != 'testCases' } + config
    }

    config.testCase=config.testCase ?: testCase

    if (config?.enableBranchSettings) {
        // set any branch specific test settings. E.g., feature/*
        Map branchSettings
        if (configDefaultsMap.branchSettings.containsKey(athGitBranch)) {
            branchSettings = configDefaultsMap.branchSettings[athGitBranch]
        } else {
            branchSettings = configDefaultsMap.branchSettings.default
        }

        branchSettings = (branchSettings) ? branchSettings : [:]
        log.info("branchSettings=${JsonUtils.printToJsonString(branchSettings)}")

        if (branchSettings!=null) {
//                config=branchSettings + config
//                config=MapMerge.merge(branchSettings, config)
            config=MapMerge.merge(config, branchSettings)
        } else {
            log.warn("branchSettings not set")
            return
        }
    }

    if (config.containsKey("enabledParamList")) {
        config.remove("enabledParamList")
    }
    log.info("config=${JsonUtils.printToJsonString(config)}")

    runATHEnv(config)

}

