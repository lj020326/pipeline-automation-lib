#!/usr/bin/env groovy

import jenkins.branch.*
import jenkins.model.Jenkins

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
// import com.dettonville.jobdsl.PipelineJobFactory
import com.dettonville.jobdsl.RepoTestJobCreator

@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

import groovy.transform.Field
@Field String scriptName = this.class.getName()

String pipelineConfigYaml = "config.repo-test-jobs.yml"

String configFilePath = "${new File(__FILE__).parent}"
println("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
Map basePipelineConfig = seedJobConfigs.pipelineConfig

println("${scriptName}: basePipelineConfig=${basePipelineConfig}")

List yamlProjectConfigList = basePipelineConfig.yamlProjectConfigList
println("${scriptName}: yamlProjectConfigList=${yamlProjectConfigList}")

yamlProjectConfigList.each { Map projectConfig ->
    String projectConfigYamlFile = projectConfig.pipelineConfigYaml
    println("${scriptName}: Creating Repo Test Jobs for ${projectConfigYamlFile}")

    Map repoTestJobConfigs = new Yaml().load(("${configFilePath}/${projectConfigYamlFile}" as File).text)
    Map pipelineConfig = repoTestJobConfigs.pipelineConfig

    // Call the static method directly from the class
    RepoTestJobCreator.createRepoTestJobsStatic(this, pipelineConfig)

}
println("${scriptName}: Finished creating repo test jobs")
