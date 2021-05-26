#!/usr/bin/env bash

#DATE=`date +%Y%m%d.%H%M%S`
DATE=`date +%Y%m%d.%H%M`

BS_IDENTIFIER="DCAPI-LOCAL-TEST-${USER}-${DATE}"
BSLOGFILE="/tmp/bs-agent-log-${DATE}.log"
BS_AGENT_EXECUTABLE="BrowserStackLocal"

#METAFILTERTAG='+smoke'
METAFILTERTAG='+TestId TC1025418'
APPENV="STAGE_EXTERNAL"
BROWSER="CHROME"
BROWSERVERSION="73"

usage() {
    echo "" 1>&2
    echo "Usage: ${0} [options]" 1>&2
    echo "" 1>&2
    echo "  Options:" 1>&2
    echo "     -t meta-filter-tag : meta filter tag used, defaults to '${METAFILTERTAG}'" 1>&2
    echo "     -e app-environment : environment used, defaults to '${APPENV}'" 1>&2
    echo "     -b browser : browser used, defaults to '${BROWSER}'" 1>&2
    echo "     -v browser-version : browser verison used, defaults to '${BROWSERVERSION}'" 1>&2
    echo "" 1>&2
    echo "  Examples:" 1>&2
    echo "     ${0}"
    echo "     ${0} -t +smoke"
    echo "     ${0} -t +APIGridProduction -e STAGE"
    echo "     ${0} -t +APIGridProduction -e STAGE -b FIREFOX"
    echo "     ${0} -t +APIGridProduction -e STAGE -b FIREFOX -v 54"
    echo "     ${0} -t 'TestId TC1025418'"
    exit 1
}


run_local_test() {

    BROWSER_LOWERCASE=$(tr [A-Z] [a-z] <<< "${BROWSER}")
    BROWSER_UPPERCASE=$(tr [a-z] [A-Z] <<< "${BROWSER}")

    if [ -z "$BROWSERSTACK_USER" ]; then
        echo "For this script to work:"
        echo "env var BROWSERSTACK_USER must be defined"
        echo "quitting!"
        exit 1
    fi
    if [ -z "$BROWSERSTACK_KEY" ]; then
        echo "For this script to work:"
        echo "env var BROWSERSTACK_KEY must be defined"
        echo "quitting!"
        exit 1
    fi

    ## ref: https://stackoverflow.com/questions/6569478/detect-if-executable-file-is-on-users-path
    path_to_executable=$(which ${BS_AGENT_EXECUTABLE})
    if [ -x "${path_to_executable}" ] ; then
        echo "BS AGENT is found here: ${path_to_executable}"
    else
        echo "BS AGENT EXECUTABLE ${BS_AGENT_EXECUTABLE} not found in user PATH=${PATH}. "
        echo ""
        echo "For this script to work, either:"
        echo "(1) copy/move the ${BS_AGENT_EXECUTABLE} binary to an existing PATH directory or"
        echo "(2) update your PATH env var to include the directory where the ${BS_AGENT_EXECUTABLE} binary is located"
        echo "quitting!"
        exit 1
    fi

    ${BS_AGENT_EXECUTABLE} --key ${BROWSERSTACK_KEY} --local-identifier ${BS_IDENTIFIER} --verbose 3 2>&1 | tee "${BSLOGFILE}" &
#    ${BS_AGENT_EXECUTABLE} --key ${BROWSERSTACK_KEY} --local-identifier ${BS_IDENTIFIER} --verbose 3 > ${BSLOGFILE} 2>&1 &

    mvn clean integration-test \
        -Denv=${APPENV} \
        -Dbrowserstack.local=true \
        -Dbrowserstack.localIdentifier=${BS_IDENTIFIER} \
        -Dbrowserstack.project=${BS_IDENTIFIER} \
        -Dbrowserstack.hub.url=http://${BROWSERSTACK_USER}:${BROWSERSTACK_KEY}@hub-cloud.browserstack.com/wd/hub \
        -Dbrowserstack.resolution=1920x1080 \
        -Dbrowserstack.web.os=Windows \
        -Dbrowserstack.web.os.version=10 \
        -Dbrowserstack.idleTimeout=300 \
        -Dbrowserstack.web.localfileupload=true \
        -Dbrowserstack.accept.ssl.certs=true \
        -Dbrowserstack.debug=true \
        -Dbrowserstack.${BROWSER_LOWERCASE}.ignore.cert=true \
        -Dbrowserstack.acceptInsecureCerts=true \
        -Dbrowserstack.networkLogs=true \
        -Dplatform=${BROWSER_LOWERCASE} \
        -Ddefault.web.execution.platform=BROWSERSTACK_${BROWSER_UPPERCASE} \
        -Dbrowserstack.${BROWSER_LOWERCASE}.version=${BROWSERVERSION} \
        -Dbrowserstack.proxy.host= \
        -Dbrowserstack.proxy.port= \
        -DstoryName= \
        -Dmeta.filter="${METAFILTERTAG}" \
        -DserenityReport=true -e
}


while getopts "t:e:b:v:hx" opt; do
    case "${opt}" in
        t) METAFILTERTAG="${OPTARG}" ;;
        e) APPENV="${OPTARG}" ;;
        b) BROWSER="${OPTARG}" ;;
        v) BROWSERVERSION="${OPTARG}" ;;
        h) usage 1 ;;
        \?) usage 2 ;;
        :)
            echo "Option -$OPTARG requires an argument." >&2
            usage
            ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

additional_args=""
if [ $# -gt 0 ]; then
    additional_args=$@
fi

run_local_test "${additional_args}"
