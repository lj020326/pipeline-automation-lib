
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

String baseFolder = "INFRA"

log.info("${scriptName}: JENKINS_ENV=${JENKINS_ENV}")

jobFolder = "${baseFolder}/bootstrap-reference-repos"

log.info("${scriptName}: Creating ${jobFolder} job")

pipelineJob(jobFolder) {
    parameters {
		stringParam('JenkinsNodeLabel', 'controller', "Specify the Jenkins node/label")
    }
    definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
        cps {
            script("bootstrapReferenceReposJob()")
            sandbox()
        }
    }
}


jobFolder = "${baseFolder}/bootstrap-all-reference-repos"
log.info("${scriptName}: Creating ${jobFolder} job")

pipelineJob(jobFolder) {
    definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
        cps {
            script("bootstrapAllReferenceReposJob()")
            sandbox()
        }
    }
//     triggers {
//       cron('@midnight')
//     }
}

log.info("${scriptName}: Finished creating reference repo jobs")
