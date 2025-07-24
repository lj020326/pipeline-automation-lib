#!/usr/bin/env groovy

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
String gitPipelineLibCredId = "bitbucket-ssh-jenkins"

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
