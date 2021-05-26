#!/usr/bin/env bash


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SCRIPT_NAME=`basename $0`
echo "SCRIPT_DIR=[${SCRIPT_DIR}] SCRIPT_NAME=${SCRIPT_NAME}"

#set -x

## ref: https://stackoverflow.com/questions/3685548/java-keytool-easy-way-to-add-server-cert-from-url-port
##

if [ "$EUID" -ne 0 ]; then
    echo "Must run this script as root. run 'sudo $SCRIPT_NAME'"
    exit
fi

DEFAULT_HOST_LIST="
repo.dettonville.int:443
artifacts.dettonville.int:443
"

DEFAULT_KEYSTORE_PASS="changeit"

#DATE=`date +&%%m%d%H%M%S`
DATE=`date +%Y%m%d`

IFS=$'\n'
IDE_KEYSTORE_LIST=$(find ${HOME}/Library/Caches/ /Applications/IntelliJ* -type f -name cacerts -exec bash -c 'printf "%q\n" "$@"' sh {} +)

#IDE_KEYSTORE_LIST=$(find ${HOME}/Library/Caches/ -type f -name cacerts)
#IDE_KEYSTORE_LIST+=$(find /Applications/IntelliJ* -type f -name cacerts)
#IDE_KEYSTORE="${HOME}/Library/Caches/IdeaIC2017.3/tasks/cacerts"

KEYTOOL=keytool

TMP_OUT=/tmp/${SCRIPT_NAME}.output


### functions followed by main
usage() {
    echo "" 1>&2
    echo "Usage: sudo $0 [-p keystore_password] endpoint_list" 1>&2
    echo "" 1>&2
    echo "      optional:" 1>&2
    echo "          -p: keystore_password" 1>&2
    echo "" 1>&2
    echo "      endpoint_list:" 1>&2
    echo "          host:port" 1>&2
    echo "" 1>&2
    echo "      example usage:" 1>&2
    echo "          sudo $0 repo.dettonville.int:443" 1>&2
    echo "          sudo $0 -p changeit repo.dettonville.int:443" 1>&2
    echo "          sudo $0 'repo.dettonville.int:443 artifacts.dettonville.int:443'" 1>&2
    exit 1
}

function get_java_keystore() {
    ## default jdk location
    JAVA_HOME=$(/usr/libexec/java_home)
    CERT_DIR=${JAVA_HOME}/lib/security/cacerts
    if [ ! -d $CERT_DIR ]; then
        CERT_DIR=${JAVA_HOME}/jre/lib/security
    fi

#    echo "CERT_DIR=[$CERT_DIR]"

    echo $CERT_DIR/cacerts
}


function get_host_cert() {
    local host=$1
    local port=$2
    local cacerts_src=$3

    alias="${host}:${port}"

    echo "get_host_cert(): retrieving certs for [${alias}]"

    if [ -z "${host}" ]
        then
        echo "ERROR: Please specify the server name to import the certificate in from, eventually followed by the port number, if other than 443."
        exit 1
        fi

    set -e

    if [ -e "${cacerts_src}/${alias}.pem" ]
    then
        rm -f ${cacerts_src}/${alias}.pem
    fi

    if openssl s_client -connect ${host}:${port} 1>${cacerts_src}/${alias}.crt 2> $TMP_OUT </dev/null
    then
    :
    else
        cat ${cacerts_src}/${alias}.crt
        cat $TMP_OUT
        exit 1
    fi

    if sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' <${cacerts_src}/${alias}.crt > ${cacerts_src}/${alias}.pem
    then
    :
    else
        echo "ERROR: Unable to extract the certificate from ${cacerts_src}/${alias}.crt ($?)"
        cat $TMP_OUT
        exit 1
    fi

}


