#!/usr/bin/env bash

##
## ref: https://stackoverflow.com/questions/3659602/automating-enter-keypresses-for-bash-script-generating-ssh-keys
##

USER=$1

#ssh-keygen -t rsa -b 4096 -C "comment" -P "examplePassphrase" -f "desired pathAndName" -q

#echo -e "\n\n\n" | ssh-keygen -t rsa -b 4096 -N "" -f ssh.user-${USER}.key -q >/dev/null
#echo -e "\n\n\n" | ssh-keygen -t rsa -b 4096 -N "" -f ssh.user-${USER}.key >/dev/null

#yes "y" | ssh-keygen -t rsa -b 4096 -N "" -f ssh.user-${USER}.key >/dev/null
yes "y" | ssh-keygen -t rsa -b 4096 -N "" -f ssh.user-${USER}.key
