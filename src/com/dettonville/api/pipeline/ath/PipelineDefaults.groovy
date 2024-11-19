package com.dettonville.api.pipeline.ath

class PipelineDefaults implements Serializable {

    static Map defaultSettings

    static Map getDefaultSettings(def dsl) {

        if (defaultSettings) {
            return defaultSettings
        }

        String ymlDefaultSettingsString = """
---
pipeline:

    logLevel: "INFO"
    debugPipeline: false
    debugMvn: false

    ## ref: https://stackoverflow.com/questions/21638697/disable-maven-download-progress-indication
    ## ref: https://stackoverflow.com/questions/17979685/disable-maven-execution-debug-output
    ## ref: https://books.sonatype.com/mvnref-book/reference/running-sect-options.html
    mvnLogOptions: "-B -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"

    appEnvironment: "STAGE_EXTERNAL"
    athGitRepo: "https://gitrepository.dettonville.int/stash/scm/api/infra-test.git"
    athGitBranch: "main"

    useLocalDir: false
    useTestGroups: true
    runSerenityRpt: true

    useSimulationMode: false
    useDryRun: false
    useStaggeredParallelStart: true
    useSingleTestNode: false
    useExecEnvJenkins: true
    useMtafWebProxy: false
    checkIfDeployJobsRan: true

    ## PER_JOB_RUN, PER_JOB, PER_RUN, PER_NODE
    runBsAgentMethod: "PER_RUN"

    webPlatform: "browserstack"

    metaFilterTags: ""
    storyName: ""

    ## RALLY parameters
    useRallyIntegration: false
    enableRallyProxy: true
    jenkinsMaster: "${dsl.env.BUILD_URL.split('/')[2].split(':')[0]}"
    jenkinsProjectName: "${dsl.env.JOB_NAME.split('/')[0]}"

    rallyBuild: ""
    rallyProjectName: "Quality Chapter  (Developer Zone APIs)"
    rallyScreenShotMode: "ON_FAILURE"
    rallyUrl: "https://rally1.rallydev.com"
    testSetId: "CREATE_ONCE"
    testSetName: ""

    ## PERF PARAMS
    runSingleMvnCmdMode: false
    parallelRunCount: 5
    useBatchMode: true
    useLocalMvnRepo: false
    jbehaveExecutionThreads: ""

    jenkinsBsCredId: "dcapi_browserstack_creds"
    jenkinsRallyCredId: "dcapi_rally_key"
    jenkinsRepoCredId: "dcapi_ci_vcs_user"
    jenkinsApiCredId: "jenkins-rest-api-user"
    artifactoryApiCredId: "dcapi_ci_vcs_user"

    ## browserstack localagent params
    useBrowserstackLocalAgent: true
    startBrowserstackLocalAgent: false
    useBrowserstackLocalProxy: false
    forceBrowserstackLocalProxy: false

    ## browserstack params
    useBrowserstackProxy: false
    useEmptyBrowserstackProxy: false
    forceBrowserstackProxy: false
    runBSCurlTest: true
    curlBSTimeout: 5
    runBsDiagnostics: true
    
    browserstackResolution: "1920x1080"
##   browserstackWebOS: "OSX"
##   browserstackWebOSVersion: "High Sierra"
    browserstackWebOS: "Windows"
    browserstackWebOSVersion: "10"

    browserstackIdleTimeout: "300"
    browserstackFirefoxVersion: "66"
    browserstackChromeVersion: "74"
    browserstackEdgeVersion: "11"
    browserstackSafariVersion: "11"
    browserstackIEVersion: "11"
    browserstackBrowser: "Chrome"
    browserstackDebug: true
    browserstackUseIdentifier: true
    browserstackLocalIdentifier: ""
    browserstackMobileDevice: "Iphone X"
    browserstackRealMobile: true

    ## STATIC / OTHER PARAMS
    browserstackHubUrl: "hub-cloud.browserstack.com"
##   browserstackHubUrl: "hub.browserstack.com"
    browserstackWebLocalfileupload: true
    browserstackAcceptSSLCerts: true

    browserstackChromeIgnoreCert: true
    browserstackAcceptInsecureCerts: true
    browserstackNetworkLogs: true

#    browserstackProxyHost: "10.158.147.49"
#    browserstackProxyPort: 16437
    browserstackProxyHost: "outboundproxy.dettonville.int"
    browserstackProxyPort: 15768

    waitingTime: 1

    ## EMAIL NOTIFICATION DISTRIBUTIONS
    changedEmailList: "ljohnson@dettonville.com, conor.dixon@dettonville.com"
    alwaysEmailList: "ljohnson@dettonville.com, conor.dixon@dettonville.com"
    failedEmailList: ""
    abortedEmailList: ""
    successEmailList: ""
    emailFrom: "DCAPI.TestAutomation@dettonville.com"

    sendEmail: false
    sendCDREmail: false
    sendInlineReport: true

    publishJbehaveRpt: false
    publishSerenityRpt: true

    sendJbehaveRpt: false
    sendSerenityRpt: false
    sendSerenitySnapshot: true

    runSonar: false
    skipTests: true
    failFast: false
    failureIgnore: true

    mvnLocalRepoDir: ".m2"
    checkoutDir: "run_${dsl.env.BUILD_NUMBER.toString().padLeft(5,'0')}"
#    checkoutDir: "."
##   executionDir: "."

    bsAgentDistGitRepo: "https://gitrepository.dettonville.int/stash/scm/api/deployment_configs.git"
    bsAgentBinType: "linux-x64"
    bsAgentVersion: "7.5"

    bsAgentBaseDir: "tmp/bslocalagent"
    bsAgentBinDir: "tmp/bslocalagent/bin"
    bsAgentLogDir: "tmp/bslocalagent/logs"
    cleanupOrphanedBsAgents: true

    forceShutdownBsAgent: false
    forceCleanupBsBaseDir: false

    pyUtilsImage: "artifactory.dev.dettonville.int:6555/com-dettonville-api/docker-pyutils:latest"
##   pyUtilsImage: "artifactory.dev.dettonville.int:6555/com-dettonville-api/docker-pyutils:42"
##   pyUtilsImage: "artifactory.dev.dettonville.int:6555/com-dettonville-api/docker-pyutils@sha256:23249a2fbd7cb4b7678f5bd5f47be8d2cd03304ffb7e4f073427bd66fc9c15f2"
    pageResImage: "artifactory.dev.dettonville.int:6555/com-dettonville-api/pageres:latest"

    createEmailableReports: false
    snapFile: "screen-snapshot"
    jbehaveRpt: "emailable.index.html"
    jbehaveRptName: "JbehaveReports"
    serenityRpt: "emailable.index.html"
    serenityRptName: "SerenityReports"

    sonarUrl: "${dsl.env.SONAR_URL}"

    ## ref: https://fusion.dettonville.int/confluence/display/CD/Use+Build+Node+Labels+in+EVERY+Job
    jenkinsM3NodeLabel: "QA-LINUX || PROD-LINUX"
    jenkinsDockerLabel: "DOCKER"
    jenkinsRunTestsLabel: "DEVCLD-LIN7"

    jobResultsFile: "runATH-results.json"

    collectTestResults: false
    maxTestResultsHistory: 3
##   testResultsHistory: "historicalTestResults.json"
    testResultsHistory: "aggregateTestResults.json"

    getJobCause: true
    getLatestArtifactVersion: true
#    getLatestArtifactVersion: false
    
    deployConfig:
        jobPrefix: "Deploy"
        jobBaseUri: "jenkins/job/DCAPI/job/DeploymentJobs/job"
        jobVersionParamName: "ARTIFACT_VERSION"
        hierarchicalDeployJobs: false
#        jobBaseUri: "jenkins/job/DCAPI/job/Jobs/job/DeploymentJobs/job"
#        hierarchicalDeployJobs: true
#        jobVersionParamName: "ArtifactVersion"
        artifactGroupId: com.dettonville.developer
        componentList:
            - name: INFRA-Frontend
              deployJobName: Frontend
              branch: main
              artifactId: devportal-frontend
            - name: INFRA-DevPortal
              deployJobName: DevPortal
              branch: main
              artifactId: devportal
            - name: INFRA-OpenAPINotifier
              deployJobName: OpenAPINotifier
              branch: main
              artifactId: openapi-notifier
    
    buildStatusConfig:
        prettyPrint: true
        getDeployBuildResults: true

"""
        
        // set job config settings
        defaultSettings = dsl.readYaml text: ymlDefaultSettingsString

        defaultSettings.pipeline.jobAcronymName="${dsl.env.JOB_NAME.split('/').collect{ it.toLowerCase().take(1) }.join('')}"
        defaultSettings.pipeline.jobName="${dsl.env.JOB_NAME.split('/').collect{ it.replaceAll('%2F', '/').replaceAll(' ', '').replaceAll('-', '').replaceAll('_', '').toLowerCase() }.join('-')}"
//        defaultSettings.pipeline.application="${dsl.env.JOB_NAME.replaceAll('%2F', '/').replaceAll('/', '-').replaceAll(' ', '-').toUpperCase()}"
        defaultSettings.pipeline.application="${dsl.env.JOB_NAME.split('/').collect{ it.toUpperCase().replaceAll('%2F', '').replaceAll(' ', '').replaceAll('-', '').replaceAll('_', '') }.join('-')}"

//        defaultSettings.pipeline.browserstackBuild="RUN ${dsl.env.BUILD_NUMBER}"
        defaultSettings.pipeline.browserstackBuild="${dsl.env.JOB_NAME.split('/')[0]}-${dsl.env.JOB_NAME.split('/').collect{ it.take(1) }.join('').toUpperCase()}-RUN${dsl.env.BUILD_NUMBER.padLeft(5,'0')}"

        defaultSettings.pipeline.isStageJenkins = (defaultSettings.pipeline.jenkinsMaster.equals("stage.cd.dettonville.int")) ? true : false

        return defaultSettings
    }


}
