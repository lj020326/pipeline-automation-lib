#!/usr/bin/env bash

nslookup_list="stage.api.dettonville.org
stage.proxy.api.dettonville.org
stage.developer.dettonville.org
stage.iapp.dettonville.int
stage.sso.iapp.dettonville.int
stage.dcapiadmin.dettonville.org
stage2.dcapiadmin.dettonville.org
sbx.stage.api.dettonville.org
api.dettonville.org
sandbox.api.dettonville.org
sandbox.proxy.api.dettonville.org
developer.dettonville.org
iapp.dettonville.int
sso.iapp.dettonville.int"


scriptName=`basename $0 | cut -f1 -d'.'`
logDir=`pwd`/save
logFile="${logDir}/${scriptName}.log"

writeToLog() {
    echo -e "${1}" | tee -a "${logFile}"
}

echo "logFile=$logFile"

if [ -f $logFile ]; then
    rm $logFile
fi

for endpoint in $nslookup_list
do
    cmd="dig +short $endpoint"

    results=$(eval $cmd)

    output=`echo $results | sed -e 's/ /==>/g'`

    writeToLog "$endpoint|$output"
done

