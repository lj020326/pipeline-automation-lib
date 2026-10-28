#!/usr/bin/env groovy

/**
 * Loads and executes ansible-test configuration from YAML manifest file
 * located in the repository.
 *
 * Users can override these default paths via the `config` map.
 * Each YAML file should contain parameters directly consumable by the `runAnsibleTest` function.
 */

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import groovy.transform.Field
@Field Logger log = new Logger(this)

Map call(Map config = [:], String configFile) {
    config = loadPipelineConfigFile(config, configFile)
    jobResults = runAnsibleTestMatrix(config)

    log.info("finished: jobResults=${JsonUtils.printToJsonString(jobResults)}")

    return jobResults

} // body

Map loadPipelineConfigFile(Map baseConfig, String configFile) {

    Map pipelineConfigMap = readYaml file: configFile
    log.debug("pipelineConfigMap=${JsonUtils.printToJsonString(pipelineConfigMap)}")

//     Map config = baseConfig + pipelineConfigMap
    Map config = MapMerge.merge(baseConfig, pipelineConfigMap)

    log.setLevel(config.logLevel)

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")
    return config
}