function import_jdk_cert() {

    local alias=$1
    local keystore=$2
    local keystore_password=$3
    local cacerts_src=$4

    echo "--- Adding certs to keystore at [${keystore}]"

    if $KEYTOOL -list -keystore ${keystore} -storepass ${keystore_password} -alias ${alias} >/dev/null
    then
        echo "Key of ${alias} already found, removing old one..."
        if $KEYTOOL -delete -alias ${alias} -keystore ${keystore} -storepass ${keystore_password} >$TMP_OUT
        then
        :
        else
            echo "ERROR: Unable to remove the existing certificate for ${alias} ($?)"
            cat $TMP_OUT
            exit 1
        fi
    fi

    {
        echo "importing pem"
        ${KEYTOOL} -import -trustcacerts -noprompt -keystore ${keystore} -storepass ${keystore_password} -alias ${alias} -file ${cacerts_src}/${alias}.pem >$TMP_OUT
    } || {  # catch
        echo "*** failed to import pem - so lets try to import the crt instead..."
        ${KEYTOOL} -import -trustcacerts -noprompt -keystore ${keystore} -storepass ${keystore_password} -alias ${alias} -file ${cacerts_src}/${alias}.pem >$TMP_OUT && \
        ${KEYTOOL} -import -trustcacerts -noprompt -keystore ${keystore} -storepass ${keystore_password} -alias ${alias} -file ${cacerts_src}/${alias}.crt >$TMP_OUT
    }

    if [ $? ]
    then
    :
    else
        echo "ERROR: Unable to import the certificate for ${alias} ($?)"
        cat $TMP_OUT
        exit 1
    fi

}

function import_host_cert() {

    local keystore_password=$1
    local host=$2
    local port=$3

    alias="${host}:${port}"
    echo "import_host_cert(${alias}): starting..."

    cacerts_src=${home}/.cacerts/${alias}/${DATE}

    if [ ! -d ${cacerts_src} ]; then
        mkdir -p ${cacerts_src}
    fi

    echo "get default java jdk cacert location"
    jdk_keystore=$(get_java_keystore)

    if [ ! -e ${jdk_keystore} ]; then
        echo "jdk_keystore [${jdk_keystore}] not found!"
        exit 1
    else
        echo "jdk_keystore found at [${jdk_keystore}]"
    fi

    echo "Get host cert"
    get_host_cert ${host} ${port} ${cacerts_src}

    ### Now build list of cacert targets to update
    echo "updating JDK certs at [${jdk_keystore}]..."
    import_jdk_cert ${alias} ${jdk_keystore} ${keystore_password} ${cacerts_src}

    echo "updating IDE certs at [$IDE_KEYSTORE_LIST]..."
    for ide_cacert_loc in ${IDE_KEYSTORE_LIST}
    do
        import_jdk_cert ${alias} ${ide_cacert_loc} ${keystore_password} ${cacerts_src}
    done

    # FYI: the default keystore is located in ~/.keystore
    default_keystore="~/.keystore"
    if [ -f ${default_keystore} ]; then
        echo "updating default certs at [${default_keystore}]..."
        import_jdk_cert ${alias} ${default_keystore} ${keystore_password} ${cacerts_src}
    fi

    echo "Adding cert to the system keychain.."
#    sudo security add-trusted-cert -d -r trustRoot -k "/Library/Keychains/System.keychain" "/private/tmp/securly_SHA-256.crt"
    sudo security add-trusted-cert -d -r trustRoot -k "/Library/Keychains/System.keychain" ${cacerts_src}/${alias}.crt

    echo "import_host_cert(${alias}): finished"

}

function import_certs_from_hostlist() {
    local keystore_password=$1
    local host_list=$2

    for endpoint in ${host_list}; do
        echo "endpoint=${endpoint}"
        IFS=':' read -r -a array <<< "$endpoint"
        host=${array[0]}
        port=${array[1]}

        echo "import_certs_from_hostlist(): host=${host} port=${port}"

        import_host_cert ${keystore_password} ${host} ${port}
    done
}

keystore_password=${DEFAULT_KEYSTORE_PASS}

while getopts ":x" opt; do
    case "${opt}" in
        x)
            debug_container=1
            ;;
        p)
            keystore_password=${OPTARG}
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            usage
            ;;
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

host_list=${DEFAULT_HOST_LIST}
if [ $# == 1 ]; then
    host_list=$1
fi

import_certs_from_hostlist ${keystore_password} ${host_list}

echo "**** Finished ${SCRIPT_NAME} ${@} ****"
