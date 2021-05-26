#!/usr/bin/env bash

#set -x

##
## ref: https://developer.atlassian.com/server/confluence/confluence-rest-api-examples/
## ref: https://serverfault.com/questions/861821/how-to-get-data-from-jenkins-to-confluence-cloud
##

scriptName="${0%.*}"
logFile="${scriptName}.log"

writeToLog() {
    echo -e "${1}" | tee -a "${logFile}"
#    echo -e "${1}" >> "${logFile}"
}

main() {

    USER=$1
    PASSWD=$2
#    INPUT_REPORT=${3-"index.html"}
#    FINAL_REPORT=${4-"emailable.foobar.html"}

#    SPACE=${3-"MAPI"}
    SPACE_KEY=${3-"~ljohnson"}
    PAGE_TITLE=${4-"Copy+of+Gitflow+Workflow"}
    HTML_OUTPUT=${5-"<p>This is the updated text for the new page</p>"}
    BASE_URL=${6-"https://fusion.dettonville.int/confluence/rest/api/content"}

    PAGE_URL="${BASE_URL}?spaceKey=${SPACE_KEY}&title=${PAGE_TITLE}"

    writeToLog "getting PAGE_ID"
    PAGE_ID=$(curl -u $USER:$PASSWD -X GET $PAGE_URL | jq -r .results[].id)
    PAGE_ID_URL="${BASE_URL}/${PAGE_ID}"
    PAGE_VERSION=2

    writeToLog "PAGE_ID=${PAGE_ID}"

    writeToLog "getting page"
    curl -u $USER:$PASSWD -X GET $PAGE_URL | python -mjson.tool

    writeToLog "updating page"

    RESULT=$(curl -u $USER:$PASSWD -X PUT -H 'Content-Type: application/json' \
        $PAGE_ID_URL \
        --data @- <<END;
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
END
)

    `cat ${RESULT} | python -mjson.tool`

}

main $@
