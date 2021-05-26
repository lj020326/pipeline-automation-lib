package com.dettonville.api.pipeline.ath

class ApiPipelineDefaults implements Serializable {

    static Map defaultSettings

    static Map getDefaultSettings(def dsl) {

        if (defaultSettings) {
            return defaultSettings
        }

        String ymlDefaultSettingsString = """
---
pipeline:

    ## PERF PARAMS
    parallelRunCount: 1
    useSingleTestNode: true
    runSingleMvnCmdMode: true
    webPlatform: "API"
    browserPlatform: "RestAssuredHttpBuilder"

    ## browserstack localagent params
    useBrowserstackLocalAgent: false
    startBrowserstackLocalAgent: false
    useBrowserstackLocalProxy: false
    forceBrowserstackLocalProxy: false

    ## browserstack params
    useBrowserstackProxy: false
    useEmptyBrowserstackProxy: false
    forceBrowserstackProxy: false
    runBSCurlTest: false
    runBsDiagnostics: false

    ## STATIC / OTHER PARAMS
    publishJbehaveRpt: true
    checkoutDir: "."
    jobResultsFile: "runApi-results.json"

"""

        // set job config settings
        Map baseSettings = PipelineDefaults.getDefaultSettings(dsl)
        Map apiSettings = dsl.readYaml text: ymlDefaultSettingsString

        defaultSettings = com.dettonville.api.pipeline.utils.MapMerge.merge(baseSettings, apiSettings)

        return defaultSettings
    }


}
