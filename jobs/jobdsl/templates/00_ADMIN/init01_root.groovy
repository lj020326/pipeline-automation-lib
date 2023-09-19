#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String projectName = "ADMIN"

String projectFolder = projectName.toUpperCase()
String basePath = "${projectFolder}/vm-templates"

String pipelineRepoUrl = "ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git"
String gitPipelineLibCredId = "bitbucket-ssh-jenkins"

// ref: https://blog.pavelsklenar.com/jenkins-creating-dynamic-project-folders-with-job-dsl/
// def adminGroup = "sg_${projectName}_admin"
// def devGroup = "sg_${projectFolder}_dev"
// def opsGroup = "sg_${projectFolder}_ops"

folder(projectFolder) {
    description "This project folder contains jobs for the ${projectName} project"
    properties {
      folderLibraries {
          libraries {
              // ref: https://issues.jenkins.io/browse/JENKINS-66402
              // ref: https://devops.stackexchange.com/questions/11833/how-do-i-load-a-jenkins-shared-library-in-a-jenkins-job-dsl-seed
              libraryConfiguration {
                  name("pipeline-automation-lib")
                  defaultVersion("main")
                  implicit(true)
                  includeInChangesets(false)
                  retriever {
                      modernSCM {
                          scm {
                              git {
                                  remote(pipelineRepoUrl)
                                  credentialsId(gitPipelineLibCredId)
                              }
                          }
                      }
                  }
              }
          }
      }
      authorizationMatrix {
        inheritanceStrategy {
            nonInheriting()
//             inheriting()
        }
      }
    }
}
