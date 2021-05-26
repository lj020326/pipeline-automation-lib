package com.dettonville.api.pipeline.utility

/**
 * Email Utility functions used to extend functionality of pipeline
 *
 * @Author neel.shah@dettonville.org
 */
class EmailUtil implements Serializable {

    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EmailUtil(steps) {this.steps = steps}

    /**
     * Function to prepare for Email Notification by getting information from Bitbucket using REST API and preparing Email Body.
     * If the current build is run against a regular branch commit, it will get email of the last committer using Bitbucket rest api.
     * If the current build is run against a Pull Request, it will get email of the person who created the pull request, pull request
     * reviewers assigned while creating it. It also get information related to the source branch from which this Pull Request was created.
     *
     * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
     * @param stashCredentials name of the credentialsId you want to use to access the repository
     * @return RECIPIENTS environment variable available for use in further pipeline which contains the email address for last committer or the one who created Pull Request
     * @return PR_REVIEWERS environment variable available for use in further pipeline which contains the email address of all Pull Request Reviewers if this is a Pull Request build or empty space value
     * @return EMAIL_BODY environment variable available for use in further pipeline which contains the body of the email notification with Bitbucket URL, Jenkins Build URL, Commit Hash, Branch Name, Pull Request Target, Pull Request Title, Pull Request Author and Pull Request URL
     */
    public void prepareEmailNotification(script, String stashCredentials = null) {
        def lastCommitHash = steps.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
        def bitbucketUrl = steps.sh(returnStdout: true, script: 'grep -m 1 "^[[:space:]]url.*" .git/config | grep -o "http.*"').trim()
        def prDetails = " "
        def prReviewers = " "
        def recipients
        def sourceBranchName = "${script.env.BRANCH_NAME}"
        def bitbucketApiDataFile = 'bitbucket_api_data.json'
        def getRepoEndpoint = steps.sh(returnStdout: true, script: "grep -m 1 '^[[:space:]]url.*' .git/config | grep -o 'http.*' | sed 's@stash/scm/@stash/rest/api/1.0/projects/@' | sed 's@projects/~@users/@' | sed 's@\\([a-zA-Z0-9._-]*.git\\)@repos/\\1@' | sed 's@.git@@'").trim()

        if ( ! script.env.CHANGE_TITLE ) {
            // Get Bitbucket REST API Endpoint when this is not a Pull Request
            def commitEndpoint = "${getRepoEndpoint}/commits/${lastCommitHash}/?until=${sourceBranchName}"

            if (stashCredentials) {
                steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${stashCredentials}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    try {
                        steps.sh "curl -u ${script.USERNAME}:${script.PASSWORD} -H 'Content-Type: application/json' ${commitEndpoint} > ${bitbucketApiDataFile}"
                    } catch (Exception ex) {
                        steps.echo "ERROR! Trying to access the bitbucket repository using the credentials id provided: ${stashCredentials}"
                    }
                }
            }
            else {
                steps.sh "curl -H 'Content-Type: application/json' ${commitEndpoint} > ${bitbucketApiDataFile}"
            }
            // Parse all JSON Data from BitBucket API Request
            try {
                recipients = steps.sh(returnStdout: true, script: "cat ${bitbucketApiDataFile} | python -c \"import sys, json; print json.load(sys.stdin)['committer']['emailAddress']\"").trim()
            } catch (Exception ex) {
                steps.echo "ERROR! Trying to parse JSON Data from bitbucket api response."
            }
        }
        else {
            // Get Bitbucket REST API Endpoint when this is a Pull Request
            def getBranchDetails = steps.sh(returnStdout: true, script: 'grep -m 1 "^[[:space:]]fetch.*" .git/config | grep -o "+refs.*:" | sed "s/+refs//" | sed "s@/from@@" |  sed "s@:@@"').trim()
            def pullRequestEndpoint = "${getRepoEndpoint}${getBranchDetails}"

            if (stashCredentials) {
                steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${stashCredentials}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    try {
                        steps.sh "curl -u ${script.USERNAME}:${script.PASSWORD} -H 'Content-Type: application/json' ${pullRequestEndpoint} > ${bitbucketApiDataFile}"
                    } catch (Exception ex) {
                        steps.echo "ERROR! Trying to access the bitbucket repository using the credentials id provided: ${stashCredentials}"
                    }
                }
            }
            else {
                steps.sh "curl -H 'Content-Type: application/json' ${pullRequestEndpoint} > ${bitbucketApiDataFile}"
            }
            // Parse all JSON Data from BitBucket API Request
            try {
                prReviewers = steps.sh(returnStdout: true, script: "cat ${bitbucketApiDataFile} | python -c \$'import sys, json\nfor item in json.load(sys.stdin)[\"reviewers\"]:print item[\"user\"][\"emailAddress\"]'").trim()
                sourceBranchName = steps.sh(returnStdout: true, script: "cat ${bitbucketApiDataFile} | python -c \"import sys, json; print json.load(sys.stdin)['fromRef']['displayId']\"").trim()
                recipients = steps.sh(returnStdout: true, script: "cat ${bitbucketApiDataFile} | python -c \"import sys, json; print json.load(sys.stdin)['author']['user']['emailAddress']\"").trim()
            } catch (Exception ex) {
                steps.echo "ERROR! Trying to parse JSON Data from bitbucket api response."
            }
            prDetails = "\nPull Request Target : ${script.env.CHANGE_TARGET} \nPull Request Title : ${script.env.CHANGE_TITLE} \nPull Request Author : ${script.env.CHANGE_AUTHOR} \nPull Request URL : ${script.env.CHANGE_URL} \n"
        }
        script.env.RECIPIENTS = recipients
        script.env.PR_REVIEWERS = prReviewers
        script.env.EMAIL_BODY = "Bitbucket URL: ${bitbucketUrl} \nJenkins Build URL: ${script.env.BUILD_URL} \nCommit Hash: ${lastCommitHash} \nBranch Name: ${sourceBranchName} ${prDetails}"
    }

    /**
     *
     * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
     * @param jenkinsCredentials credentials for accessing Jenkins
     * @param noOfLines optional parameter as an Integer value to specify the number of lines for extracting the log file.
     * @return FAILURE_EMAIL_BODY It create a new environment variable called FAILURE_EMAIL_BODY which has custom build log appended towards the end of it for failure notifications
     */
    public void getBuildLog(script, String jenkinsCredentials, Integer noOfLines = null) {
        def buildLogUrl = "${script.env.BUILD_URL}consoleText"
        def crumbUrl = "${script.env.JENKINS_URL}crumbIssuer/api/json"
        def insecureConnection = buildLogUrl.contains('stage.cd.dettonville.int') ? "--insecure" : ""
        def logOutput = "custom_build_log.txt"
        def filteredOutput = "custom_build_log_filtered.txt"
        steps.echo "Starting to extract the log file for current build..."
        if ( ! noOfLines ) {
            noOfLines = 200
        }
        steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${jenkinsCredentials}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            def crumb = steps.sh(returnStdout: true, script: "curl -u ${script.USERNAME}:${script.PASSWORD} -H 'Content-Type: text/plain' ${insecureConnection} ${crumbUrl} | python -c 'import sys,json;j=json.load(sys.stdin);print j[\"crumbRequestField\"] + \":\" + j[\"crumb\"]'").trim()
            steps.sh (returnStdout: true, script:"curl -u ${script.USERNAME}:${script.PASSWORD} -H \"${crumb}\" ${insecureConnection} ${buildLogUrl} > ${logOutput}")
        }
        // Todo: Need to parse this ${logOutput} file to filter unnecessary data and only output meaningful info for the email notification
        steps.sh "sed '/(Declarative: Post Actions)/q' ${logOutput} > ${filteredOutput}"
        def customLog = steps.sh(returnStdout: true, script: "tail -n ${noOfLines} ${filteredOutput}").trim()
        script.env.FAILURE_EMAIL_BODY = "${script.env.EMAIL_BODY} \n\nBuild Log:\n${customLog}"
    }

    /**
     *
     * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
     * @return EMAIL_BODY environment by appending the status for each of these variables UNIT_TESTS, BUILD_ARTIFACT, SONAR_CHECK towards the end of EMAIL_BODY variable
     */
    public void getCustomStageStatus(script) {
        // Todo: Make this code more dynamic by parsing the filteredOutput file from getBuildLog() and setting the status for each of the below variables
        if ( ( ! script.env.UNIT_TESTS ) && ( ! script.env.BUILD_ARTIFACT ) ) {
            script.env.UNIT_TESTS = "FAILED"
            script.env.BUILD_ARTIFACT = "SKIPPED"
            script.env.SONAR_CHECK = "SKIPPED"
        }
        else if ( ( script.env.UNIT_TESTS ) && ( ! script.env.BUILD_ARTIFACT ) ) {
            script.env.BUILD_ARTIFACT = "FAILED"
            script.env.SONAR_CHECK = "SKIPPED"
        }
        else if ( ( script.env.BUILD_ARTIFACT ) && ( ! script.env.UNIT_TESTS ) ) {
            script.env.UNIT_TESTS = "FAILED"
            script.env.SONAR_CHECK = "SKIPPED"
        }
        else if ( ( script.env.BUILD_ARTIFACT ) && ( ! script.env.SONAR_CHECK ) ) {
            script.env.SONAR_CHECK = "FAILED"
        }
        else {
            script.env.UNIT_TESTS = "SKIPPED"
            script.env.BUILD_ARTIFACT = "SKIPPED"
            script.env.SONAR_CHECK = "SKIPPED"
        }

        script.env.EMAIL_BODY = "${script.env.EMAIL_BODY} \n\nUnit Tests: ${script.env.UNIT_TESTS} \nBuild Artifact: ${script.env.BUILD_ARTIFACT}\nSonar Check: ${script.env.SONAR_CHECK}"
    }
}
