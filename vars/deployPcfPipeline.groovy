#!/usr/bin/env groovy

// ref: https://fusion.dettonville.int/stash/projects/AA/repos/cardcase/raw/Jenkinsfile

// Jenkins Declarative Pipeline for multi-branch project
// @author Roman Slipchenko (Roman_Slipchenko@epam.com)
// Conventions:
// Upper-case variables - Jenkins default environment variables, e.g. env.JOB_NAME
// Lower-case variables - custom environment variables, e.g. env.groupId
// Notes:
// Spring Boot version - 1.5.3
// Gradle Wrapper version - 4.1
// Gradle daemon is disabled on CI env:
// org.gradle.daemon=false added to local gradle.properties
properties(
        [
                [$class: 'ParametersDefinitionProperty',
                 parameterDefinitions:[
                         booleanParam(defaultValue: false,
                                 description: 'If true - deploy to GlobalPrepaid-LoadTesting environment', name: 'LoadTesting'),
                         booleanParam(defaultValue: false,
                                 description: 'If true - create Dettonville PCF-specific artifact', name: 'Dettonville_PCF_build'),
                         booleanParam(defaultValue: false,
                                 description: 'If true - rollback to specified release version', name: 'Rollback'),
                         // Maven Metadata Plugin
                         [$class: 'MavenMetadataParameterDefinition', artifactId: 'macd-plat-epam', credentialsId: 'jenkins-ci-nexus',
                          defaultValue: 'FIRST',
                          description: 'Release artifact version that will be used if Rollback parameter == true',
                          groupId: 'com.epam.macd', name: 'release_artifact', packaging: 'jar',
                          sortOrder: 'DESC',
                          repoBaseUrl: 'http://ecsa00400590.epam.com:8081/repository/macd-plat-backend-releases/'
                         ]
                 ]
                ]
        ]
)

