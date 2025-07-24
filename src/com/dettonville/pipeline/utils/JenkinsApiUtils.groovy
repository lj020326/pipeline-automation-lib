package com.dettonville.pipeline.utils


import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.JsonUtils

class JenkinsApiUtils implements Serializable {
    private static final long serialVersionUID = 1L

    com.dettonville.pipeline.utils.logging.Logger log = new com.dettonville.pipeline.utils.logging.Logger(this)
    def dsl

    String jenkinsApiCredId
//    String currentBuildUrl
//    String jenkinsMasterUrl

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    JenkinsApiUtils(dsl, String jenkinsApiCredId="jenkins-rest-api-user") {
        this.dsl = dsl
        this.jenkinsApiCredId = jenkinsApiCredId
    }

    void setJenkinsApiCredId(String jenkinsApiCredId="jenkins-rest-api-user") {
        this.jenkinsApiCredId = jenkinsApiCredId
    }

    // ref: https://stackoverflow.com/questions/20098739/match-base-url-regex
    // ref; https://groovyconsole.appspot.com/script/1109001
    // ref: https://coderwall.com/p/utgplg/regex-full-url-base-url
    String getBaseUrl(String urlStr) {

        return urlStr.find(/(^.+?[^\/:](?=[?\/]|$))\.*/) { it[1] };
    }

