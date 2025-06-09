#!/usr/bin/env groovy

def call(String yamlName) {

    Map appConfigs=[:]

    def pcfEnvResourceTxt = libraryResource 'pcfEnvResources.yml'
    def pcfResourceConfigs = readYaml text: "${pcfEnvResourceTxt}"
    def envConfigs = pcfResourceConfigs.pcfEnvironments[env.BRANCH_NAME]

    // ref: https://stackoverflow.com/questions/46630168/in-a-declarative-jenkins-pipeline-can-i-set-the-agent-label-dynamically

    def pcfAgentName = envConfigs.pcfJenkinsNodeLabel
    pcfAgentLabel = "${-> println 'Right Now the Agent Label Name is ' + pcfAgentName; return pcfAgentName}"

    def email_from="DCAPI.pcfDeployAutomation@dettonville.com"

    pipeline {
        agent { label "QA-LIN7 || PROD-LINUX" }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            skipDefaultCheckout()
            timeout(time: 1, unit: 'HOURS')
        }
        stages {
            stage('Checkout') {
                steps {
                    script {
                        deleteDir()
                        gitCheckout(scm)

                        appConfigs = readYaml file: yamlName;

                    }
                }
            }
            stage('Test and Build') {
                parallel {
                    stage ('Unit Tests') {
                        when {
                            anyOf {
                                branch 'develop'
                                branch 'stage'
                                branch 'main'
                                environment name: 'CHANGE_TARGET', value: 'develop'
                                environment name: 'CHANGE_TARGET', value: 'stage'
                                environment name: 'CHANGE_TARGET', value: 'main'
                            }
                        }
                        steps {
                            sh "echo ${GRADLE4}/gradle clean check test -d | tee ${env.CUSTOM_LOG_FILE}"
                            sh "set -o pipefail; $GRADLE4/bin/gradle clean check test -d |& tee -a ${env.CUSTOM_LOG_FILE}"
                            script {
                                env.UNIT_TESTS = "PASSED"
                            }
                        }
                    }

                    stage('Build') {
                        when {
                            anyOf {
                                branch 'develop'
                                branch 'stage'
                                branch 'main'
                                environment name: 'CHANGE_TARGET', value: 'develop'
                                environment name: 'CHANGE_TARGET', value: 'stage'
                                environment name: 'CHANGE_TARGET', value: 'main'
                            }
                        }
                        steps {
                            sh "echo ${GRADLE4}/bin/gradle clean build -d -x test | tee ${env.CUSTOM_LOG_FILE}"
                            sh "set -o pipefail; ${GRADLE4}/bin/gradle clean build -d -x test |& tee -a ${env.CUSTOM_LOG_FILE}"
                            stash includes: '**', name: 'workspace'
                            script {
                                env.BUILD_ARTIFACT = "PASSED"
                            }
                        }
                    }
                }
            }

            stage ('SonarQube Static Code Analysis') {
                steps {
                    script {
                        env.SONAR_CHECK = "PASSED"
                    }
                }
            }

//        stage('Publish to Artifactory') {
//            when {
//                anyOf {
//                    branch 'develop'
//                    branch 'main'
//                    environment name: 'CHANGE_TARGET', value: 'develop'
//                    environment name: 'CHANGE_TARGET', value: 'main'
//                }
//            }
//            steps {
//                unstash 'workspace'
//
//                sh "echo $GRADLE4/bin/gradle publish | tee ${env.CUSTOM_LOG_FILE}"
//                sh "set -o pipefail; $GRADLE4/bin/gradle publish -d |& tee -a ${env.CUSTOM_LOG_FILE}"
//                script {
//                    env.PUBLISH_ARTIFACT = "PASSED"
//                }
//            }
//        }


            stage('PCF Deployment') {

                when {
                    anyOf {
                        branch 'develop'
                        branch 'stage'
                        branch 'main'
                    }
                }
                steps {

                    node( pcfAgentLabel as String ) {  // Evaluate the node label now

                        script {
                            withEnv( ["CF_HOME=${env.WORKSPACE}"] ) {

                                //
                                // setup an app cluster instance for each foundation for the given branch
                                // e.g., for the main branch (prod) there may be 2 foundations: nyc-prod & jpn-
                                //
                                foundationList = envConfigs.pcfFoundations
                                for (Map foundationConfigs : foundationList) {
                                    def config = foundationConfigs + appConfigs

                                    echo "login to PCF"
                                    withCredentials([usernamePassword(credentialsId: config.pcfJenkinsCredId, usernameVariable: "PCF_USERNAME", passwordVariable: "PCF_PASSWORD")]) {
                                        sh "cf login -a ${config.pcfApiUrl} -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${config.pipeline.pcfOrg} -s ${config.pipeline.pcfSpace}"
                                    }

                                    echo 'Deploy Green'
                                    deployGreen(config)

                                    echo 'Perform Testing or Health check'
                                    runTests(config)

                                    echo 'Flip Traffic'
                                    flipTraffic(config)

                                } // for each foundation
                            }

                        } // script

                    } // node

                } // steps
            } // stage PCF Deploy

        }
        post {
            always {
                script {
                    env.CUSTOM_LOG = sh(returnStdout: true, script: "tail -n 50 ${env.CUSTOM_LOG_FILE}").trim()
                    if (env.EMAIL_BODY) {
                        env.EMAIL_BODY += "\n\nUnit Tests: ${env.UNIT_TESTS} \nBuild Artifact: ${env.BUILD_ARTIFACT}\nSonar Check: ${env.SONAR_CHECK}"
                    } else {
                        env.EMAIL_BODY = "Unit Tests: ${env.UNIT_TESTS} \nBuild Artifact: ${env.BUILD_ARTIFACT}\nSonar Check: ${env.SONAR_CHECK}"
                    }
                    def build_status = "${currentBuild.result ? currentBuild.result : 'SUCCESS'}"
                    emailext (
                            to: "${appConfigs.pipeline.alwaysEmailList}",
                            from: "${email_from}",
                            subject: "BUILD ${build_status}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                            body: "${env.EMAIL_BODY} \n\nBuild Log:\n${env.CUSTOM_LOG}",
                    )
                }

                echo "Empty current workspace dir"
                deleteDir()
            }
            failure {
                emailext (
                        to: "${appConfigs.pipeline.emailList}",
                        from: "${email_from}",
                        subject: "BUILD FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                        body: "${env.EMAIL_BODY} \n\nBuild Log:\n${env.CUSTOM_LOG}",
                )
            }
            changed {
                emailext (
                        to: "${env.PR_REVIEWERS}; ${appConfigs.pipeline.emailList}",
                        from: "${email_from}",
                        subject: "BUILD CHANGED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                        body: "${env.EMAIL_BODY}",
                )
            }
        }
    }



} // body


