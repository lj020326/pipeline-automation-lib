
// author: Tulam, VenuGopal
def call(Map params=[:]) {

    def timeoutValue = 60

    #! groovy
    pipeline {
        agent { label 'M3' }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            timeout(time: 60, unit: 'MINUTES')
        }
        parameters {
            string(defaultValue: 'LTS', description: 'Provide Bitbucket Project Key', name: 'PROJECT')
            string(defaultValue: 'master', description: 'Enter checkout Branch', name: 'BRANCH')
            string(defaultValue: 'cdloyalty', description: 'What credentials ID should I use to checkout code from above project?', name: 'STASH_CREDENTIALS_ID')
            string(defaultValue: 'https://gitrepository.dettonville.int/stash/scm/~e074534/dependencymap.git', description: 'To which git repository should I commit dependency file?', name: 'GITDEPURL')
            string(defaultValue: 'cdloyalty', description: 'What credentials ID should I use to commit dependency file?', name: 'CREDENTIALS_ID')
        }
        tools {
            maven 'M3'
            jdk 'JDK 1.8'
        }
        environment {
            PROJECTDETAILS = 'projectdetails.json'
        }
        stages {
            stage('Project Dependency') {
                steps {
                    script {

                        withCredentials([usernameColonPassword(credentialsId: STASH_CREDENTIALS_ID, variable: 'USERPASS')]) {
                            mysh("curl -u $USERPASS -H 'Content-Type: application/json' https://gitrepository.dettonville.int/stash/rest/api/1.0/projects/${params.PROJECT}/repos?limit=1000 > ${env.PROJECTDETAILS}")
                            projectRepos = steps.readJSON file: 'projectdetails.json'
                        }

                        projectRepos.values.each { repoDtl ->
                            deleteDir()
                            repoName = repoDtl.slug

                            echoSteps("PROCESSING REPOSITORY : ${repoName}")

                            try {

                                git url: "https://gitrepository.dettonville.int/stash/scm/${project}/${repoName}.git", branch: "${params.BRANCH}"

                                if (fileExists('pom.xml')) {

                                    def artifactUrl = artifactoryUrl()
                                    def pomGid = pomGroupId()
                                    def artifactId = pomArtifactId()
                                    def artifactV = pomVersion()
                                    def packaging = pomPackaging()
                                    def runDate = steps.sh(returnStdout: true, script: "date +'%m-%d-%y-%T' | tr -d '\n'")

                                    echo "${runDate}"
                                    artifactPath = "${artifactUrl}${pomGid}/${artifactId}/${artifactV}"
                                    def artifactName = ""
                                    try {
                                        if (packaging == "war") {
                                            artifactName = readLastWarFromArtifactory(artifactPath)
                                        } else {
                                            artifactName = readLastJarFromArtifactory(artifactPath)
                                        }
                                    }
                                    catch (Exception e) {
                                        echo "Exception reading last artifact from Artifactory: ${e}"
                                    }

                                    echo "artifactName: ${artifactName}"
                                    echo "pomGid: ${pomGid}"
                                    echo "artifactId: ${artifactId}"
                                    echo "artifactV: ${artifactV}"

                                    MVN_FIRSTLEVEL_DEP = artifactId + 'MvnFirstLevelDep.csv'
                                    MVN_FULL_DEP = artifactId + 'MvnFullDep.csv'
                                    ART_DEP = artifactId + 'ArtDep.csv'
                                    DEPENDENCY_MAP = artifactId + runDate + 'DependencyMap.csv'

                                    touch "${MVN_FIRSTLEVEL_DEP}"
                                    touch "${MVN_FULL_DEP}"
                                    touch "${ART_DEP}"
                                    touch "${DEPENDENCY_MAP}"

                                    try {
                                        sh "mvn dependency:tree -DoutputFile=${MVN_FULL_DEP}"
                                        sh "grep '^[+-]' ${MVN_FULL_DEP} > ${MVN_FIRSTLEVEL_DEP}"
                                    }
                                    catch (Exception e) {
                                        echo "error resolving maven dependency tree ${e}"
                                    }

                                    sh "ls -ltr; pwd"
                                    def mvnFirstLevelContent = readFile(MVN_FIRSTLEVEL_DEP)
                                    def mvnFullContent = readFile(MVN_FULL_DEP)
                                    echoSteps("DOWNLOADING ARTIFACT")
                                    def artDepContent = "ARTIFACT NOT FOUND IN ARTIFACTORY"

                                    try {
                                        withCredentials([usernameColonPassword(credentialsId: STASH_CREDENTIALS_ID, variable: 'USERPASS')]) {
                                            sh "curl -o ${artifactId}.${packaging} ${artifactUrl}${pomGid}/${artifactId}/${artifactV}/${artifactName}"
                                        }
                                        sh "touch artDep.csv"
                                        sh "ls -ltr; pwd"
                                        sh "unzip ${artifactId}.${packaging} > artDep.csv"
                                        def artDep = readFile('artDep.csv')
                                        if (artDep.contains('BOOT-INF')) {
                                            sh """grep -o 'BOOT-INF/lib.*' artDep.csv | sed 's@BOOT-INF/lib/@@g' > ${
                                                ART_DEP
                                            }"""
                                        } else {
                                            sh """grep -o 'WEB-INF/lib.*' artDep.csv | sed 's@WEB-INF/lib/@@g' > ${
                                                ART_DEP
                                            }"""
                                        }
                                        artDepContent = readFile(ART_DEP)
                                    }
                                    catch (Exception e) {
                                        echo "error downloading artifact from artifactory ${e}"
                                    }

                                    writeFile file: DEPENDENCY_MAP, text: "\r\nFIRST LEVEL DEPENDENCY\n" + mvnFirstLevelContent + "\r\nDEPENDENCY FULL MAPPING\n" + mvnFullContent + "\r\nACTUAL DEPENDENCY LIBRARY EXTRACTED FROM ARTIFACT\n" + artDepContent
                                    echo "DEPENDENCY_MAP:"
                                    sh "cat ${DEPENDENCY_MAP}"

                                    def gitrepo = GITDEPURL
                                    def remoteOrigin = gitrepo.replace('https://', '')
                                    println remoteOrigin

                                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: CREDENTIALS_ID, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
                                        git url: "${params.GITDEPURL}", branch: "master"
                                        sh('git config --global user.email "jenkins@dettonville.org"')
                                        sh('git config --global user.name "Jenkins Pipeline"')
                                        sh("git remote set-url origin https://${GIT_USERNAME}:${GIT_PASSWORD}@${remoteOrigin}")
                                        sh("git pull origin master")
                                        sh("mkdir -p ${artifactId}-dependency")
                                        //sh("rm -fr ${artifactId}-dependency/")
                                        sh("cp *DependencyMap.csv ./${artifactId}-dependency/")
                                        sh("git add ./${artifactId}-dependency/*")
                                        sh("git commit -m 'updated dependency mapping for ${artifactId}'")
                                        sh("git push -u origin master")

                                        sh("ls -ltr ${artifactId}-dependency; pwd")
                                        sh "ls -ltr; pwd"
                                        sh "ls -ltr ../; pwd"
                                    }

                                    archiveArtifacts artifacts: '*DependencyMap.csv', onlyIfSuccessful: false
                                    deleteDir()
                                    sh "ls -ltr; pwd"
                                } else {
                                    echo "Pom does not exists in application parent directory of ${repoName}"
                                }
                            }
                            catch (Exception e) {
                                echo "Exception cloning repository ${repoName}: ${e}"
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                echoSteps("EMPTYING CURRENT WORKING DIR")
                deleteDir()
            }
        }
    }
}


def mysh(cmd) {
    sh('#!/bin/sh -e\n' + cmd)
}

def echoSteps(String newEcho) {
    def line2 = "***** INFO: ${newEcho} *****"
    def line13 = ""
    for (int i = 0; i < line2.length(); i++) {
        line13 = line13 + "*";
    }
    steps.echo "${line13}"
    steps.echo "${line2}"
    steps.echo "${line13}"
}

def artifactoryUrl() {
    url = "https://gitrepository.dettonville.int/artifactory/star/"
    return url
}

def pomGroupId() {
    pom = readMavenPom().getGroupId()
    artifactSpace = pom.replaceAll("\\.", "/")
    return artifactSpace
}

def pomArtifactId() {
    artifactD = readMavenPom().getArtifactId()
    return artifactD
}

def pomVersion() {
    ver = readMavenPom file: 'pom.xml'
    articatVersion = ver.version
    return articatVersion
}

def pomPackaging() {
    packType = readMavenPom file: 'pom.xml'
    articatType = packType.packaging
    return articatType
}

def readLastWarFromArtifactory(String artifactUploadPath) {
    def output = steps.sh returnStdout: true, script: "curl ${artifactUploadPath}/"
    //workaround to obtain the last element of the matching output
    //i do it this way because everything else (e.g. findall) is blocked within jenkins sandbox
    def i = 0
    def wars = ((output =~ /<a.*>(.+.war)<\/a>/))
    def lastArtifact = ""
    try {
        def count = 0
        //either terminate when we get the exception or when count is == 100
        //max number of artifacts we can have
        while (true || count != 100) {
            def war = wars[i][1]
            lastArtifact = war
            i++
            count++
        }
    } catch (Exception e) {
        steps.echo("Last element obtained")
        return lastArtifact
    }

    return ""
}

def readLastJarFromArtifactory(String artifactUploadPath) {
    def output = steps.sh returnStdout: true, script: "curl ${artifactUploadPath}/"
    //workaround to obtain the last element of the matching output
    //i do it this way because everything else (e.g. findall) is blocked within jenkins sandbox
    def i = 0
    def jars = ((output =~ /<a.*>(.+.jar)<\/a>/))
    def lastArtifact = ""
    try {
        def count = 0
        //either terminate when we get the exception or when count is == 100
        //max number of artifacts we can have
        while (true || count != 100) {
            def jar = jars[i][1]
            lastArtifact = jar
            i++
            count++
        }
    } catch (Exception e) {
        steps.echo("Last element obtained")
        return lastArtifact
    }

    return ""
}
