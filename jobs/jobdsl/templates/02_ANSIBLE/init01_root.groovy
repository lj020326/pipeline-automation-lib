#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String projectName = "INFRA"

String projectFolder = projectName.toUpperCase()
String basePath = "${projectFolder}/ansible"

// ref: https://blog.pavelsklenar.com/jenkins-creating-dynamic-project-folders-with-job-dsl/
// def adminGroup = "sg_${projectName}_admin"
// def devGroup = "sg_${projectFolder}_dev"
// def opsGroup = "sg_${projectFolder}_ops"

folder(projectFolder) {
    description "This project folder contains jobs for the ${projectName} project"
    properties {
      authorizationMatrix {
        inheritanceStrategy {
            nonInheriting()
//             inheriting()
        }
        permissions([
            'GROUP:Job/Read:authenticated',
            "GROUP:Credentials/Create:infra-admin",
            "GROUP:Credentials/Delete:infra-admin",
            "GROUP:Credentials/ManageDomains:infra-admin",
            "GROUP:Credentials/Update:infra-admin",
            "GROUP:Credentials/View:infra-admin",
            "GROUP:Job/Build:infra-admin",
            "GROUP:Job/Cancel:infra-admin",
            "GROUP:Job/Configure:infra-admin",
            "GROUP:Job/Create:infra-admin",
            "GROUP:Job/Delete:infra-admin",
            "GROUP:Job/Discover:infra-admin",
            "GROUP:Job/Move:infra-admin",
            "GROUP:Job/Read:infra-admin",
            "GROUP:Job/Workspace:infra-admin",
            "GROUP:Run/Delete:infra-admin",
            "GROUP:Run/Replay:infra-admin",
            "GROUP:Run/Update:infra-admin",
            "GROUP:View/Configure:infra-admin",
            "GROUP:View/Create:infra-admin",
            "GROUP:View/Delete:infra-admin",
            "GROUP:View/Read:infra-admin",
            "GROUP:SCM/Tag:infra-admin",
        ])
      }
    }
}
