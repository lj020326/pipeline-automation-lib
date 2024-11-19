package com.dettonville.api.pipeline.deployment

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
    debugReleaseScript: false
    useSimulationMode: false

    runPostDeployTests: true
    
    jenkinsProjectName: "${dsl.env.JOB_NAME.split('/')[0]}"

    jenkinsApiCredId: "jenkins-rest-api-user"
    jenkinsRepoCredId: "dcapi_ci_vcs_user"
    jenkinsAraCredId: "dfs-automation-ara-cred"
    
    jenkinsRunAraDeployLabel: QA-LINUX

    failFast: false
    continueIfFailed: false

    ## EMAIL NOTIFICATION DISTRIBUTIONS
    changedEmailList: "ljohnson@dettonville.com"
    alwaysEmailList: "ljohnson@dettonville.com"
    failedEmailList: ""
    abortedEmailList: ""
    successEmailList: ""
    
    emailFrom: "DCAPI.DeployAutomation@dettonville.com"

    getDeployResultsFile: true
    jobResultsFile: "runAppDeployment-results.json"
    deployResultsFile: "DeployInfo.json"

    appTestEnvironment: DEV
    
    testJobBaseUri: "DCAPI/Acceptance_Test_Jobs"
    artifactGroupId: com.dettonville.developer

    araClientUrl: https://artifacts.dettonville.int/artifactory/releases/com/dettonville/ara/ara-release-cli/1.0.3/ara-release-cli-1.0.3.tar
    araReleaseSpecUrl: https://gitrepository.dettonville.int/stash/scm/dfsbizops/ara_spec_files.git
    araEnvSpecFile: "DCAPI/env_specs/dev_env_spec.json"
    araApiURL: https://dev-stage.techorch.dettonville.int

    appComponents:
        Frontend:
            appComponentRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/devzone-frontend.git"
            deployJobName: Frontend
            artifactId: devportal-frontend
            araReleaseSpecFile: DCAPI/ind_rel_specs/release_spec_content_DCAPI.DevZone.json
            araWorkflow: mc_api_dfs_workflows.dcapi_deploy_all_devzone_content
    
        DevPortal:
            appComponentRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/devportal.git"
            deployJobName: DevPortal
            artifactId: devportal
            araReleaseSpecFile: DCAPI/dcapi_flows/devportal.json
            araWorkflow: mc_api_dfs_workflows.dcapi_deploy_devportal
    
        OpenAPINotifier:
            appComponentRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/encryption-key-services.git"
            deployJobName: OpenAPINotifier
            artifactId: openapi-notifier
            araReleaseSpecFile: DCAPI/ind_rel_specs/release_spec_OpenAPI_Notifier.json
            araWorkflow: mc_api_dfs_workflows.deploy_war

    postDeployTests:
        - target: SMOKE/Chrome

"""

        // set job config settings
        defaultSettings = dsl.readYaml text: ymlDefaultSettingsString

        return defaultSettings
    }


}
