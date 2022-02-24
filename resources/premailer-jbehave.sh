#!/usr/bin/env bash

#set -x

function render_inline_css() {
    local REPORT=$1
    local EMAILABLE_REPORT=$2

    echo "REPORT=${REPORT}"
    echo "EMAILABLE_REPORT=${EMAILABLE_REPORT}"

    cp ${REPORT} ${EMAILABLE_REPORT}

    ## use the following command to generate inline css for purpose to determine pattern used in sed:
    ##
    ## PYTHONIOENCODING=utf_8 python -m premailer -f reports.html --external-style ./style/jbehave-core.css > emailable-reports.html
    ## PYTHONIOENCODING=utf_8 python -m premailer -f stories.GenCSR.html --external-style ./style/jbehave-core.css > emailable-stories.GenCSR.html
    ## PYTHONIOENCODING=utf_8 python -m premailer -f stories.TestLocationServiceViaCsrOnRenewkeySandbox.html --external-style ./style/jbehave-core.css > emailable-stories.TestLocationServiceViaCsrOnRenewkeySandbox.html

    #sed -i 's///g' reports.html
    sed -i -e 's/<div class="path">/<div class="path" style="font-size:16px; font-weight:bolder; margin-bottom:10px; opacity:0.85; padding:5px 10px">/g' \
            -e 's/<div class="meta">/<div class="meta" style="color:purple; margin-bottom:10px; text-align:left" align="left">/g' \
            -e 's/<div class="keyword">/<div class="keyword" style="margin-left:10px; text-weight:bold">/g' \
            -e 's/<div class="property">/<div class="property" style="margin-left:10px">/g' \
            -e 's/<div class="narrative"><h2>/<div class="narrative" style="color:blue; text-align:left" align="left"><h2 style="background-color:#fff; margin-bottom:10px; padding:5px 10px; border-color:#ffcd13" bgcolor="#ffffff">/g' \
            -e 's/<div class="element inOrderTo"><span class="keyword inOrderTo">/<div class="element inOrderTo" style="padding-left:10px"><span class="keyword inOrderTo" style="margin-left:10px; text-weight:bold; font-weight:bold">/g' \
            -e 's/<div class="element asA"><span class="keyword asA">/<div class="element asA" style="padding-left:10px"><span class="keyword asA" style="margin-left:10px; text-weight:bold; font-weight:bold">/g' \
            -e 's/<div class="element iWantTo"><span class="keyword iWantTo">/<div class="element iWantTo" style="padding-left:10px"><span class="keyword iWantTo" style="margin-left:10px; text-weight:bold; font-weight:bold">/g' \
            -e 's/<div class="scenario">/<div class="scenario" style="padding-left:1px; text-align:left" align="left">/g' \
            -e 's/<h2>/<h2 style="background-color:#fff; margin-bottom:10px; padding:5px 10px; border-color:#ffcd13" bgcolor="#ffffff">/g' \
            -e 's/<div class="step">/<div class="step" style="color:black; padding:2px 2px 2px 20px">/g' \
            -e 's/<div class="step successful">/<div class="step successful" style="color:green; padding:2px 2px 2px 20px">/g' \
            -e 's/<div class="step failed">/<div class="step failed" style="color:red; padding:2px 2px 2px 20px">/g' \
            -e 's/<div class="step notPerformed">/<div class="step notPerformed" style="color:brown; padding:2px 2px 2px 20px">/g' \
            -e 's/<colgroup span="\(.*\)" class="\(.*\)">/<colgroup span="\1" class="\2" style="border:1px solid #000">/g' \
            -e 's/<th colspan="\(.*\)".*>/<th colspan="\1" style="border-bottom:1px solid #000; font-weight:bold; padding:10px; text-align:center>/g' \
            -e 's/<th><\/th>/<th style="border-bottom:1px solid #000; font-weight:bold; padding:10px; text-align:center" align="center"><\/th>/g' \
            -e 's/<th>\(.*\)<\/th>/<th style="border-bottom:1px solid #000; font-weight:bold; padding:10px; text-align:center" align="center">\1<\/th>/g' \
            -e 's/<td class="story successful">/<td class="story successful" style="font-size:13px; border-left:1px solid; padding-left:5px; padding-right:5px; text-align:left; margin-left:10px; color:green; padding:5px" align="left">/g' \
            -e 's/<td class="story failed">/<td class="story failed" style="font-size:13px; border-left:1px solid; padding-left:5px; padding-right:5px; text-align:left; margin-left:10px; color:red; padding:5px" align="left">/g' \
            -e 's/^<td>$/<td style="font-size:13px; border-left:1px solid; padding-left:5px; padding-right:5px; text-align:center; padding:5px" align="center">/g' \
            -e 's/^<tr class="\(.*\)">$/<tr class="\1" style="border-top:1px solid #000; font-weight:bold; padding-top:10px">/g' \
            -e 's/^<td colspan="\(.*\)"\/>$/<td colspan="\1" style="font-size:13px; border-left:1px solid; padding-left:5px; padding-right:5px; text-align:center; padding:5px" align="center"><\/td>/g' \
            -e 's/<span class="failed">/<span class="failed" style="color:red">/g' \
            -e 's/<h3>/<h3 style="opacity:0.85">/g' $EMAILABLE_REPORT

#            -e 's/<a *.>//g' \
#            -e 's/<img src=.*/>//g' \


    return 0
}

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
            sed -n '/<body.*>/,/<\/body>/ {
                    /<body.*>/ s/.*<body.*>//
                    /<\/body>/ s/<\/body>.*//
                    p
                }' "$REPORT" >> ${FINAL_REPORT}
        fi
    done

    echo "</body>" >> ${FINAL_REPORT}
    echo "</html>" >> ${FINAL_REPORT}

}

