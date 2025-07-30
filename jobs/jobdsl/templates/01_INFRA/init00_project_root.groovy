#!/usr/bin/env groovy

// // Get a reference to your shared library's entry point
// def pipelineAutomationLib = this.getBinding().getProperty('pipelineAutomationLib')

// ref: https://stackoverflow.com/questions/36199072/how-to-get-the-script-name-in-groovy
// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field

import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.JenkinsLogger

@Field String scriptName = this.class.getName()

@Field JenkinsLogger log = new JenkinsLogger(this, prefix: scriptName)
//@Field JenkinsLogger log = new JenkinsLogger(this, logLevel: 'DEBUG', prefix: scriptName)

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
log.info("${scriptName}: configFilePath: ${configFilePath}")

Map seedJobConfigs = new Yaml().load(("${configFilePath}/${pipelineConfigYaml}" as File).text)
// log.info("${scriptName}: seedJobConfigs=${seedJobConfigs}")

Map pipelineConfig = seedJobConfigs.pipelineConfig
// log.info("${scriptName}: pipelineConfig=${JsonUtils.printToJsonString(pipelineConfig)}")

String pipelineRepoUrl = pipelineConfig.pipelineRepoUrl
String gitCredentialsId = pipelineConfig.gitCredentialsId

Map envConfigs = pipelineConfig.envConfigs

// ref: https://blog.pavelsklenar.com/jenkins-creating-dynamic-project-folders-with-job-dsl/
def jobFolder = folder(projectFolder) {
    description "This project folder contains jobs for the ${projectName} project"
    properties {
        authorizationMatrix {
            inheritanceStrategy {
                nonInheriting() // Or inheriting(), depending on your desired behavior
            }
            entries {
                user {
                    name('admin')
                    permissions([
                        'Job/Build',      // Trigger builds of jobs
                        'Job/Cancel',     // Cancel running builds
                        'Job/Configure',  // Configure jobs
                        'Job/Create',     // Create new jobs
                        'Job/Delete',     // Delete jobs (within this folder)
                        'Job/Discover',   // Discover jobs (required for viewing)
                        'Job/Move',       // Move jobs (within or out of this folder)
                        'Job/Read',       // Read job configurations and status
                        'Job/Workspace',  // Access job workspace
                        // Run Permissions (for individual builds)
                        'Run/Delete',     // Delete build records
                        'Run/Replay',     // Replay pipeline builds
                        'Run/Update',     // Update build descriptions/properties
                        // SCM Permissions
                        'SCM/Tag',        // Create SCM tags (e.g., in pipelines)
                        // Credential Permissions (if credentials are stored at folder level)
                        'Credentials/Create',
                        'Credentials/Delete',
                        'Credentials/ManageDomains',
                        'Credentials/Update',
                        'Credentials/View',
                        // View Permissions (if custom views are created within the folder)
                        'View/Configure',
                        'View/Create',
                        'View/Delete',
                        'View/Read'
                    ])
                }
                group {
                    name('authenticated')
                    permissions([
                        'Job/Read',      // Trigger builds of jobs
                    ])
                }
            }
        }
    }
}

log.info("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")

// No longer setting the pipeline library at the folder level
// if (JENKINS_ENV) {
//     Map pipelineEnvConfigs = envConfigs[JENKINS_ENV]
//     String pipelineLibraryBranch = pipelineEnvConfigs.pipelineLibraryBranch
//
//     jobFolder.properties {
//         folderLibraries {
//             libraries {
//                 // ref: https://issues.jenkins.io/browse/JENKINS-66402
//                 // ref: https://devops.stackexchange.com/questions/11833/how-do-i-load-a-jenkins-shared-library-in-a-jenkins-job-dsl-seed
//                 libraryConfiguration {
//                     name("pipeline-automation-lib")
//                     defaultVersion(pipelineLibraryBranch)
//                     implicit(true)
//                     includeInChangesets(false)
//                     retriever {
//                         modernSCM {
//                             scm {
//                                 git {
//                                     remote(pipelineRepoUrl)
//                                     credentialsId(gitCredentialsId)
//                                 }
//                             }
//                         }
//                     }
//                 }
//             }
//         }
//     }
// }
