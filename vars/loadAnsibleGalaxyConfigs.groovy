#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.Logger

import groovy.transform.Field
@Field Logger log = new Logger(this)

Map call(Map params) {
    // copy immutable params maps to mutable config map
    Map config = params.clone()

    Map galaxyConfig = readYaml(file: config.galaxyYamlPath)
    config.collectionNamespace = galaxyConfig.namespace
    config.collectionName = galaxyConfig.name

    log.info("Derived Collection Namespace: ${config.collectionNamespace}")
    log.info("Derived Collection Name: ${config.collectionName}")

    if (!config?.collectionNamespace || !config?.collectionName) {
        error "FATAL: Could not derive collection namespace or name from ${config.galaxyYamlPath}. Please ensure 'namespace' and 'name' fields are present."
    }
    return config
}