## render inline css -
## e.g., this script is doing far less than a more robust python utility here - but since not needed/necessary - just script using sed for now
##
## python -m premailer -f reports.html --external-style ./style/jbehave-core.css > emailable-reports.html
## python -m premailer -f stories.TestLocationServiceViaCsrOnRenewkeySandbox.html --external-style ./style/jbehave-core.css > emailable-stories.TestLocationServiceViaCsrOnRenewkeySandbox.html
##

REPORT_DIR=target/jbehave/view

if [ ! -d ${REPORT_DIR} ]; then
    echo "No report directory found [${REPORT_DIR}] ...quitting!"
    exit 1
fi

cd ${REPORT_DIR}

STORY_REPORTS=($(grep "stories.*.html" reports.html | cut -d"|" -f2 | sed -n 's/.*href="\([^"]*\).*/\1/p'))
#STORY_REPORTS=$(find . -name stories.*.html -type f)
STORY_REPORTS=("reports.html" "${STORY_REPORTS[@]}")

echo "STORY_REPORTS=[${STORY_REPORTS[@]}]"

REPORT_LIST=()

for REPORT in "${STORY_REPORTS[@]}"; do
    echo "for: REPORT=${REPORT}"
    EMAILABLE_REPORT="emailable.${REPORT}"
#    echo "Rendering mailable inline css for ${REPORT_FILE}"
    render_inline_css ${REPORT} ${EMAILABLE_REPORT}
#    echo "for: EMAILABLE_REPORT=${EMAILABLE_REPORT}"
#    echo "Adding REPORT_FILE [$REPORT_FILE] to REPORT_LIST"
    REPORT_LIST+=("${EMAILABLE_REPORT}")
done

echo "REPORT_LIST=[${REPORT_LIST[@]}]"
#merge_html_reports 'target/jbehave/view' "${REPORT_LIST[@]}"
#merge_html_reports 'target/jbehave/view' $REPORT_LIST
merge_html_reports "${REPORT_LIST[@]}"




