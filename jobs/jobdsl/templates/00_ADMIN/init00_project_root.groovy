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
String projectName = "ADMIN"

String projectFolder = projectName.toUpperCase()

String pipelineRepoUrl = "ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git"
String gitPipelineLibCredId = "gitea-ssh-jenkins"

// ref: https://blog.pavelsklenar.com/jenkins-creating-dynamic-project-folders-with-job-dsl/
// def adminGroup = "sg_${projectName}_admin"
// def devGroup = "sg_${projectFolder}_dev"
// def opsGroup = "sg_${projectFolder}_ops"

folder(projectFolder) {
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
