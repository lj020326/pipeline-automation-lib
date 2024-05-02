#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String projectName = "INFRA"

String projectFolder = "${projectName.toUpperCase()}"

// separate configuration from job dsl "seedjob" code
// ref: https://stackoverflow.com/questions/47443106/jenkins-dsl-parse-yaml-for-complex-processing#54665138
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

String pipelineConfigYaml = "config.project-root.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String configFilePath = "${new File(__FILE__).parent}"
println("configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
// println("seedJobConfigs=${seedJobConfigs}")

Map pipelineConfig = seedJobConfigs.pipelineConfig
// println("pipelineConfig=${JsonUtils.printToJsonString(pipelineConfig)}")

String pipelineRepoUrl = pipelineConfig.pipelineRepoUrl
String gitCredentialsId = pipelineConfig.gitCredentialsId

Map envConfigs = pipelineConfig.envConfigs

// ref: https://blog.pavelsklenar.com/jenkins-creating-dynamic-project-folders-with-job-dsl/
def jobFolder = folder(projectFolder) {
    description "This project folder contains jobs for the ${projectName} project"
    properties {
        authorizationMatrix {
          inheritanceStrategy {
              nonInheriting()
  //             inheriting()
          }
          // ref: https://github.com/jenkinsci/matrix-auth-plugin/releases
          entries {
            user {
                name('admin')
                permissions([
                  'Overall/Administer'
                ])
            }
            group {
                name('admin')
                permissions([
                  'Overall/Administer'
                ])
            }
            group {
                name('infra-admin')
                permissions([
                  'Overall/Administer'
                ])
            }
            group {
                name('Domain Admins')
                permissions([
                  'Overall/Administer'
                ])
            }
            group {
                name('authenticated')
                permissions([
                  'Overall/Read'
                ])
            }
          }
        }
    }
}

println("JENKINS_ENV=${JENKINS_ENV}")

if (JENKINS_ENV) {
    Map pipelineEnvConfigs = envConfigs[JENKINS_ENV]
    String pipelineLibraryBranch = pipelineEnvConfigs.pipelineLibraryBranch

    jobFolder.properties {
        folderLibraries {
            libraries {
                // ref: https://issues.jenkins.io/browse/JENKINS-66402
                // ref: https://devops.stackexchange.com/questions/11833/how-do-i-load-a-jenkins-shared-library-in-a-jenkins-job-dsl-seed
                libraryConfiguration {
                    name("pipeline-automation-lib")
                    defaultVersion(pipelineLibraryBranch)
                    implicit(true)
                    includeInChangesets(false)
                    retriever {
                        modernSCM {
                            scm {
                                git {
                                    remote(pipelineRepoUrl)
                                    credentialsId(gitCredentialsId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
