#!/usr/bin/env bash
# just do a chmod 755 one the script
# ./YOUR_SCRIPT_NAME.sh $YOUHOST $PORT $TIMEOUT
# if you get "Escape character is '^]'" as the output it means got connected otherwise it has failed
# ref: https://serverfault.com/questions/297976/using-telnet-in-shell-script
# ref: https://stackoverflow.com/questions/7013137/automating-telnet-session-using-bash-scripts

set -x

DATE=`date +%Y-%m-%d`
TIME=`date +%H%M%S`

host=$1
port=$2

#echo "$DATE $TIME: host=[$host] port=[$port]"
#echo "$DATE $TIME: \$0 = [$0]"
if [[ $0 != *"testConnTelnet.sh" ]]; then
    echo "calling with bash -c - shifting args"
    host=$0
    port=$1
fi

echo "$DATE $TIME: testing telnet connection to host=[$host] port=[$port]"
if telnet -c ${host} ${port} </dev/null 2>&1 | grep -q "Escape character is '^]'"; then
    echo "$DATE $TIME: Connected to ${host} ${port}"
    exit 0
else
    echo "$DATE $TIME: Failed to connect to ${host} ${port}"
    exit 1
fi

#expect "'^]'."

