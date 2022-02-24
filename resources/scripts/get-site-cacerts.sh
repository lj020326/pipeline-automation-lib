#!/usr/bin/env bash

## ref: https://unix.stackexchange.com/questions/368123/how-to-extract-the-root-ca-and-subordinate-ca-from-a-certificate-chain-in-linux

#DATE=`date +&%%m%d%H%M%S`
DATE=`date +%Y%m%d`

ENDPOINT="cd.dettonville.int:443"
IFS=':' read -r -a array <<< "${ENDPOINT}"
host=${array[0]}
port=${array[1]}

alias="${host}_${port}"

certs_dir=${HOME}/.certs/${alias}/${DATE}

if [ ! -d ${certs_dir} ]; then
    mkdir -p ${certs_dir}
fi

cd ${certs_dir}

openssl s_client -showcerts -verify 5 -connect ${ENDPOINT} < /dev/null \
    | awk '/BEGIN/,/END/{ if(/BEGIN/){a++}; out="cert"a".crt"; print >out}' && \
    for cert in *.crt;
    do
        newname=$(echo "${cert}" | cut -f 1 -d '.')-$(openssl x509 -noout -subject -in ${cert} | sed -n 's/, /,/g; s/ = /=/g; s/^.*CN=\(.*\)$/\1/; s/[ ,.*]/_/g; s/__/_/g; s/^_//g;p').pem
        mv $cert $newname;
    done

#openssl s_client -showcerts -verify 5 -connect ${ENDPOINT} < /dev/null | awk '/BEGIN/,/END/{ if(/BEGIN/){a++}; out="cert"a".pem"; print >out}'
#for cert in *.pem;
#do
#    newname=$(echo "${cert}" | cut -f 1 -d '.')-$(openssl x509 -noout -subject -in ${cert} | sed -n 's/, /,/g; s/ = /=/g; s/^.*CN=\(.*\)$/\1/; s/[ ,.*]/_/g; s/__/_/g; s/^_//g;p').pem
#    echo "newname=${newname}"
#    mv $cert $newname;
#done