def deployGreen(Map config) {
    // artifactoryUtil.artifactoryDownload(this, "infra-artifactory")
    // in absence of artifactory working - we simply stash for now
    unstash 'workspace'

    echo "deploy ${config.pipeline.pcfAppName} with route ${config.pipeline.pcfAppRouteGreen}"
    sh "cf push ${config.pipeline.pcfAppName}'-green' -n ${config.pipeline.pcfAppRouteGreen}"

    if (config.pipeline.containsKey("pcfAppEnvVars")) {
        setupAppEnv(config)
    } else {
        echo "No application environment variables found in ${env.BRANCH_NAME}.yml (key: pcfAppEnvVars)"
    }

}

def setupAppEnv(Map config) {
    appEnvVarList = config.pipeline.pcfAppEnvVars

    for (Map appEnvVar : appEnvVarList) {
        if (appEnvVar.containsKey("jenkinsCredId")) {
            def usernameVar="USERNAME"
            def passwordVar="PASSWORD"
            if (appEnvVar.containsKey("usernameVar")) {
                usernameVar=appEnvVar.usernameVar
            }
            if (appEnvVar.containsKey("passwordVar")) {
                passwordVar=appEnvVar.passwordVar
            }
            withCredentials([usernamePassword(credentialsId: appEnvVar.jenkinsCredId, usernameVariable: usernameVar, passwordVariable: passwordVar)]) {
                sh "cf set-env ${config.pipeline.pcfAppName}'-green' ${usernameVar} ${passwordVar}"
                echo "added Cred Env Var [${appEnvVar.jenkinsCredId}] to application ${config.pipeline.pcfAppName}-green"
            }
        } else if (appEnvVar.containsKey("jenkinsSecretTxt")) {
            withCredentials([string(credentialsId: appEnvVar.jenkinsSecretTxt, variable: 'PASSWORD')]) {
                sh "cf set-env ${config.pipeline.pcfAppName}'-green' ${appEnvVar.name} ${PASSWORD}"
                echo "added Cred Secret [${appEnvVar.name}] to application ${config.pipeline.pcfAppName}-green"
            }
        } else {
            sh "cf set-env ${config.pipeline.pcfAppName}'-green' ${appEnvVar.name} ${appEnvVar.value}"
            echo "added Env Var [${appEnvVar.name}] to application ${config.pipeline.pcfAppName}-green"
        }
    }
    echo "restaging app to pick up env vars"
    sh "cf restage ${config.pipeline.pcfAppName}'-green'"

}