    // ref: https://stackoverflow.com/questions/36194316/how-to-get-the-build-user-in-jenkins-when-job-triggered-by-timer
    Map getCurrentJobCauseInfo() {

        Map jobCauseMap = [:]

        log.debug("starting")

        String buildResultsUrl = "${dsl.env.BUILD_URL}"

        log.debug("buildResultsUrl=${buildResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                              usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("Determine if job info exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()

            log.debug("responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("job results not found at ${buildResultsUrl}, returned responseStatus=${responseStatus}")
                return jobCauseMap
            }

            log.debug("job info exists, retrieving")

            buildResultsUrl += "api/json?pretty=true"
            String jobInfoJson = dsl.sh(script: "curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).trim().replaceAll('null','""')

            log.debug("jobInfoJson=${jobInfoJson}")

            Map jobInfoMap = dsl.readJSON text: jobInfoJson

            log.debug("jobInfoMap=${JsonUtils.printToJsonString(jobInfoMap)}")

            jobCauseMap = jobInfoMap.actions.findResult { it.causes }[0]
            log.debug("jobCauseMap=${JsonUtils.printToJsonString(jobCauseMap)}")

        }
        return jobCauseMap
    }

    // ref: https://santoshbandage.wordpress.com/2017/09/04/jenkins-api-to-get-build-details/
    // ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/master/lastCompletedBuild/api/json?pretty=true
    // ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/master/721/api/json?pretty=true
//    Integer getBuildNumber(String jobBaseUri, String buildStatus="lastSuccessfulBuild") {
    Integer getBuildNumber(String jobBaseUri, String buildStatus="lastCompletedBuild") {

        log.debug("starting")

        Integer buildNumber

        String jenkinsMasterUrl = getBaseUrl(dsl.currentBuild.absoluteUrl)

        String buildResultsUrl = "${jenkinsMasterUrl}/${jobBaseUri}/${buildStatus}/buildNumber"
        log.debug("buildResultsUrl=${buildResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                              usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("Determine if job info exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()

            log.debug("responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("job results not found at ${buildResultsUrl}, returned responseStatus=${responseStatus}")
                return buildNumber
            }

            log.debug("job info exists, retrieving")

            buildNumber = dsl.sh(script: "curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()
            log.debug("buildNumber=${buildNumber}")

        }
        return buildNumber
    }

//    Map getBuildResults(String jobBaseUri, Integer buildNumber, boolean prettyPrint=true) {
    Map getBuildResults(Integer buildNumber, Map buildStatusConfig) {
        return getBuildResults(buildNumber.toString(), buildStatusConfig)
    }

    // ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/master/lastCompletedBuild/api/json?pretty=true
    // ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/master/721/api/json?pretty=true
//    Map getBuildResults(String jobBaseUri, String buildNumber="lastCompletedBuild", boolean prettyPrint=true, boolean getResultsFile = false, String buildResultsFileName="buildResults") {
//    Map getBuildResults(String buildNumber="lastStableBuild", Map buildStatusConfig) {
//    Map getBuildResults(String buildNumber="lastSuccessfulBuild", Map buildStatusConfig) {
    Map getBuildResults(String buildNumber="lastCompletedBuild", Map buildStatusConfig) {

        log.debug("starting")

        Map buildResults = [:]

//        String buildResultsFile = "buildResults.${buildNumber}.json"
        String buildResultsFile = "${buildStatusConfig.buildResultsFileName}.${buildNumber}.json"
//        String jenkinsMaster=dsl.env.BUILD_URL.split('/')[2].split(':')[0]
        String jenkinsMasterUrl = getBaseUrl(dsl.currentBuild.absoluteUrl)

        String buildResultsUrl = "${jenkinsMasterUrl}/${buildStatusConfig.jobBaseUri}/${buildNumber}/api/json"
        if (buildStatusConfig.prettyPrint) {
            buildResultsUrl += "?pretty=true"
        }
        log.debug("buildResultsUrl=${buildResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                          usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("Determine if job info exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()

            log.debug("responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("job test results not found at ${buildResultsUrl}, returned responseStatus=${responseStatus}")
                return buildResults
            }

            log.debug("job info exists, retrieving")

//            if (buildStatusConfig.getResultsFile) {
//                dsl.sh("curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl} 2>&1 | sed 's/null/\"\"/g' | tee ${buildResultsFile}")
//                dsl.archiveArtifacts(artifacts: '*.json', onlyIfSuccessful: false)
//                buildResults = dsl.readJSON file: buildResultsFile
//            } else {
//                String buildResultsStr = dsl.sh(script: "curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).trim().replaceAll('null','""')
//                buildResults = dsl.readJSON text: buildResultsStr
//            }
            String buildResultsStr = dsl.sh(script: "curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).trim().replaceAll('null','""')
            buildResults = dsl.readJSON text: buildResultsStr

            log.debug("buildResults=${JsonUtils.printToJsonString(buildResults)}")

        }
        return buildResults
    }

    Map getTestResults(Integer buildNumber) {

        log.info("starting")

        Map testResults = [:]

        String testResultFile = "testResults.${buildNumber}.json"
        String buildUrl = dsl.currentBuild.absoluteUrl.substring(0, dsl.currentBuild.absoluteUrl.lastIndexOf("/"))
        String testResultsUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
        testResultsUrl += "/${buildNumber}/testReport/api/json"
        log.info("testResultsUrl=${testResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([dsl.usernamePassword(credentialsId: this.jenkinsApiCredId,
                            passwordVariable: 'JENKINS_TOKEN', usernameVariable: 'JENKINS_USERNAME')]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.info("Determine if test results exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${testResultsUrl}", returnStdout: true).toInteger()

            log.info("responseStatus: ${responseStatus}")
            if (responseStatus==200) {
                log.info("job test results exists, retrieving")

                dsl.sh "curl -sSL -u ${JENKINS_CREDS} ${testResultsUrl} 2>&1 | sed 's/null/\"\"/g' | tee ${testResultFile}"
                //    dsl.sh "curl -sSL -u ${JENKINS_CREDS} ${testResultsUrl} -o ${testResultFile}"

//            dsl.sh "find . -maxdepth 1 -name ${testResultFile} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"
                testResults = dsl.readJSON file: "${testResultFile}"
                //    log.info("testResults=${testResults}")
                log.debug("testResults=${JsonUtils.printToJsonString(testResults)}")

            } else {
                log.warn("job test results not found at ${testResultsUrl}, returned responseStatus=${responseStatus}")
            }

        }
        testResults.buildNumber=buildNumber
        return testResults
    }

    Integer getJobArtifact(String artifactPath, String buildNumber="lastCompletedBuild") {

        log.debug("starting")

        Map buildResults = [:]

        String buildUrl = dsl.currentBuild.absoluteUrl.substring(0, dsl.currentBuild.absoluteUrl.lastIndexOf("/"))
        String artifactUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
        artifactUrl += "/${buildNumber}/artifact/${artifactPath}"

        log.debug("artifactUrl=${artifactUrl}")

        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                          usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("Determine if test results exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${artifactUrl}", returnStdout: true).toInteger()

            log.debug("responseStatus: ${responseStatus}")
            if (responseStatus!=200) {
                log.debug("artifact not found at ${artifactUrl}, returned responseStatus=${responseStatus}")
                return buildResults
            }
            log.debug("artifact exists, retrieving")

            dsl.sh("curl -sSL -u ${JENKINS_CREDS} ${artifactUrl} -o ${artifactPath}")

            dsl.sh("find . -maxdepth 1 -name ${artifactPath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3")

        }

        return responseStatus
    }

    Integer getJobArtifactFromBuildUrl(String buildUrl, String artifactPath, String destinationPath=null) {
        log.debug("starting")

        destinationPath = destinationPath ?: artifactPath

        log.debug("artifactPath=${artifactPath}")
        log.debug("destinationPath=${destinationPath}")

        Map buildResults = [:]

        String artifactUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
        artifactUrl += "/artifact/${artifactPath}"

        log.debug("artifactUrl=${artifactUrl}")

        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                              usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("Determine if test results exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${artifactUrl}", returnStdout: true).toInteger()

            log.debug("responseStatus: ${responseStatus}")
            if (responseStatus!=200) {
                log.debug("artifact not found at ${artifactUrl}, returned responseStatus=${responseStatus}")
                return buildResults
            }
            log.debug("artifact exists, retrieving")

            dsl.sh("curl -sSL -u ${JENKINS_CREDS} ${artifactUrl} -o ${destinationPath}")

            dsl.sh("find . -maxdepth 1 -name ${artifactPath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3")

        }

        return responseStatus
    }



}
