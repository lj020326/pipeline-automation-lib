#!/usr/bin/env bash

set -x

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    CYGWIN*)    machine=Cygwin;;
    MINGW*)     machine=MinGW;;
    MSYS*)      machine=MSYS;;
    *)
esac

echo "setup premailer"
#if [[ "${machine}" == "Linux" ]]; then
#    #sudo yum install -y python-devel python-setuptools python-pip
#    #sudo yum install --upgrade pip
#    sudo yum install -y pandoc
#fi
#sudo pip install virtualenv
#

########################
# Step 1: Setup virtualenv
# This step is only for Jenkins. Travis and CircleCI will ignore this step.
########################
if [ -n "${WORKSPACE:+1}" ]; then
    # Path to virtualenv cmd installed by pip
    # /usr/local/bin/virtualenv
    PATH=$WORKSPACE/venv/bin:/usr/local/bin:$PATH
    if [ ! -d "venv" ]; then
            virtualenv venv
    fi
    . venv/bin/activate
else
    # Alternatively, $TRAVIS_REPO_SLUG could be utilized here to provide name.
    export JOB_NAME="ci-stat"
fi
#pip install -r requirements.txt -r test/test_requirements.txt --cache-dir /tmp/$JOB_NAME
#pip install premailer --cache-dir /tmp/$JOB_NAME --extra-index-url http://pip.croscon.com:8088/simple/ --trusted-host pip.croscon.com
pip install premailer --cache-dir /tmp/$JOB_NAME --extra-index-url http://repo.dettonville.int/artifactory/api/pypi/pypi-remote/simple/

########################
# Step 2: Execute Premailer
########################

## ref: https://www.unix.com/os-x-apple-/264634-how-join-several-html-files.html
merge_html_reports() {
    local REPORT_LIST=("$@")

    local FINAL_REPORT="emailable.report.html"

    echo "merge: REPORT_LIST=[${REPORT_LIST[@]}]"
    echo "merge: FINAL_REPORT=[$FINAL_REPORT]"

    local lFirst=1 #  flag for first file

    for REPORT in "${REPORT_LIST[@]}"; do
        echo "merge: REPORT=$REPORT"

        if [[ $lFirst -eq 1 ]] ; then              # first file is copied up to "</body>"
            echo "****FIRST PASS"
                sed '/<\/body>/ {
                    s/<\/body>.*//
                    :loop
                       n
                       /.*/d
                    b loop
                    }' "$REPORT" > ${FINAL_REPORT}
            lFirst=0
        else
            echo "****NOT FIRST PASS"
            # subsequent files, only the content
            # of "<body>...</body>"
            sed -n '/<body/,/<\/body>/ {
                    /<body/ s/.*<body.*>//
                    /<\/body>/ s/<\/body>.*//
                    p
                }' "$REPORT" >> ${FINAL_REPORT}
        fi
    done

    echo "</body>" >> ${FINAL_REPORT}
    echo "</html>" >> ${FINAL_REPORT}

}

REPORT_DIR=target/jbehave/view
cd ${REPORT_DIR}

## render inline css -
## e.g., this script is doing far less than a more robust python utility here - but since not needed/necessary - just script using sed for now
##
## python -m premailer -f reports.html --external-style ./style/jbehave-core.css > emailable-reports.html
## python -m premailer -f stories.TestLocationServiceViaCsrOnRenewkeySandbox.html --external-style ./style/jbehave-core.css > emailable-stories.TestLocationServiceViaCsrOnRenewkeySandbox.html
##

STORY_REPORTS=($(grep "stories.*.html" reports.html | cut -d"|" -f2 | sed -n 's/.*href="\([^"]*\).*/\1/p'))
#STORY_REPORTS=$(find target/jbehave/view -name stories.*.html -type f)

echo "STORY_REPORTS (before prepend)=[${STORY_REPORTS[@]}]"
STORY_REPORTS=("reports.html" "${STORY_REPORTS[@]}")
echo "STORY_REPORTS=[${STORY_REPORTS[@]}]"

REPORT_LIST=()

for REPORT in "${STORY_REPORTS[@]}"; do
    echo "for: REPORT=${REPORT}"
    EMAILABLE_REPORT_FILE=emailable.${REPORT}

    echo "Rendering mailable inline css for ${REPORT_FILE}"
    PYTHONIOENCODING=utf_8 python -m premailer -f ${REPORT} --external-style style/jbehave-core.css > ${EMAILABLE_REPORT_FILE}

#    echo "Adding REPORT_FILE [$REPORT_FILE] to REPORT_LIST"
    REPORT_LIST+=("${EMAILABLE_REPORT_FILE}")
#    echo "for: REPORT_LIST=[${REPORT_LIST[@]}]"
done

echo "REPORT_LIST=[${REPORT_LIST[@]}]"
FINAL_REPORT="emailable.report.html"
#pandoc --self-contained -o ${FINAL_REPORT} -V "pagetitle:JBhehave Test Results" -V "title: UI Test Results" -s "${REPORT_LIST[@]}"

merge_html_reports "${REPORT_LIST[@]}"