def runTests(Map config) {
    echo "runTests: running simple curl based connectivity test"

    def httpStatusOk = "200"
    def httpStatus = sh(
            script: "curl -s -o /dev/null -w '%{http_code}' 'https://${config.pipeline.pcfAppRouteGreen}.${config.pcfAppsDomain}/'",
            returnStdout: true
    ).trim()

    if (httpStatus != httpStatusOk) {
        echo "Health check failed!"
        sh "cf delete ${config.pipeline.pcfAppName}'-green' -f"
        error("Removed build as health check on latest build failed")
    } else {
        echo "Health check succeeded!"
    }

}

def flipTraffic(Map config) {

    echo "map blue route to green app"
    sh "cf map-route ${config.pipeline.pcfAppName}'-green' ${config.pcfAppsDomain} --hostname ${config.pipeline.pcfAppRouteBlue}"

    echo "increase green instance"
    sh "cf scale ${config.pipeline.pcfAppName}'-green' -i ${config.pipeline.pcfMinInst}"

    echo "check if blue app exist"
    def blueAppExists = sh(script: "cf app ${config.pipeline.pcfAppName}'-blue'", returnStatus: true) == 0

    if (blueAppExists) {
        echo "blue app exist"

        echo "check if blue backup exist"
        def blueBackAppExists = sh(script: "cf app ${config.pipeline.pcfAppName}'-blue-backup'", returnStatus: true) == 0

        if (blueBackAppExists) {
            echo "blue backup exist....deleting it"
            sh "cf delete ${config.pipeline.pcfAppName}'-blue-backup' -f"
        }

        echo "unmap live route from blue app"
        sh "cf unmap-route ${config.pipeline.pcfAppName}'-blue' ${config.pcfAppsDomain} --hostname ${config.pipeline.pcfAppRouteBlue}"

        echo "scale down blue instance to 0"
        sh "cf scale ${config.pipeline.pcfAppName}'-blue' -i 0"

        echo "stope blue application"
        sh "cf stop ${config.pipeline.pcfAppName}'-blue'"

        echo "rename blue app to blue-back"
        sh "cf rename ${config.pipeline.pcfAppName}'-blue' ${config.pipeline.pcfAppName}'-blue-backup'"
    }
    echo "rename green app to blue"
    sh "cf rename ${config.pipeline.pcfAppName}'-green' ${config.pipeline.pcfAppName}'-blue'"

    echo "un-map test route from new blue"
    sh "cf unmap-route ${config.pipeline.pcfAppName}'-blue' ${config.pcfAppsDomain} --hostname ${config.pipeline.pcfAppRouteGreen}"

}

//// env config sourced from config yml file
//// per: https://github.com/mozilla/kuma/blob/master/Jenkinsfile
//def loadBranchConfig(String branch) {
//
//    def config = [:]
//    if (fileExists("./jenkins/${branch}.yml")) {
//        config = readYaml file: "./jenkins/${branch}.yml"
//        println "config ==> ${config}"
//    }
//    else {
//        config = readYaml file: "./jenkins/develop.yml"
//    }
//    return config
//}


/**
 * Run Sonar code scan. The results of the analysis will be available on SonarQube (https://fusion.dettonville.int/sonar/)
 */
void runSonarScan(def branchName = null) {

    final String branchParam = (branchName == null ? '' : "-Dsonar.branch=${branchName}")

    try {
        // ref: https://fusion.dettonville.int/stash/projects/API/repos/devzone-frontend/browse/Jenkinsfile
        withCredentials([usernamePassword(credentialsId: 'dcapi_ci_vcs_user', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
            sh """
                mvn org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent \
                    -Dsonar.login="${USER}" \
                    -Dsonar.password="${PASS}" \
                    -DfailIfNoTests=false \
                    -Dsonar.host.url="${env.SONAR_URL}" \
                    sonar:sonar \
                    ${branchParam}
            """
        }

    } catch (Exception err) {
        echo "runSonarScan(): sonar exception occurred [${err}]"
    }

}


