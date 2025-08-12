#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import java.net.URLDecoder

import static com.dettonville.pipeline.utils.ConfigConstants.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

Map call(Map params=[:]) {

    // copy immutable params maps to mutable config map
    Map config = params.clone()
//     log.enableDebug()

//     String gitBranch = URLDecoder.decode(env.BRANCH_NAME, "UTF-8")
    String gitBranch = URLDecoder.decode(env.GIT_BRANCH, "UTF-8")
    config.get('gitBranch',gitBranch)
    config.get('testGitBranch',gitBranch)

    config.testGitBranchAbbrev = config.testGitBranch.replaceAll(/^(.*)-(\d+)-(.*)$/, '$1-$2').replace('/','-').replace('%2F','-')
    log.debug("config.testGitBranchAbbrev=${config.testGitBranchAbbrev}")

    config.gitCommitId = env.GIT_COMMIT

    log.debug("config.gitBranch=${config.gitBranch}")
    log.debug("config.gitCommitId=${config.gitCommitId}")

    config.collectionsBaseDir = "${WORKSPACE}/ansible_collections"
    config.targetCollectionDir = "${config.collectionsBaseDir}/${config.collectionNamespace}/${config.collectionName}"
    log.debug("config.collectionsBaseDir=${config.collectionsBaseDir}")

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

    log.debug("config.collectionsBaseDir=${config.collectionsBaseDir}")

    // good: test-results/tests/dettonville/git_inventory/main
    // bad:  test-results/tests/ansible-git-inventory/main
//     String testComponentBaseDirDefault = "${config.testBaseDir}/tests/${config.testComponent.replace('.','/')}"
    String testComponentBaseDirDefault = "${config.testBaseDir}/tests/${config.collectionNamespace}/${config.collectionName}"
    config.testComponentBaseDir = config.get('testComponentBaseDir', testComponentBaseDirDefault)
    log.debug("config.testComponentBaseDir=${config.testComponentBaseDir}")

    String testComponentDirDefault = "${config.testComponentBaseDir}/${config.testGitBranchAbbrev}"
    config.testComponentDir = config.get('testComponentDir', testComponentDirDefault)
    log.debug("config.testComponentDir=${config.testComponentDir}")

    Map ansibleExtraVars = config.get('ansibleExtraVars',[:])
    if (config.testCaseIdList) {
        ansibleExtraVars.test_case_id_list_string = config.testCaseIdList
    }
    ansibleExtraVars.test_component__git_test_results_enabled = config.enableGitTestResults

//     config.testBasePath = "${env.WORKSPACE_TMP}/${config.testBaseDir}"
    config.testBasePath = "${env.WORKSPACE}/${config.testBaseDir}"
    ansibleExtraVars.test_job__test_base_dir = config.testBasePath
    ansibleExtraVars.test_git_branch = config.testGitBranch
    ansibleExtraVars.test_git_commit_hash = config.gitCommitId

    ansibleExtraVars.test_job_url = env.BUILD_URL

    config.ansibleExtraVars = ansibleExtraVars

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}
