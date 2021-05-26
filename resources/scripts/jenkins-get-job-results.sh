#!/usr/bin/env bash

## ref: https://stackoverflow.com/questions/296536/how-to-urlencode-data-for-curl-command
rawurlencode() {
  local string="${1}"
  local strlen=${#string}
  local encoded=""
  local pos c o

  for (( pos=0 ; pos<strlen ; pos++ )); do
     c=${string:$pos:1}
     case "$c" in
        [-_.~a-zA-Z0-9] ) o="${c}" ;;
        * )               printf -v o '%%%02x' "'$c"
     esac
     encoded+="${o}"
  done
  echo "${encoded}"    # You can either set a return variable (FASTER)
  REPLY="${encoded}"   #+or echo the result (EASIER)... or both... :p
}

#DATE=`date +%Y%m%d`
DATE=`date +%Y%m%d.%H%M%S`

if [ -z "$JENKINS_TOKEN" ]; then
    echo "For this script to work:"
    echo "env var JENKINS_TOKEN must be defined"
    echo "quitting!"
    exit 1
fi

JENKINS_CREDS="${USER}:${JENKINS_TOKEN}"
JENKINS_URL="cd.dettonville.int"

QA_APP_ENV="STAGE"
QA_TEST_SUITE="HEALTHCHECK-HOURLY"
QA_TEST_CASE="Chrome"
QA_JOB_FOLDER="jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/${QA_APP_ENV}/job/${QA_TEST_SUITE}/job/${QA_TEST_CASE}"
QA_JOB_NUMBER="lastBuild"
#QA_JOB_NUMBER="52"

FILENAME="jobresults.${QA_JOB_NUMBER}.${DATE}.json"
RPT_DIR="save/jobresults"


# ref: https://gist.github.com/justlaputa/5634984
#/job/${job_name}/api/json?tree=builds[number,status,timestamp,id,result]
# https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE/job/HEALTHCHECK-HOURLY/job/Chrome/api/json?pretty=true

#JENKINS_JOB_URL="https://${JENKINS_URL}/${QA_JOB_FOLDER}/api/json?pretty=true"

#args="pretty=true&tree=builds[number,status,timestamp,id,result]"
#args="tree=number,building,result,timestamp"
#args="tree=builds[number,status,timestamp,id,result]"
#JENKINS_JOB_URL="https://${JENKINS_URL}/${QA_JOB_FOLDER}/lastBuild/api/json?${args}"
#JENKINS_JOB_URL="https://${JENKINS_URL}/${QA_JOB_FOLDER}/api/json?$( rawurlencode "${args}" )"

#args="tree=number,status,building,result,timestamp"
args="tree=number,status,result,timestamp"
#JENKINS_JOB_URL="https://${JENKINS_URL}/${QA_JOB_FOLDER}/lastBuild/api/json?$( rawurlencode "${args}" )"
#JENKINS_JOB_URL="https://${JENKINS_URL}/${QA_JOB_FOLDER}/lastBuild/artifact/target/site/serenity/summary.txt"

## ref: https://stackoverflow.com/questions/42311654/jenkins-is-there-any-api-to-see-test-reports-remotely
JENKINS_JOB_URL="https://${JENKINS_URL}/${QA_JOB_FOLDER}/${QA_JOB_NUMBER}/testReport/api/json"

## target/site/serenity/summary.txt
## https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE/job/HEALTHCHECK-HOURLY/job/Chrome/40/artifact/target/site/serenity/summary.txt

## good:
## curl -s -u ljohnson:a0fee46aacab91f44cffa109c1ceb76e 'https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE/job/HEALTHCHECK-HOURLY/job/Chrome/52/api/json?tree=number,building,result,timestamp' | jq
##
## good: https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE/job/HEALTHCHECK-HOURLY/job/Chrome/lastBuild/api/json?tree=number,building,result,timestamp
## bad:  https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE/job/HEALTHCHECK-HOURLY/job/Chrome/lastBuild/api/json?tree=number,building,result,timestamp
## bad:  https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE/job/HEALTHCHECK-HOURLY/job/Chrome/lastBuild/api/json?tree=number,status,timestamp,id,result

mkdir -p ${RPT_DIR}

echo "fetching"
#curl -k -u ${JENKINS_CREDS} ${JENKINS_JOB_URL} -o ${JENKINS_JOB_FILE_1}.txt

## ref: https://stackoverflow.com/questions/48964305/write-output-to-a-file-after-piped-to-jq
#curl -k -u ${JENKINS_CREDS} ${JENKINS_JOB_URL} | jq
#curl -vsS -u ${JENKINS_CREDS} ${JENKINS_JOB_URL} | jq
curl -s -u ${JENKINS_CREDS} ${JENKINS_JOB_URL} | jq . 2>&1 | tee ${RPT_DIR}/${FILENAME}

echo "done"
