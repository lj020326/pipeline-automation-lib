#!/usr/bin/expect
# just do a chmod 755 one the script
# ./YOUR_SCRIPT_NAME.sh $YOUHOST $PORT
# if you get "Escape character is '^]'" as the output it means got connected otherwise it has failed

set ip [lindex $argv 0]
set port [lindex $argv 1]

set timeout 5
spawn telnet $ip $port
expect "'^]'."

