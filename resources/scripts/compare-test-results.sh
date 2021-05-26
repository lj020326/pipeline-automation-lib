#!/usr/bin/env bash

#curl https://cd.dettonville.int/jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/STAGE_EXTERNAL/job/REGRESSION/job/Chrome/63/consoleText -o

JENKINS_CREDS="ljohnson:a0fee46aacab91f44cffa109c1ceb76e"
JENKINS_URL="cd.dettonville.int"

QA_APP_ENV="STAGE_EXTERNAL"
QA_TEST_TYPE="REGRESSION"
QA_JOB_FOLDER="jenkins/job/DCAPI/job/Acceptance_Test_Jobs/job/${QA_APP_ENV}/job/${QA_TEST_TYPE}/job/Chrome"

JENKINS_JOB_ID_1=63
JENKINS_JOB_ID_2=64

JENKINS_JOB_URL_1="https://${JENKINS_URL}/${QA_JOB_FOLDER}/${JENKINS_JOB_ID_1}/consoleText"
JENKINS_JOB_URL_2="https://${JENKINS_URL}/${QA_JOB_FOLDER}/${JENKINS_JOB_ID_2}/consoleText"

JENKINS_JOB_FILE_PREFIX="save/consoleText-${QA_APP_ENV}-${QA_TEST_TYPE}"
JENKINS_JOB_FILE_1="${JENKINS_JOB_FILE_PREFIX}-${JENKINS_JOB_ID_1}"
JENKINS_JOB_FILE_2="${JENKINS_JOB_FILE_PREFIX}-${JENKINS_JOB_ID_2}"

if [ ! -f ${JENKINS_JOB_FILE_1}.txt ]; then
    echo "downloading ${JENKINS_JOB_FILE_1}.txt"
    curl -k -u ${JENKINS_CREDS} ${JENKINS_JOB_URL_1} -o ${JENKINS_JOB_FILE_1}.txt
fi
if [ ! -f ${JENKINS_JOB_FILE_2}.txt ]; then
    echo "downloading ${JENKINS_JOB_FILE_2}.txt"
    curl -k -u ${JENKINS_CREDS} ${JENKINS_JOB_URL_2} -o ${JENKINS_JOB_FILE_2}.txt
fi

grep -e "Running on " -e "TEST STARTED:" -e "TEST PASSED:" ${JENKINS_JOB_FILE_1}.txt | sort > ${JENKINS_JOB_FILE_1}-teststatus.txt
grep -e "Running on " -e "TEST STARTED:" -e "TEST PASSED:" ${JENKINS_JOB_FILE_2}.txt | sort > ${JENKINS_JOB_FILE_2}-teststatus.txt

echo "*************************"
echo "TEST RUN DIFFS"
sdiff -s ${JENKINS_JOB_FILE_1}-teststatus.txt ${JENKINS_JOB_FILE_2}-teststatus.txt

#grep -e "Running on " ${JENKINS_JOB_FILE_1}.txt | sort > ${JENKINS_JOB_FILE_1}-nodes.txt
#grep -e "Running on " ${JENKINS_JOB_FILE_2}.txt | sort > ${JENKINS_JOB_FILE_2}-nodes.txt
#
#grep -e "TEST STARTED:" -e "TEST PASSED:" ${JENKINS_JOB_FILE_1}.txt | sort > ${JENKINS_JOB_FILE_1}-teststatus.txt
#grep -e "TEST STARTED:" -e "TEST PASSED:" ${JENKINS_JOB_FILE_2}.txt | sort > ${JENKINS_JOB_FILE_2}-teststatus.txt
#
#echo "*************************"
#echo "NODE RUN DIFFS"
#sdiff -s ${JENKINS_JOB_FILE_1}-teststatus.txt ${JENKINS_JOB_FILE_2}-nodes.txt
#
#echo "*************************"
#echo "TEST RUN DIFFS"
#sdiff -s ${JENKINS_JOB_FILE_1}-teststatus.txt ${JENKINS_JOB_FILE_2}-teststatus.txt
