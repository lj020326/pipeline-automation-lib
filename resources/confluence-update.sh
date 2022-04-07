#!/usr/bin/env bash

#set -x

##
## https://stackoverflow.com/questions/17029902/using-curl-post-with-variables-defined-in-bash-script-functions
## ref: https://developer.atlassian.com/server/confluence/confluence-rest-api-examples/
## ref: https://serverfault.com/questions/861821/how-to-get-data-from-jenkins-to-confluence-cloud
##
## http://www.amnet.net.au/~ghannington/confluence/readme.html
##
## https://gist.github.com/dodok1/4352500
## https://avleonov.com/2018/01/18/confluence-rest-api-for-reading-and-updating-wiki-pages/
## https://www.openmakesoftware.com/restful-json-api-calls-jenkins-pipeline/
## https://jenkins.io/blog/2016/07/18/pipeline-notifications/
## https://gist.github.com/xseignard/1422531
## https://stackoverflow.com/questions/38978295/using-pipeline-groovy-how-can-i-extract-test-results-from-jenkins-for-my-curr
##
## to confirm xml storage content xml
## xmllint --schema confluence.xsd yourxml.xml --noout
## ref: https://stackoverflow.com/questions/42809088/how-to-validate-a-xml-file-with-xsd-through-xmllint
## http://www.amnet.net.au/~ghannington/confluence/docs/confluence/s-confluence.xsd.html
## http://www.amnet.net.au/~ghannington/confluence/docs/confluence/ss.html
##
## https://stackoverflow.com/questions/16090869/how-to-pretty-print-xml-from-the-command-line

scriptName="${0%.*}"
logFile="${scriptName}.log"

if [ -f $logFile ]; then
    rm $logFile
fi

writeToLog() {
    echo -e "${1}" | tee -a "${logFile}"
#    echo -e "${1}" >> "${logFile}"
}

generate_post_data()
{
#  nl=$'\n'
#IFS= read -r -d '' var <<'EOF' ||:
#IFS='' read -r -d '' jsonStr <<"EOF"
#IFS=''
  cat <<EOF
{
    "id":"${PAGE_ID}",
    "type":"page",
    "title":"${PAGE_TITLE}",
    "space":{"key":"${SPACE_KEY}"},
    "body":{
        "storage":{
            "value":"${HTML_OUTPUT}",
            "representation":"storage"
            }
        },
    "version": {
        "number": ${PAGE_VERSION},
        "minorEdit": true
    }
}
EOF
}

## ref: https://fusion.dettonville.int/confluence/display/~ljohnson/Copy+of+Gitflow+Workflow

main() {

    USER=$1
    PASSWD=$2
#    INPUT_REPORT=${3-"index.html"}
#    INPUT_REPORT=${3-"resources/testdata/emailable.html"}
    INPUT_REPORT=${3-"resources/testdata/emailable.html"}
#    SPACE_KEY=${4-"MAPI"}
    SPACE_KEY=${4-"~ljohnson"}
    PAGE_TITLE=${5-"Copy+of+Gitflow+Workflow"}
#    PAGE_TITLE=${5-"Copy\ of\ Gitflow\ Workflow"}
    BASE_URL=${6-"https://fusion.dettonville.int/confluence/rest/api/content"}

#    HTML_OUTPUT=${7-"<p>This is the updated text for the new page</p>"}
    HTML_OUTPUT="$(<${INPUT_REPORT})"

    writeToLog "replace newlines in html_output"
#    HTML_OUTPUT=$(printf '%q\n' "${HTML_OUTPUT}")
    HTML_OUTPUT=$(echo "${HTML_OUTPUT}" | tr '\n' ' ' | sed 's/"/\\"/g' | sed 's/\\#/\#/g')
#    HTML_OUTPUT=$(printf '%q' ${HTML_OUTPUT})

    writeToLog "HTML_OUTPUT=${HTML_OUTPUT}"

    PAGE_URL="${BASE_URL}?spaceKey=${SPACE_KEY}&title=${PAGE_TITLE}"

    ##
    ## using method describe here:
    ## ref: https://serverfault.com/questions/861821/how-to-get-data-from-jenkins-to-confluence-cloud
    ##
    writeToLog "1) get PAGE_ID of page with PAGE_TITLE=${PAGE_TITLE}"
    PAGE_ID=$(curl -u $USER:$PASSWD -X GET "$PAGE_URL" | jq -r .results[].id)

    writeToLog "PAGE_ID=${PAGE_ID}"

    if [ -z "$PAGE_ID" ]; then
        writeToLog "PAGE_ID not found"
        exit 1
    fi

    PAGE_ID_URL="${BASE_URL}/${PAGE_ID}"

    writeToLog "2) get PAGE_VERSION for PAGE_ID=${PAGE_ID}"
    PAGE_VERSION=$(curl -u $USER:$PASSWD -X GET "$PAGE_ID_URL?expand=version" | jq .version.number)
    PAGE_TITLE=$(curl -u $USER:$PASSWD -X GET "$PAGE_ID_URL?expand=version" | jq .title | sed 's/\"//g')

    writeToLog "incrementing PAGE_VERSION=${PAGE_VERSION}"
    ((PAGE_VERSION++))
    writeToLog "PAGE_VERSION=${PAGE_VERSION}"

#    writeToLog "getting page"
#    curl -u $USER:$PASSWD -X GET $PAGE_URL | python -mjson.tool

    writeToLog "updating page"
    curl -u $USER:$PASSWD -X PUT -H 'Content-Type: application/json' \
        $PAGE_ID_URL  --data "$(generate_post_data)"  | python -mjson.tool

}

main $@
