// Jenkinsfile for the ADMIN/bootstrap-projects job

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger
// import com.dettonville.pipeline.utils.JsonUtils

import groovy.json.JsonOutput

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
// @Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

// Change to a List of String
// Add other jobs here if needed, e.g., "SOME_FOLDER/another-job"
List<String> jobsToTrigger = [
    "ADMIN/reset-SSH-hostkeys",
    "INFRA/build-docker-image",
    "INFRA/repo-test-automation/run-ansible-test",
    "INFRA/repo-test-automation/run-molecule"
]

pipeline {
    agent { label 'controller' } // Use the 'controller' label as defined in your Job DSL

    triggers {
        // Cron schedule for 6 a.m. and 6 p.m. every day.
        cron('0 6,18 * * *')
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(false)
        disableConcurrentBuilds()
        timestamps()
    }

    stages {
        stage('Run Job DSL Scripts') {
            steps {
                script {
                    // This step will process all your Job DSL definition files
                    // from the 'jobs/jobdsl/templates' directory.
                    jobDsl(
                        targets: 'jobs/jobdsl/templates/**/*.groovy',
                        removedJobAction: 'DELETE',
                        additionalClasspath: "vars/" + "\r\n" + "src/")
                    log.info("All Jenkins jobs created/updated by Job DSL.")
                }
            }
        }

        stage('Trigger Initial Pipeline Runs for Parameter Initialization') {
            when {
                expression { !jobsToTrigger.isEmpty() }
            }
            steps {
                script {

                    if (jobsToTrigger.isEmpty()) {
                        log.info("No jobs specified in JOBS_TO_TRIGGER. Skipping initial triggers.")
                    } else {
                        log.info("Triggering initial runs for: ${JsonOutput.prettyPrint(JsonOutput.toJson(jobsToTrigger))}")

                        jobsToTrigger.each { jobName ->
                            log.info("Triggering: ${jobName}")
                            // NOTE: each job is expected to support a hidden parameter
                            //    that allows running for parameter initialization only
                            build job: jobName, wait: false,
                                parameters: [
                                    [$class: 'BooleanParameterValue', name: 'InitializeParamsOnly', value: true]
                                ]
                        }
                    }
                }
            }
        }
    }
}
