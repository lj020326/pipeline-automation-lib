#!/usr/bin/env bash

#set -x

logFile="${0}.log"

writeToLog() {
#    echo -e "${1}" | tee -a "${logFile}"
    echo -e "${1}" >> "${logFile}"
}

main() {

    SITEDIR=${1-"."}
    NGINX_IMAGE=${1-"com-dettonville-api/nginx"}
    PAGERES_IMAGE=${2-"com-dettonville-api/pageres"}
    SNAPSHOT_FILENAME=${3-"serenity-snapshot.png"}

    CMD_NGINX="docker run -d -p 9090:9090 -v "$PWD/nginx":/nginx -v $SITEDIR:/static $NGINX_IMAGE nginx -c /nginx/nginx.conf"
    $CMD_NGINX

    CMD_PAGERES="docker run --rm --net=host -v "$PWD":/data $PAGERES_IMAGE pageres localhost:9090 1024x768 filename=$SNAPSHOT_FILENAME"
    $CMD_PAGERES

}

main $@
