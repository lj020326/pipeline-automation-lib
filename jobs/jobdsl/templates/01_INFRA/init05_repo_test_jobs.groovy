#!/usr/bin/env groovy

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.JenkinsLogger
import com.dettonville.jobdsl.RepoTestJobCreator

@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

import groovy.transform.Field
@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)
// @Field JenkinsLogger log = new JenkinsLogger(this, logLevel: 'DEBUG', prefix: scriptName)

String pipelineConfigYaml = "config.repo-test-jobs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
log.info("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
Map basePipelineConfig = seedJobConfigs.pipelineConfig

log.info("${scriptName}: basePipelineConfig=${basePipelineConfig}")

List yamlProjectConfigList = basePipelineConfig.yamlProjectConfigList
log.info("${scriptName}: yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    log.info("${scriptName}: Creating Repo Test Jobs for ${projectConfigYamlFile}")

    Map repoTestJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    Map pipelineConfig = repoTestJobConfigs.pipelineConfig

    // Call the static method directly from the class
    RepoTestJobCreator.createRepoTestJobs(this, pipelineConfig)

}
log.info("${scriptName}: Finished creating repo test jobs")
