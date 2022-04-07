#!/usr/bin/env bash

#set -x

logFile="${0}.log"

writeToLog() {
#    echo -e "${1}" | tee -a "${logFile}"
    echo -e "${1}" >> "${logFile}"
}

main() {

    PYUTILS_IMAGE=${1-"docker-pyutils:latest"}
    BASE_URL=${2-"./"}
    INPUT_REPORT=${3-"index.html"}
    FINAL_REPORT=${4-"emailable.foobar.html"}

#    inline_files=`grep -e .css -e .js ${INPUT_REPORT} | grep -v "data:image" | sed -e 's/.*href="//g' | sed -e 's/.*src="//g' | sed -e 's/".*//g' | grep -v ie7 | uniq`
    inline_files=`grep -e .css ${INPUT_REPORT} | grep -v "data:image" | sed -e 's/.*href="//g' | sed -e 's/.*src="//g' | sed -e 's/".*//g' | grep -v ie7 | uniq`

    EXTERNAL_STYLES=""

    for file in $inline_files; do
        EXTERNAL_STYLES+=" --external-style $file"
    done

    writeToLog "EXTERNAL_STYLES=${EXTERNAL_STYLES}"

    writeToLog "Rendering mailable inline css for ${FINAL_REPORT}"
#    docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${INPUT_REPORT} ${EXTERNAL_STYLES} --base-path=. --base-url=${BASE_URL} --pretty --disable-validation > ${FINAL_REPORT}
    docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer -f ${INPUT_REPORT} ${EXTERNAL_STYLES} --base-path=. --base-url=${BASE_URL} --pretty --disable-validation &> $0.log 1> ${FINAL_REPORT}

    ## To hide css util warnings/errors:
    ## ref: https://github.com/peterbe/premailer/issues/161
#    docker run --rm -v $PWD:/app ${PYUTILS_IMAGE} python -m premailer --cssutils-logging-level=logging.CRITICAL -f ${INPUT_REPORT} ${EXTERNAL_STYLES} --base-path=. --base-url=${BASE_URL} --pretty --disable-validation &> $0.log 1> ${FINAL_REPORT}


}

main $@
