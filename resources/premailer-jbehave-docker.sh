#!/usr/bin/env bash

#set -x

logFile="${0}.log"

writeToLog() {
#    echo -e "${1}" | tee -a "${logFile}"
    echo -e "${1}" >> "${logFile}"
}

## ref: https://www.unix.com/os-x-apple-/264634-how-join-several-html-files.html
merge_html_reports() {
    local MERGED_REPORT=$1
    shift
    local REPORT_LIST=("$@")

    writeToLog "merge: REPORT_LIST=[${REPORT_LIST[@]}]"
    writeToLog "merge: MERGED_REPORT=[$MERGED_REPORT]"

    local lFirst=1 #  flag for first file

    for REPORT in "${REPORT_LIST[@]}"; do
        writeToLog "merge: REPORT=$REPORT"

        if [[ $lFirst -eq 1 ]] ; then              # first file is copied up to "</body>"
            writeToLog "****FIRST PASS"
            sed '/<\/body>/ {
                s/<\/body>.*//
                :loop
                   n
                   /.*/d
                b loop
                }' "$REPORT" > ${MERGED_REPORT}
            lFirst=0
        else
            writeToLog "****NOT FIRST PASS"
            # subsequent files, only the content
            # of "<body>...</body>"
            sed -n '/<body/,/<\/body>/ {
                    /<body/ s/.*<body.*>//
                    /<\/body>/ s/<\/body>.*//
                    p
                }' "$REPORT" >> ${MERGED_REPORT}
        fi
    done

    echo "</body>" >> ${MERGED_REPORT}
    echo "</html>" >> ${MERGED_REPORT}

}

main() {

    PYUTILS_IMAGE=${1-"docker-pyutils:master"}
    PYSCRIPT=${2-"standalone_html.py"}
    BASE_URL=${3-"./"}
    FINAL_REPORT=${4-"emailable.foobar.html"}

    STORY_REPORTS=($(grep "stories.*.html" reports.html | cut -d"|" -f2 | sed -n 's/.*href="\([^"]*\).*/\1/p'))
    #STORY_REPORTS=$(find target/jbehave/view -name stories.*.html -type f)

#    writeToLog "STORY_REPORTS (before prepend)=[${STORY_REPORTS[@]}]"
    STORY_REPORTS=("reports.html" "${STORY_REPORTS[@]}")

    writeToLog "STORY_REPORTS=[${STORY_REPORTS[@]}]"

    ########################
    # Execute Premailer
    ########################

    ## render inline css -
    ## e.g., this script is doing far less than a more robust python utility here - but since not needed/necessary - just script using sed for now
    ##
    ## python -m premailer -f reports.html --external-style ./style/jbehave-core.css > emailable-reports.html
    ## python -m premailer -f stories.TestLocationServiceViaCsrOnRenewkeySandbox.html --external-style ./style/jbehave-core.css > emailable-stories.TestLocationServiceViaCsrOnRenewkeySandbox.html
    ##
    REPORT_LIST=()

    for REPORT in "${STORY_REPORTS[@]}"; do
        writeToLog "for: REPORT=${REPORT}"
        EMAILABLE_REPORT_FILE0=emailable-0.${REPORT}
        EMAILABLE_REPORT_FILE=emailable.${REPORT}

        docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python ${PYSCRIPT} ${REPORT} ${EMAILABLE_REPORT_FILE0}

        writeToLog "Rendering mailable inline css for ${REPORT_FILE}"
    #    PYTHONIOENCODING=utf_8 python -m premailer -f ${REPORT} --external-style style/jbehave-core.css > ${EMAILABLE_REPORT_FILE}
    #    docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f index.html > ${EMAILABLE_REPORT_FILE}
#        docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${REPORT} --external-style=style/jbehave-core.css --base-path=. --base-url=${BASE_URL} --pretty --disable-validation --preserve-style-tags --exclude-pseudoclasses > ${EMAILABLE_REPORT_FILE}
#        docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${REPORT} --external-style=style/jbehave-core.css --base-path=. --base-url=${BASE_URL} --pretty --disable-validation > ${EMAILABLE_REPORT_FILE}
#        docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${REPORT} --cssutils-logging-level=logging.CRITICAL --external-style=style/jbehave-core.css --base-path=. --base-url=${BASE_URL} --pretty --disable-validation &> $0.log 1> ${EMAILABLE_REPORT_FILE}
#        docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${REPORT} --external-style=style/jbehave-core.css --base-path=. --base-url=${BASE_URL} --pretty --disable-validation &> $0.log 1> ${EMAILABLE_REPORT_FILE}
        docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${EMAILABLE_REPORT_FILE0} --external-style=style/jbehave-core.css --base-path=. --base-url=${BASE_URL} --pretty --disable-validation &> $0.log 1> ${EMAILABLE_REPORT_FILE}

    #    writeToLog "Adding REPORT_FILE [$REPORT_FILE] to REPORT_LIST"
        REPORT_LIST+=("${EMAILABLE_REPORT_FILE}")
    #    writeToLog "for: REPORT_LIST=[${REPORT_LIST[@]}]"
    done

    writeToLog "REPORT_LIST=[${REPORT_LIST[@]}]"
    #pandoc --self-contained -o ${FINAL_REPORT} -V "pagetitle:JBhehave Test Results" -V "title: UI Test Results" -s "${REPORT_LIST[@]}"

    merge_html_reports $FINAL_REPORT "${REPORT_LIST[@]}"
}

main $@
