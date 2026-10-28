#!/usr/bin/env groovy

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.JenkinsLogger
import com.dettonville.jobdsl.RepoJobCreator

@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

import groovy.transform.Field
@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)

String pipelineConfigYaml = "config.repo-jobs.yml"

String configFilePath = "${new File(__FILE__).parent}"
log.info("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)

List yamlProjectConfigList = seedJobConfigs.yamlProjectConfigList
log.info("${scriptName}: yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    log.info("${scriptName}: Creating Repo Jobs for ${projectConfigYamlFile}")

    Map repoJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)

    RepoJobCreator.createRepoJobs(this, repoJobConfigs)
}
log.info("${scriptName}: Finished creating all repo automation jobs")
