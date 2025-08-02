#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

Map call(Map params=[:]) {

    // copy immutable params maps to mutable config map
    Map config = params.clone()
//     log.enableDebug()

    config.collectionsBaseDir = "${WORKSPACE}/ansible_collections"
    config.targetCollectionDir = "${config.collectionsBaseDir}/${config.collectionNamespace}/${config.collectionName}"
    log.info("config.collectionsBaseDir=${config.collectionsBaseDir}")

    config.get("galaxyYamlPath", "galaxy.yml")
    Map galaxyConfig = readYaml(file: config.galaxyYamlPath)
    config.collectionNamespace = galaxyConfig.namespace
    config.collectionName = galaxyConfig.name

    log.info("Derived Collection Namespace: ${config.collectionNamespace}")
    log.info("Derived Collection Name: ${config.collectionName}")

    if (!config?.collectionNamespace || !config?.collectionName) {
        error "FATAL: Could not derive collection namespace or name from ${config.galaxyYamlPath}. Please ensure 'namespace' and 'name' fields are present."
    }
    config.collectionsBaseDir = "${WORKSPACE}/collections"
//     config.collectionsBaseDir = "${WORKSPACE}/collections/ansible_collections"
    config.targetCollectionDir = "${config.collectionsBaseDir}/ansible_collections/${config.collectionNamespace}/${config.collectionName}"

    config.ansibleEnvVarsList = [
        "ANSIBLE_COLLECTIONS_PATH=~/.ansible/collections:/usr/share/ansible/collections:${config.collectionsBaseDir}"
    ]

    log.info("config.collectionsBaseDir=${config.collectionsBaseDir}")

    return config
}
