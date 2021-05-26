#!groovy

// ref: https://fusion.dettonville.int/stash/projects/MCREFARCH/repos/polaris-documentation/browse/Jenkinsfile

def pcfUtil = new com.dettonville.api.pipeline.utility.PCFUtil(this)
def mvnUtil = new com.dettonville.api.pipeline.utility.MavenUtil(this)
def emailList = 'saron.yifru@dettonville.org faisal.chaudhry@dettonville.org kiran.hatti@dettonville.org viral.mehta@dettonville.org lakshmi.adepu@dettonville.org'

def appHostName = "polaris-documentation"
def gitRepo = "https://gitrepository.dettonville.int/stash/scm/mcrefarch/polaris-documentation.git"
def gitBranch = 'docs'
def pcfSpace = "polaris"
def pcfEnv = "nyc-dev"
def pcfLoginCred = "shcomp_app_frameworks_id"
def pcfOrg = "Shared-Components-App-Frameworks"

def keyMap = [:]

pipeline {
    agent none
    stages {
        stage('pull code') {
            agent { label "M3" }
            steps {
                script {
                    deleteDir()
                    mvnUtil.gitPull(this,gitRepo,gitBranch)
                    stash includes: '**', name: 'workspace'
                }
            }
        }
        stage('Build') {
            agent { label "M3" }
            steps {
                script {
                    unstash 'workspace'
                    sh "echo Setup Hugo && tar -xvf hugo/hugo_0.36_Linux-64bit.tar.gz -C hugo/ && ls -lah hugo"
                    sh "echo Hugo Build && ./hugo/hugo"
                    stash includes: '**', name: 'workspace'
                }
            }
        }
        stage('Deploy to DevPaaS') {
            agent { label "CF-CLI && DTL" }
            steps {
                script {
                    unstash 'workspace'
                    def vaultCredentialId = null
                    def vaultBackendId = null
                    def synapseEnabled = false
                    def vaultEnabled = false
                    def activeSpringProfiles = ''
                    def dirToPush = '.'
                    def manifestLoc = "./manifest.yml"
                    pcfUtil.deployToPCFGoRouter(this, appHostName, pcfEnv, pcfOrg, pcfSpace, pcfLoginCred, vaultCredentialId, vaultBackendId, keyMap, synapseEnabled, vaultEnabled, activeSpringProfiles, dirToPush, manifestLoc)
                }

            }
        }


    }
}
