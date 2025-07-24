#!groovy

// ref: https://fusion.dettonville.int/stash/projects/MPASS/repos/dcuser-wallet-cookbook/browse/static/documents/blue-green-deployment/blue-green-deploy-Snapshot-Jenkinsfile

def pcfUtil = new com.dettonville.pipeline.utility.PCFUtil(this)
def caasUtil = new com.dettonville.pipeline.utility.CaaSUtil(this)

def greenAppName = "mobile-wallet-service-green"
def blueAppName = "mobile-wallet-service-blue"
def routeName = "mobile-wallet-service"
def routeDomain = "apps.nyc.pcfstage00.dettonville.int"
def cnName = "mpdirect-mobile-services"
def spaceName = "mpdirect-wallet-services"
def paasUrl = "api.system.nyc.pcfstage00.dettonville.int"
def org = "dcuser"
//setting profile to null so that the profile is picked up from
//the manifest and not changed at deploy time.  If you wish to change
//at deploy time set the profile here.
def activeProfile = null
def keyMap = [:]

def releaseArtifactUrlValue = params.artifact_url

pipeline {
    agent {
        node {
            label "CF-CLI && DTL"
        }
    }

    stages {
        stage("get release artifact") {
            agent { label "M3" }
            steps {
                script {
                    def baseURL = "https://gitrepository.dettonville.int/artifactory/star"
                    dir("artifact") {
                        sh "curl -o mpwlw-wallet-mobile-services.war ${baseURL}${releaseArtifactUrlValue}"
                    }
                    stash includes: '**', name: 'workspace'
                }
            }
        }
        stage("get certs - staging") {
            agent { label "QA-CAAS-CLIENT" }
            steps {
                script {
                    caasUtil.getJKSFromCaaS(this, routeName, "nyc-stage", keyMap, cnName)
                }
            }
        }
        stage("update any existing blue/green apps") {
            agent { label "CF-CLI && DTL" }
            steps {
                dir("ci/blue-green-stage-dev") {
                    withEnv(["CF_HOME=."]) {
                        script {
                            withCredentials([[$class: "UsernamePasswordMultiBinding", credentialsId: "pcf_stage", usernameVariable: "PCF_USERNAME", passwordVariable: "PCF_PASSWORD"]]) {
                                sh "cf login -a ${paasUrl} -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${spaceName}"
                            }

                            def blueAppExists = sh (script: "cf app ${blueAppName}", returnStatus: true) == 0
                            def greenAppExists = sh (script: "cf app ${greenAppName}", returnStatus: true) == 0
                            def greenAppHasRoute = sh (script: "cf app ${greenAppName} | grep routes: | grep ${routeName}.${routeDomain} | wc -l | tr -d [:space:]", returnStdout: true).equalsIgnoreCase("1")

                            if (greenAppExists && blueAppExists && greenAppHasRoute) {
                                sh "cf delete ${blueAppName} -f"
                                sh "cf rename ${greenAppName} ${blueAppName}"
                            } else if (greenAppExists && !blueAppExists) {
                                sh "cf rename ${greenAppName} ${blueAppName}"
                            }
                        }
                    }
                }
            }
        }
        stage("deploy to PCF-staging") {
            agent { label "CF-CLI && DTL" }
            steps {
                unstash "workspace"
                dir("ci/blue-green-stage-dev") {
                    script {
                        pcfUtil.deployToPCFGoRouter(this, greenAppName, 'nyc-stage', 'dcuser', spaceName, 'pcf_stage', null, null, keyMap, true, false, activeProfile)
                    }
                }
            }
        }
        stage("map route to green app") {
            agent { label "CF-CLI && DTL" }
            steps {
                dir("ci/blue-green-stage-dev") {
                    withEnv(["CF_HOME=."]) {
                        script {
                            withCredentials([[$class: "UsernamePasswordMultiBinding", credentialsId: "pcf_stage", usernameVariable: "PCF_USERNAME", passwordVariable: "PCF_PASSWORD"]]) {
                                sh "cf login -a ${paasUrl} -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${spaceName}"
                            }

                            sh "cf map-route ${greenAppName} ${routeDomain} -n ${routeName}"
                            sh "cf delete-route ${routeDomain} --hostname ${greenAppName} -f"

                            def blueAppExists = sh (script: "cf app ${blueAppName}", returnStatus: true) == 0

                            if(blueAppExists) {
                                sh "cf unmap-route ${blueAppName} ${routeDomain} -n ${routeName}"
                            }
                        }
                    }
                }
            }
        }
    }
}
