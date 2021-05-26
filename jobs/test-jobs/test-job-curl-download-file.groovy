#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

Logger.init(this, LogLevel.INFO)
Logger log = new Logger(this)

node ('QA-LINUX || PROD-LINUX') {
    cleanWs()

    log.info("Download something")
    String downloadUrl = "https://fusion.dettonville.int/stash/projects/API/repos/certutils/raw/scripts/certs.tgz?at=refs%2Fheads%2Fmaster"
    String downloadFilePath = "certs.tar.gz"

    String runDir = "foobar"

    dir(runDir) {
        withCredentials([[$class          : 'UsernamePasswordMultiBinding', credentialsId: 'dcapi_ci_vcs_user',
                          usernameVariable: 'BITBUCKET_USERNAME', passwordVariable: 'BITBUCKET_PASSWORD']]) {

            String BITBUCKET_CREDS = "${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD}"

            log.info("Determine if test results exists")
            Integer responseStatus = sh(script: "curl -s -o /dev/null -w %{http_code} -u ${BITBUCKET_CREDS} ${downloadUrl}", returnStdout: true).toInteger()

            log.info("responseStatus: ${responseStatus}")
            if (responseStatus == 200) {
                log.info("prior test results exists, retrieving")

                sh "curl -sSL -u ${BITBUCKET_CREDS} ${downloadUrl} -o ${downloadFilePath}"

                String curr_dir = pwd()
                log.info("curr_dir=${curr_dir}")

                sh "find . -maxdepth 1 -name ${downloadFilePath} -type f -printf \"%TY-%Tm-%Td %TH:%TM:%.2Ts %m:%u:%g %p %k kB\\n\" | sort -k 3,3"

                archiveArtifacts artifacts: downloadFilePath
                log.info("${downloadFilePath} archived")

            } else {
                log.info("file not found at ${downloadUrl}, returned responseStatus=${responseStatus}")
            }

        }
        sh('printenv | sort')
    }

    cleanWs()
}

