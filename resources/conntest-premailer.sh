#!/usr/bin/env bash

#set -x


## ref: https://www.unix.com/os-x-apple-/264634-how-join-several-html-files.html
merge_html_reports() {
    local REPORT_LIST=("$@")

    local FINAL_REPORT="emailable.report.html"

    echo "merge: REPORT_LIST=[${REPORT_LIST[@]}]"
    echo "merge: FINAL_REPORT=[$FINAL_REPORT]"

    local lFirst=1 #  flag for first file

    for REPORT in "${REPORT_LIST[@]}"; do
        echo "merge: REPORT=[$REPORT]"

        if [[ $lFirst -eq 1 ]] ; then              # first file is copied up to "</body>"
            echo "****FIRST PASS"
            sed '/<\/body>/ {
                s/<\/body>.*//
                :loop
                   n
                   /.*/d
                b loop
                }' "${REPORT}" > ${FINAL_REPORT}
            lFirst=0
        else
            echo "****NOT FIRST PASS"
            # subsequent files, only the content
            # of "<body>...</body>"
            sed -n '/<body.*>/,/<\/body>/ {
                    /<body.*>/ s/.*<body.*>//
                    /<\/body>/ s/<\/body>.*//
                    p
                }' "${REPORT}" >> ${FINAL_REPORT}
        fi
    done

    echo "</body>" >> ${FINAL_REPORT}
    echo "</html>" >> ${FINAL_REPORT}

}


REPORT_DIR=./pipelineSummary

echo "REPORT_DIR=${REPORT_DIR}"
#curr_dir=$(pwd)
#echo "**** pwd=${curr_dir}"
find ${REPORT_DIR} -type f

if [ ! -d ${REPORT_DIR} ]; then
    echo "No report directory found [${REPORT_DIR}] ...quitting!"
    exit 1
fi

cd ${REPORT_DIR}

FINAL_REPORT="emailable.report.html"
if [ -f ${FINAL_REPORT} ]; then
    echo "${FINAL_REPORT} report found - removing it"
    rm ${FINAL_REPORT}
fi

#curr_dir=`pwd`
#echo "**** pwd=${curr_dir}"

REPORT_LIST=$(find . -type f -name "*.html")

echo "REPORT_LIST=[${REPORT_LIST[@]}]"
#merge_html_reports 'target/jbehave/view' "${REPORT_LIST[@]}"
#merge_html_reports 'target/jbehave/view' $REPORT_LIST
#merge_html_reports "${REPORT_LIST[@]}"
merge_html_reports ${REPORT_LIST}




