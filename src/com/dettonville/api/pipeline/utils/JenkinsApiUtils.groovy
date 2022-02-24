package com.dettonville.api.pipeline.utils


import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.JsonUtils

class JenkinsApiUtils implements Serializable {
    private static final long serialVersionUID = 1L

    com.dettonville.api.pipeline.utils.logging.Logger log = new com.dettonville.api.pipeline.utils.logging.Logger(this)
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
        String logPrefix = "getBaseUrl():"

        return urlStr.find(/(^.+?[^\/:](?=[?\/]|$))\.*/) { it[1] };
    }

    // ref: https://stackoverflow.com/questions/36194316/how-to-get-the-build-user-in-jenkins-when-job-triggered-by-timer
    Map getCurrentJobCauseInfo() {
        String logPrefix = "getCurrentJobCauseInfo():"

        Map jobCauseMap = [:]

        log.debug("${logPrefix} starting")

        String buildResultsUrl = "${dsl.env.BUILD_URL}"

        log.debug("${logPrefix} buildResultsUrl=${buildResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                              usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("${logPrefix} Determine if job info exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()

            log.debug("${logPrefix} responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("${logPrefix} job results not found at ${buildResultsUrl}, returned responseStatus=${responseStatus}")
                return jobCauseMap
            }

            log.debug("${logPrefix} job info exists, retrieving")

            buildResultsUrl += "api/json?pretty=true"
            String jobInfoJson = dsl.sh(script: "curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).trim().replaceAll('null','""')

            log.debug("${logPrefix} jobInfoJson=${jobInfoJson}")

            Map jobInfoMap = dsl.readJSON text: jobInfoJson

            log.debug("${logPrefix} jobInfoMap=${JsonUtils.printToJsonString(jobInfoMap)}")

            jobCauseMap = jobInfoMap.actions.findResult { it.causes }[0]
            log.debug("${logPrefix} jobCauseMap=${JsonUtils.printToJsonString(jobCauseMap)}")

        }
        return jobCauseMap
    }

    // ref: https://santoshbandage.wordpress.com/2017/09/04/jenkins-api-to-get-build-details/
    // ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/master/lastCompletedBuild/api/json?pretty=true
    // ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/master/721/api/json?pretty=true
//    Integer getBuildNumber(String jobBaseUri, String buildStatus="lastSuccessfulBuild") {
    Integer getBuildNumber(String jobBaseUri, String buildStatus="lastCompletedBuild") {
        String logPrefix = "getBuildNumber():"

        log.debug("${logPrefix} starting")

        Integer buildNumber

        String jenkinsMasterUrl = getBaseUrl(dsl.currentBuild.absoluteUrl)

        String buildResultsUrl = "${jenkinsMasterUrl}/${jobBaseUri}/${buildStatus}/buildNumber"
        log.debug("${logPrefix} buildResultsUrl=${buildResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                              usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("${logPrefix} Determine if job info exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()

            log.debug("${logPrefix} responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("${logPrefix} job results not found at ${buildResultsUrl}, returned responseStatus=${responseStatus}")
                return buildNumber
            }

            log.debug("${logPrefix} job info exists, retrieving")

            buildNumber = dsl.sh(script: "curl -sSL -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()
            log.debug("${logPrefix} buildNumber=${buildNumber}")

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
        String logPrefix = "getBuildResults():"

        log.debug("${logPrefix} starting")

        Map buildResults = [:]

//        String buildResultsFile = "buildResults.${buildNumber}.json"
        String buildResultsFile = "${buildStatusConfig.buildResultsFileName}.${buildNumber}.json"
//        String jenkinsMaster=dsl.env.BUILD_URL.split('/')[2].split(':')[0]
        String jenkinsMasterUrl = getBaseUrl(dsl.currentBuild.absoluteUrl)

        String buildResultsUrl = "${jenkinsMasterUrl}/${buildStatusConfig.jobBaseUri}/${buildNumber}/api/json"
        if (buildStatusConfig.prettyPrint) {
            buildResultsUrl += "?pretty=true"
        }
        log.debug("${logPrefix} buildResultsUrl=${buildResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                          usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("${logPrefix} Determine if job info exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${buildResultsUrl}", returnStdout: true).toInteger()

            log.debug("${logPrefix} responseStatus: ${responseStatus}")

            if (responseStatus!=200) {
                log.warn("${logPrefix} job test results not found at ${buildResultsUrl}, returned responseStatus=${responseStatus}")
                return buildResults
            }

            log.debug("${logPrefix} job info exists, retrieving")

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

            log.debug("${logPrefix} buildResults=${JsonUtils.printToJsonString(buildResults)}")

        }
        return buildResults
    }

    Map getTestResults(Integer buildNumber) {
        String logPrefix = "getTestResults():"

        log.info("${logPrefix} starting")

        Map testResults = [:]

        String testResultFile = "testResults.${buildNumber}.json"
        String buildUrl = dsl.currentBuild.absoluteUrl.substring(0, dsl.currentBuild.absoluteUrl.lastIndexOf("/"))
        String testResultsUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
        testResultsUrl += "/${buildNumber}/testReport/api/json"
        log.info("${logPrefix} testResultsUrl=${testResultsUrl}")
        Integer responseStatus

        dsl.withCredentials([dsl.usernamePassword(credentialsId: this.jenkinsApiCredId,
                            passwordVariable: 'JENKINS_TOKEN', usernameVariable: 'JENKINS_USERNAME')]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.info("${logPrefix} Determine if test results exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${testResultsUrl}", returnStdout: true).toInteger()

            log.info("${logPrefix} responseStatus: ${responseStatus}")
            if (responseStatus==200) {
                log.info("${logPrefix} job test results exists, retrieving")

                dsl.sh "curl -sSL -u ${JENKINS_CREDS} ${testResultsUrl} 2>&1 | sed 's/null/\"\"/g' | tee ${testResultFile}"
                //    dsl.sh "curl -sSL -u ${JENKINS_CREDS} ${testResultsUrl} -o ${testResultFile}"

//            dsl.sh "find . -maxdepth 1 -name ${testResultFile} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"
                testResults = dsl.readJSON file: "${testResultFile}"
                //    log.info("${logPrefix} testResults=${testResults}")
                log.debug("${logPrefix} testResults=${JsonUtils.printToJsonString(testResults)}")

            } else {
                log.warn("${logPrefix} job test results not found at ${testResultsUrl}, returned responseStatus=${responseStatus}")
            }

        }
        testResults.buildNumber=buildNumber
        return testResults
    }

    Integer getJobArtifact(String artifactPath, String buildNumber="lastCompletedBuild") {
        String logPrefix = "getJobArtifact():"

        log.debug("${logPrefix} starting")

        Map buildResults = [:]

        String buildUrl = dsl.currentBuild.absoluteUrl.substring(0, dsl.currentBuild.absoluteUrl.lastIndexOf("/"))
        String artifactUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
        artifactUrl += "/${buildNumber}/artifact/${artifactPath}"

        log.debug("${logPrefix} artifactUrl=${artifactUrl}")

        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                          usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("${logPrefix} Determine if test results exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${artifactUrl}", returnStdout: true).toInteger()

            log.debug("${logPrefix} responseStatus: ${responseStatus}")
            if (responseStatus!=200) {
                log.debug("${logPrefix} artifact not found at ${artifactUrl}, returned responseStatus=${responseStatus}")
                return buildResults
            }
            log.debug("${logPrefix} artifact exists, retrieving")

            dsl.sh("curl -sSL -u ${JENKINS_CREDS} ${artifactUrl} -o ${artifactPath}")

            dsl.sh("find . -maxdepth 1 -name ${artifactPath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3")

        }

        return responseStatus
    }

    Integer getJobArtifactFromBuildUrl(String buildUrl, String artifactPath, String destinationPath=null) {
        String logPrefix = "getJobArtifactFromBuildUrl():"
        log.debug("${logPrefix} starting")

        destinationPath = destinationPath ?: artifactPath

        log.debug("${logPrefix} artifactPath=${artifactPath}")
        log.debug("${logPrefix} destinationPath=${destinationPath}")

        Map buildResults = [:]

        String artifactUrl = buildUrl.substring(0, buildUrl.lastIndexOf("/"))
        artifactUrl += "/artifact/${artifactPath}"

        log.debug("${logPrefix} artifactUrl=${artifactUrl}")

        Integer responseStatus

        dsl.withCredentials([[$class : 'UsernamePasswordMultiBinding', credentialsId: this.jenkinsApiCredId,
                              usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_TOKEN']]) {

            String JENKINS_CREDS = "${dsl.env.JENKINS_USERNAME}:${dsl.env.JENKINS_TOKEN}"

            log.debug("${logPrefix} Determine if test results exists")
            responseStatus=dsl.sh(script: "curl -s -o /dev/null -w %{http_code} -u ${JENKINS_CREDS} ${artifactUrl}", returnStdout: true).toInteger()

            log.debug("${logPrefix} responseStatus: ${responseStatus}")
            if (responseStatus!=200) {
                log.debug("${logPrefix} artifact not found at ${artifactUrl}, returned responseStatus=${responseStatus}")
                return buildResults
            }
            log.debug("${logPrefix} artifact exists, retrieving")

            dsl.sh("curl -sSL -u ${JENKINS_CREDS} ${artifactUrl} -o ${destinationPath}")

            dsl.sh("find . -maxdepth 1 -name ${artifactPath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3")

        }

        return responseStatus
    }



}
