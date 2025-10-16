#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-to-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.DEBUG)
@Field Logger log = new Logger(this)

Map call() {

    // Simple Declarative Pipeline to test and verify copying artifacts
    pipeline {
        agent {
            label "docker"
        }
        stages {
            stage('Initialize') {
                steps {
                    script {
                        log.info("Starting pipeline to copy test artifacts...")
                    }
                }
            }

            stage('Copy Artifacts from Existing Job') {
                steps {
                    script {
                        String buildNumber = "212"
                        // The 'copyArtifacts' step requires the 'Copy Artifact Plugin' to be installed.
                        // We use the full job path provided by the user as the projectName.
                        copyArtifacts(
                            // 1. PROJECT NAME: The full path/name of the job to copy artifacts *from*.
                            projectName: '/INFRA/repo-test-automation/run-ansible-test',

                            // 2. SELECTOR: Specifies which build to copy from (e.g., last successful, specific build number).
//                             selector: lastSuccessful(),
                            selector: specific(buildNumber),

                            // 3. ARTIFACTS: The file pattern (glob) *relative to the source job's artifact archive*.
                            // This example copies all files archived by the source job.
//                             filter: '**/*',
                            filter: '**/*.xml',

                            fingerprintArtifacts: true,

                            // 4. TARGET DIRECTORY: The subdirectory in the current job's workspace where artifacts will be placed.
//                             target: 'test-artifacts-collection'
                        )
                        log.info("Artifacts copied into the 'test-artifacts-collection' directory.")
                    }
                }
            }

            stage('Verify Artifacts') {
                steps {
                    script {
                        log.info("Contents of the target directory:")
                        // Use 'sh' (or 'bat' for Windows) to list the copied files recursively
//                         sh 'ls -R test-artifacts-collection'
                        sh 'find . -type f'

                        // FIX: Separated the logical check from the print statement to avoid the Groovy/Shell ClassCastException.
                        // The 'sh' step will automatically fail the pipeline if 'test -d' (directory not found) returns a non-zero exit code.
//                         sh 'test -d test-artifacts-collection'
                        sh 'test -d ansible_collections'
                        log.info("Artifact copy test PASSED. Directory exists.")
                    }
                }
            }
        }
        post {
            always {
                script {
                    log.info("Archiving test results")
                    try {
                        archiveArtifacts artifacts: 'ansible_collections/**/tests/output/**', fingerprint: true, allowEmptyArchive: true
                        log.info("Archived artifacts successfully")
                    } catch (Exception archiveErr) {
                        log.warn("Failed to archive artifacts: ${archiveErr.message}")
                    }
                    log.info("Recording JUnit results")
                    try {
                        junit(
                            testResults: 'ansible_collections/**/tests/output/junit/*.xml',
                            skipPublishingChecks: true,
                            allowEmptyResults: true
                        )
                        log.info("Recorded JUnit results successfully")
                    } catch (Exception archiveErr) {
                        log.warn("Failed to record JUnit: ${archiveErr.message}")
                    }

                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }
        }
    }
}
