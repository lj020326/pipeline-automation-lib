#!/usr/bin/env groovy
import groovy.json.JsonOutput

import com.dettonville.pipeline.utils.JsonUtils

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map params=[:]) {

    Map config = loadPipelineConfig(params)
    Map jobResults = [:]

    pipeline {
        agent {
            label "docker"
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '20'))
            skipDefaultCheckout(false)
            disableConcurrentBuilds()
            ansiColor('xterm')
            timestamps()
            timeout(time: config.timeout as Integer, unit: config.timeoutUnit)
        }

        stages {
            stage('Extract Metadata') {
                steps {
                    script {
                        // Extract metadata or parse galaxy.yml if needed
                        echo "Extracting collection metadata..."
                    }
                }
            }

            stage('Ansible Lint') {
                steps {
                    script {
                        runAnsibleLint()
                    }
                }
            }

            stage('Sanity Testing') {
                steps {
                    script {
                        runAnsibleTestManifest(config)
                    }
                }
            }

            stage('Unit Testing') {
                steps {
                    script {
                        runAnsibleTestManifest(config)
                    }
                }
            }

            stage('Publish to Pulp') {
                when {
                    branch 'main'
                }
                steps {
                    script {
                        withCredentials([string(credentialsId: 'pulp-api-token', variable: 'PULP_API_KEY')]) {
                            sh '''
                                ansible-galaxy collection build
                                # Find the built tarball and publish to your local Pulp registry
                                TARBALL=$(ls dettonville-utils-*.tar.gz)
                                ansible-galaxy collection publish "$TARBALL" \
                                    --api-key "$PULP_API_KEY" \
                                    --server "https://pulp.media.johnson.int/pulp/api/v3/plugin/ansible/ansible/"
                            '''
                        }
                    }
                }
            }
        }
        post {
            always {
                script {
                    notifyGitRemoteRepo(
                        config.gitRemoteRepoType,
                        gitRemoteBuildKey: config.gitRemoteBuildKey,
                        gitRemoteBuildName: config.gitRemoteBuildName,
                        gitRemoteBuildStatus: config.gitRemoteBuildStatus,
                        gitRemoteBuildSummary: config.gitRemoteBuildSummary,
                        gitRemoteBuildConclusion: config.gitRemoteBuildConclusion,
                        gitCommitId: config.gitCommitId
                    )
                    if (config?.alwaysEmailList) {
                        log.info("config.alwaysEmailList=${config.alwaysEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.alwaysEmailList.split(","))
                    } else {
                        sendEmail(currentBuild, env)
                    }
                    log.info("Empty current workspace dir")
                    try {
                        cleanWs()
                    } catch (Exception ex) {
                        log.warn("Unable to cleanup workspace - e.g., likely cause git clone failure", ex.getMessage())
                    }
                }
            }
            success {
                script {
                    if (config?.successEmailList) {
                        log.info("config.successEmailList=${config.successEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.successEmailList.split(","))
                    }
                }
            }
            failure {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            aborted {
                script {
                    if (config?.failedEmailList) {
                        log.info("config.failedEmailList=${config.failedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.failedEmailList.split(","))
                    }
                }
            }
            changed {
                script {
                    if (config?.changedEmailList) {
                        log.info("config.changedEmailList=${config.changedEmailList}")
                        sendEmail(currentBuild, env, emailAdditionalDistList: config.changedEmailList.split(","))
                    }
                }
            }
        }
    }
} // body

Map loadPipelineConfig(Map params = [:]) {
    log.debug("copy immutable params map to mutable config map")
    log.debug("params=${JsonUtils.printToJsonString(params)}")
//     Map config = MapMerge.merge(config, params)
    Map config = params.clone()

    config.get('logLevel', "INFO")
//     config.get('logLevel', "DEBUG")
    config.get('timeout', "5")
    config.get('timeoutUnit', "HOURS")
    config.get('debugPipeline', false)
    config.get('wait', true)
    config.get('failFast', false)
    config.get('propagate', config.failFast)
    config.get('maxRandomDelaySeconds', "10")
    config.get('childJobTimeout', "4")
    config.get('childJobTimeoutUnit', "HOURS")
    config.get("copyChildJobArtifacts", false)

    config.get('runGroupsInParallel', false)
    config.get('runInParallel', false)
    config.get('parallelJobsBatchSize', 0)

//     config.get("gitCredentialsId", "gitea-ssh-jenkins")
    config.get("gitCredentialsId", "infra-jenkins-git-user")

//    config.get("registryUrl","https://registry.media.johnson.int:5000")
    config.get("registryUrl","https://media.johnson.int:5000")
//    config.get("registryUrl","https://media.dettonville.int:5000")
    config.get("registryCredId", "docker-registry-admin")

    config.get("buildImageName", "${env.JOB_NAME.split('/')[-2]}")
    config.get("buildDir", ".")
    config.get("buildPath", ".")
    config.get("buildTestAppendIdArg", false)

    config.get('buildReportDir','build')
    config.get('buildReport',"${config.buildReportDir}/build-results.yml")

    // ref: https://stackoverflow.com/questions/40261710/getting-current-timestamp-in-inline-pipeline-script-using-pipeline-plugin-of-hud
    Date now = new Date()

    String buildDate = now.format("yyyy-MM-dd", TimeZone.getTimeZone('UTC'))
    String buildId = "build-${env.BUILD_NUMBER}"

    config.get("buildId", buildId)
    config.get("buildDate", buildDate)

    log.setLevel(config.logLevel)

    if (config.debugPipeline) {
        log.setLevel(LogLevel.DEBUG)
    }
    log.debug("log.level=${log.level}")

    config.get('configFileSanity', ".jenkins/ansible-test-sanity.yml")
    config.get('configFileUnits', ".jenkins/ansible-test-units.yml")

    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}