pipeline {
    // agent any
    agent { label 'java-backend' }
    options {
        skipDefaultCheckout()
        // From Timestamper plugin
        timestamps()
        // Only keep the 10 most recent builds
        buildDiscarder(logRotator(numToKeepStr:'10', artifactNumToKeepStr: '5'))
        // Limit pipeline execution time to 30 min.
        timeout(time: 30, unit: 'MINUTES')
    }
    tools {
        jdk 'java8'
    }
    environment {
        vaultPassword = credentials('vault-pass')
        sonarApiToken = credentials('sonar-api-token')
        nexusCreds    = credentials('jenkins-ci-nexus')
    }
    stages {
        stage ('Cleanup Workspace') {
            steps { deleteDir() }
        }
        // Checkout
        stage ('Checkout SCM') {
            steps {
                parallel (
                        CheckoutApplicationCode: {
                            git credentialsId: 'macd-plat-gitlab',
                                    url: 'git@git.epam.com:macd-plat/macd-plat.git',
                                    branch: "${env.BRANCH_NAME}"
                        },
                        CheckoutAcceptanceTestsCode: {
                            // Create separate directory for api tests
                            retry (2) {
                                dir('api-testing') {
                                    writeFile file: 'empty.dummy', text: ''
                                    git credentialsId: 'macd-plat-gitlab',
                                            url: 'git@git.epam.com:macd-plat/macd-api-testing.git',
                                            branch: 'master'
                                }
                            }
                        }
                )
            }
        }
        // Ansible should be installed on agent
        // This step should be used for protecting sensitive data in public-available repos or for
        // any other similar activities. When new credentials etc. need to be added to gradle.properties -
        // should be added to encrypted by Ansible Vault .properties file as well
        stage ('Decrypt gradle.properties') {
            steps {
                dir('ansible') {
                    ansiblePlaybook(
                            playbook: 'site.yml',
                            inventory: 'hosts',
                            extras: '--vault-password-file ./vault_pass.py'
                    )
                }
            }
        }
        // Env. variables
        stage ('Set environment variables') {
            steps {
                script {
                    // Pipeline Utility Steps plugin is required to read *.properties file
                    def gradleProps          = readProperties file: 'gradle.properties'
                    env.groupId              = gradleProps.groupId
                    env.appVersion           = gradleProps.version
                    env.appName              = gradleProps.appName
                    env.sonarQGUrl           = gradleProps.sonarQGUrl
                    env.mainline             = 'develop'
                    env.loadTestingEnv       = "${params.LoadTesting}"
                    env.Rollback             = "${params.Rollback}"
                    // Maven metadata plugin
                    // Workaround due to bug:
                    // https://issues.jenkins-ci.org/browse/JENKINS-38619
                    // Parameters mapping here:
                    // https://github.com/jenkinsci/maven-metadata-plugin/blob/master/src/main/resources/eu/markov/jenkins/plugin/mvnmeta/MavenMetadataParameterValue/value.jelly
                    if (Rollback == 'true') {
                        String r_version         = currentBuild.rawBuild.getAction(hudson.model.ParametersAction).getParameter("release_artifact").version
                        env.rollbackVersion      = r_version
                        String r_url             = currentBuild.rawBuild.getAction(hudson.model.ParametersAction).getParameter("release_artifact").artifactUrl
                        env.rollbackArttifactUrl = r_url
                        echo "Rollback: ${env.Rollback}"
                        echo "Rollback version: ${env.rollbackVersion}"
                        echo "Rollback artifact: ${env.rollbackArttifactUrl}"
                    }
                    env.GitCommitAuthor      = sh script: '''
                                                    git --no-pager show -s --format="%ae" |\
                                                    sed s/@epam.com//g | tr "[:upper:]" "[:lower:]" |\
                                                    xargs echo -n
                                                    ''', returnStdout: true
                }
                echo "App.name: ${env.appName}, version: ${env.appVersion}"
                echo "Git commit author: ${env.GitCommitAuthor}"

            }
        }
        // Start
        stage ('Pipeline start') {
            steps {
                slackSend color: '#FFFF00', channel: 'ci_cd',
                        message: "STARTED: Job: ${env.JOB_NAME}, Application: ${env.appName} - [Build #: ${env.BUILD_ID}] (${env.BUILD_URL})"
            }
        }
        // Set app.version for /version endpoint
        stage ('Set build info to application.yml') {
            when {
                allOf {
                    not { expression { return params.Rollback } }
                    not { expression { return params.LoadTesting } }
                }
            }
            steps {
                sh '''
                    sed -i -E "s,\\s\\sversion:.*,  version: ${appVersion},g" src/main/resources/application.yml &&
                    sed -i -E "s,build_id:.*,build_id: ${BUILD_ID}_${BRANCH_NAME},g" src/main/resources/application.yml &&
                    sed -i -E "s/timestamp:.*/timestamp: ${BUILD_TIMESTAMP}/g" src/main/resources/application.yml
                   '''
            }
        }
        // Unit test
        stage ('Unit tests') {
            when {
                allOf {
                    not { expression { return params.Rollback } }
                    not { expression { return params.LoadTesting } }
                }
            }
            steps {
                echo 'Execute unit tests...'
                sh './gradlew test'
                echo 'Done.'
            }
            post {
                always {
                    echo 'Publishing JUnit test results.'
                    junit 'build/test-results/**/*.xml'
                    echo 'Done.'
                }
            }
        }
        // Build
        // TO-DO
        // Add rollback and parameter for check
        stage ('Build artifacts') {
            when { not { expression { return params.Rollback } } }
            steps {
                // gradle install task required for acceptance tests
                sh './gradlew install'
                sh './gradlew build -x check -x test -i'
            }
        }
        // Analysis
        stage ('Code analysis and acceptance tests') {
            when {
                allOf {
                    not { expression { return params.Rollback } }
                    not { expression { return params.LoadTesting } }
                }
            }
            steps {
                parallel (
                        SonarQubeAnalysis: {
                            script {
                                sh "./gradlew sonarqube -Dsonar.login=${sonarApiToken} -i"
                            }
                        },
                        // Acceptance tests
                        AcceptanceTests: {
                            dir('api-testing') {
                                sh './gradlew test --stacktrace --rerun-tasks'
                                // echo 'Acceptance tests temporarily disabled'
                            }
                        }
                )
            }
            post {
                always {
                    echo 'Publishing Cucumber report...'
                    //generate cucumber reports
                    cucumber '**/*.json'
                    echo 'Done.'
                }
            }
        }
        // Quality Gate
        stage ('Quality Gate') {
            when {
                allOf {
                    not { expression { return params.Rollback } }
                    not { expression { return params.LoadTesting } }
                }
            }
            // API call to SonarQube
            // API token generated for Jenkins pseudo-user in SonarQube
            steps {
                sleep 2
                script {
                    println("Get Quality Gate status...")
                    // jq required on CI/build server/agent
                    sh '''
                       curl -s -u ${sonarApiToken}: \
                       ${sonarQGUrl}=macd-plat:${BRANCH_NAME} |\
                       jq ".projectStatus.status" > qg_status
                       '''
                    qgStatus = readFile 'qg_status'
                    println("Quality Gate status is " + qgStatus)
                    if (qgStatus.trim() == "\"OK\"") {
                        println "Quality Gate passed"
                    } else if (qgStatus.trim() == "\"NONE\"") {
                        println "Quality Gate is not assigned! Check SonarQube configuration"
                        sh 'exit 1'
                    } else {
                        println "Quality Gate is RED!"
                        sh 'exit 1'
                    }
                }
            }
        }
        // Publish
        stage ('Publish SNAPSHOT artifacts') {
            when {
                allOf {
                    branch 'develop'
                    not { expression { return params.LoadTesting } }
                    not { expression { return params.Rollback } }
                }
            }
            steps {
                echo 'Publishing SNAPSHOT artifacts to Nexus using Gradle...'
                sh './gradlew publish -x jar -x bootJar --info'
                echo 'Published.'
            }
        }
        // Release
        stage ('Release artifacts and update version') {
            when {
                allOf {
                    expression { BRANCH_NAME ==~ /(master|release*)/ }
                    not { expression { return params.LoadTesting } }
                    not { expression { return params.Rollback } }
                    not { expression { return params.Dettonville_PCF_build } }
                }
            }
            steps {
                echo 'Releasing artifacts and publish them to release Nexus repo...'
                sh 'git commit -a -m "Commited during release"'
                sh './gradlew release -x test -x check -Prelease.useAutomaticVersion=true'
                // Copy updated properties to ansible role
                sh '''
                cd ansible/roles/encrypted_properties/files && \
                cp ${WORKSPACE}/gradle.properties gradle.properties.encr && \
                ansible-vault encrypt gradle.properties.encr --vault-password-file ../../../vault_pass.py && \
                git add gradle.properties.encr && git commit --amend --no-edit
                '''
                sh 'git pull origin ${mainline} && git checkout ${mainline} && git merge master && git push'
                echo 'Published.'
            }
        }
        stage ('Rollback') {
            when { expression { return params.Rollback } }
            steps {
                dir('build/libs') {
                    sh "wget -q --user=${nexusCreds_USR} --password=${nexusCreds_PSW} ${rollbackArttifactUrl}"
                }
            }
        }
        // Delivery
        stage ('Deploy to Pivotal Web Services') {
            // Temporary disable deploy to stage env.
            when {
                allOf {
                    not { branch 'master' }
                    not { expression { return params.Dettonville_PCF_build } }
                }
            }
            steps {
                sh './gradlew cf-push --stacktrace'
            }
        }
        // DB migration
        stage ('Liquibase DB migration') {
            // Temporary disable DB migration to stage env.
            when {
                allOf {
                    not { branch 'master' }
                    not { expression { return params.Dettonville_PCF_build } }
                }
            }
            steps {
                sh './gradlew cf-stop-app --stacktrace'
                sh './gradlew init-db update'
                sh './gradlew cf-start-app --stacktrace'
            }
        }
    }
    post {
        // Notify
        success {
            slackSend color: '#00FF00', channel: 'ci_cd',
                    message: "SUCCESSFUL: Job: ${env.JOB_NAME}, Application: ${env.appName} - [Build #: ${env.BUILD_ID}] (${env.BUILD_URL})"
        }
        failure {
            slackSend color: '#FF0000', channel: 'ci_cd',
                    message: "FAILED: Job: ${env.JOB_NAME}, Application: ${env.appName} - [Build #: ${env.BUILD_ID}] (${env.BUILD_URL})"
        }
        // Jenkins bug https://issues.jenkins-ci.org/browse/JENKINS-43339
        aborted {
            slackSend color: '#808080', channel: 'ci_cd',
                    message: "ABORTED: Job: ${env.JOB_NAME}, Application: ${env.appName} - [Build #: ${env.BUILD_ID}] (${env.BUILD_URL})"
        }
        // Always cleanup workspace
        always {
            deleteDir()
        }
    }
}
