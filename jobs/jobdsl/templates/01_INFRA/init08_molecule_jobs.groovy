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

String baseFolder = "INFRA/repo-test-automation"

jobFolder = "${baseFolder}/run-molecule"

// ref: https://stackoverflow.com/questions/40215394/how-to-get-environment-variable-in-jenkins-groovy-script-console
log.info("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")
if (JENKINS_ENV=='PROD') {
    createMoleculeJobs(this)

    log.info("${scriptName}: Finished creating molecule jobs")
}

//******************************************************
//  Function definitions from this point forward
//
void createMoleculeJobs(def dsl) {

    dsl.pipelineJob(jobFolder) {
        parameters {
            booleanParam('InitializeParamsOnly', false, 'Set to true to only initialize parameters without full execution.')
        }
        properties {
            // Specific to parent multibranch
            copyArtifactPermissionProperty {
                projectNames('/**')
//                 projectNames('INFRA/repo-test-automation/**')
            }
        }
        definition {
            logRotator {
               daysToKeep(-1)
               numToKeep(40)
               artifactNumToKeep(-1)
               artifactDaysToKeep(-1)
            }
            cps {
                script("runMoleculePipeline()")
                sandbox()
            }
        }
        throttleConcurrentBuilds {
            categories(['ansible_test'])
        }
    }
}
