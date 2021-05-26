#!/usr/bin/env bash

#set -x

##
## https://github.com/greymd/confl
## https://stackoverflow.com/questions/17029902/using-curl-post-with-variables-defined-in-bash-script-functions
## ref: https://developer.atlassian.com/server/confluence/confluence-rest-api-examples/
## ref: https://serverfault.com/questions/861821/how-to-get-data-from-jenkins-to-confluence-cloud
##
## https://gist.github.com/dodok1/4352500
## https://avleonov.com/2018/01/18/confluence-rest-api-for-reading-and-updating-wiki-pages/
##
## https://community.atlassian.com/t5/Confluence-questions/Access-page-content-via-URL/qaq-p/163060
## https://www.openmakesoftware.com/restful-json-api-calls-jenkins-pipeline/
## https://jenkins.io/blog/2016/07/18/pipeline-notifications/
## https://gist.github.com/xseignard/1422531
## https://stackoverflow.com/questions/38978295/using-pipeline-groovy-how-can-i-extract-test-results-from-jenkins-for-my-curr
##

scriptName="${0%.*}"
logFile="${scriptName}.log"

CONFL_USER="${CONFL_USER:-your_name_here}"
CONFL_PASS="${CONFL_PASS:-your_password_here}"
#CONFL_SPACE_KEY="${CONFL_SPACE_KEY:-MAPI}"
CONFL_SPACE_KEY=${CONFL_SPACE_KEY:-"~${USER}"}
CONFL_API_END="${CONFL_API_END:-https://fusion.dettonville.int/confluence/rest/api/content}"

AUTH_HEADER="Authorization: Basic $(printf "%s" "$CONFL_USER:$CONFL_PASS" | base64)"

if [ -f $logFile ]; then
    rm $logFile
fi

writeToLog() {
    echo -e "${1}" | tee -a "${logFile}"
#    echo -e "${1}" >> "${logFile}"
}

usage () {
  cat <<__EOF__
Usage:
  ${0} [COMMANDS] [argument ...]

COMMANDS:
  help                                     -- Show this help.
  info <PAGE_ID>                           -- Print information the page having given PAGE_ID.
  cat <PAGE_ID> [OPTIONS]                  -- Print html of the page having given PAGE_ID.
  cat-storage <PAGE_ID>                    -- Print storage content of the page having given PAGE_ID.
  cat-storage-html <PAGE_ID>               -- Print storage content html of the page having given PAGE_ID.
  ls <PAGE_ID>                             -- Show child pages under the page.
  rm  <PAGE_ID>                            -- Remove the page.
  mv  <PAGE_ID> <DEST PAGE_ID>             -- Move the page.
  create <PARENT PAGE_ID> <TITLE>          -- Create new page, standard input will be page body.
  create-ffile <TITLE> <FILE>              -- Create new page, loading page body from file.
  update <PARENT PAGE_ID>                  -- Update the page, standard input will be page body.
  update-ffile <TITLE> <FILE>              -- Update page, loading page body from file.

PAGE_ID:
  Unique numeric ID or title of the page.

OPTIONS:
  -f  -- pretty print Html
__EOF__
}

# 1. Remove all the line breaks
# 2. Replace backslash '\' to '\\'
# 3. Escape double quotation \"
_confl_encode4confl () {
  cat \
    | sed  -e 's/\\/\\\\/g' \
    | sed -e 's/"/\\"/g' \
    | tr -d '\r\n'
}

_confl_check_id () {
    local _id="$1"
    if [[ "$_id" =~ ^[0-9]+$ ]]; then
        echo "$_id"
    else
        _confl-title2id "$_id"
    fi
}

_confl_urlenc () {
  tr -d '\r\n' \
    | od -An -tx1 \
    | awk 'NF{OFS="%";$1=$1;print "%"$0}' \
    | tr '[:lower:]' '[:upper:]' \
    | tr -d '\r\n'
}

_confl-title2id () {
    local _query="$1"
    _query="$(echo "$_query" | _confl_urlenc)"
    local _ids="$(curl -so- -H "$AUTH_HEADER" -X GET -L "$CONFL_API_END/search?cql=space=$CONFL_SPACE_KEY%20and%20title=%22$_query%22%20and%20type=page" | jq -r '.results[] | .id')"
    local _num="$(echo "$_ids" | grep -c .)"
    if [ $_num -eq 1 ]; then
        echo "$_ids"
    elif [ $_num -eq 0 ];then
        echo "error: Page '$_query' not found." >&2
        exit 1
    else
        echo "error: Multiple candidates[ $_ids ]." >&2
        exit 1
    fi
}

confl-mv () {
    local _page_id="$(_confl_check_id "$1")"; shift
    local _page_id_dst="$(_confl_check_id "$1")"; shift
    local _arr=()
    while read e;
    do
    _arr+=("$e")
    done < <(confl-info "$_page_id" | jq -r '.title,.version.number')
    local _title="${_arr[0]}"
    local _version_num="${_arr[1]}"
    _version_num=$(($_version_num + 1))

    curl  -so- -H "$AUTH_HEADER" -X PUT \
        -H "Content-Type: application/json" \
        -d '{"id":'"$_page_id"',"type":"page",
             "title":"'"$_title"'",
             "ancestors":[{"id":'"$_page_id_dst"'}],
             "space":{"key":"'"$CONFL_SPACE_KEY"'"},
             "version":{"number":'"$_version_num"'}}' \
        -L "$CONFL_API_END/$_page_id" \
        | jq .
}

confl-info () {
  local _page_id="$(_confl_check_id "$1")"; shift
  curl -so- -H "$AUTH_HEADER" -X GET -L "$CONFL_API_END/$_page_id" \
    | jq .
}

confl-rm () {
  local _page_id="$(_confl_check_id "$1")"; shift
  curl -so- -H "$AUTH_HEADER" -X DELETE -L "$CONFL_API_END/$_page_id" \
    | jq .
}

confl-ls () {
  local _page_id="$(_confl_check_id "$1")"; shift
  curl -so- -H "$AUTH_HEADER" -X GET -L "$CONFL_API_END/$_page_id/child/page" \
    | jq -r '.results[]| .id + "\t" + .title'
}

confl-cat () {
  local _page_id="$(_confl_check_id "$1")"; shift
  local _pp="${1-}"
  local _parser=cat
  if [[ "$_pp" =~ f ]]; then
#    _parser='xmllint --format --encode UTF-8 -'
    _parser='xmllint --format --html -'
  fi
  curl -so- -H "$AUTH_HEADER" -X GET -L "$CONFL_API_END/$_page_id?expand=body.view" \
    | jq -r '.body.view.value' \
    | $_parser
}

## ref: https://fusion.dettonville.int/confluence/display/~ljohnson/Copy+of+Gitflow+Workflow
confl-cat-storage () {
    local _page_id="$(_confl_check_id "$1")"; shift

#    writeToLog "fetching body.storage.value of page with PAGE_ID=${_page_id}"
    curl -so- -H "$AUTH_HEADER" -X GET -L "$CONFL_API_END/$_page_id?expand=body.storage" | jq -r '.body.storage.value'

#    writeToLog "PAGE_BODY_CONTENT=${PAGE_BODY_CONTENT}"
}

## ref: https://fusion.dettonville.int/confluence/display/~ljohnson/Copy+of+Gitflow+Workflow
confl-cat-storage-html () {
    local _page_id="$(_confl_check_id "$1")"; shift

    writeToLog "fetching body.storage.value of page with PAGE_ID=${_page_id}"
    curl -so- -H "$AUTH_HEADER" -X GET -L "$CONFL_API_END/$_page_id?expand=body.storage" | jq -r '.body.storage.value' | xmllint --format --html - 2>/dev/null

#    writeToLog "PAGE_BODY_CONTENT=${PAGE_BODY_CONTENT}"

}

confl-create () {
  local _page_id="$(_confl_check_id "$1")"; shift
  local _title="$1"; shift
  curl -so- -H "$AUTH_HEADER" \
    -X POST \
    -H 'Content-Type: application/json' \
    -d '{"type":"page",
        "title":"'"$_title"'",
        "ancestors":[{"id":'"$_page_id"'}],
        "space":{"key":"'"$CONFL_SPACE_KEY"'"},
        "body":{"storage":{"value":"'"$(cat | _confl_encode4confl)"'",
            "representation":"storage"}
         }}' \
        "$CONFL_API_END" \
  | jq .
}

generate_update_page_data()
{
    local _page_id="$(_confl_check_id "$1")"
    local _title="$2"
    local _infile="$3"

    local _version_num=$(curl -so- -H "$AUTH_HEADER" -X GET -L "${CONFL_API_END}/${_page_id}?expand=version" | jq .version.number)
    _version_num=$(($_version_num + 1))

    local _content_raw="$(<${_infile})"
    local _content=$(echo "${_content_raw}" | _confl_encode4confl)

    cat <<EOF
{
    "id":"${_page_id}",
    "type":"page",
    "title":"${_title}",
    "space":{"key":"${CONFL_SPACE_KEY}"},
    "body":{
        "storage":{
            "value":"${_content}",
            "representation":"storage"
            }
    },
    "version": {
        "number": ${_version_num},
        "minorEdit": true
    }
}
EOF
}

generate_create_page_data()
{
    local _title="$1"
    local _infile="$2"

    local _content_raw="$(<${_infile})"
    local _content=$(echo "${_content_raw}" | _confl_encode4confl)

    cat <<EOF
{
    "type":"page",
    "title":"${_title}",
    "space":{"key":"${CONFL_SPACE_KEY}"},
    "body":{
        "storage":{
            "value":"${_content}",
            "representation":"storage"
            }
    }
}
EOF
}

generate_create_child_page_data()
{
    local _page_id="$(_confl_check_id "$1")"
    local _title="$2"
    local _infile="$3"

    local _content_raw="$(<${_infile})"
    local _content=$(echo "${_content_raw}" | _confl_encode4confl)

    cat <<EOF
{
    "type":"page",
    "title":"${_title}",
    "ancestors":[{"id":'"$_page_id"'}],
    "space":{"key":"${CONFL_SPACE_KEY}"},
    "body":{
        "storage":{
            "value":"${_content}",
            "representation":"storage"
            }
    }
}
EOF
}


confl-create-page-from-file () {
    local _title="$1"
    local _infile="$2"

    curl  -so- -H "$AUTH_HEADER" \
        -X POST \
        -H "Content-Type: application/json" \
        -d "$(generate_create_page_data "${_title}" "${_infile}")" \
            "$CONFL_API_END" \
        | jq .
}

confl-update () {
    local _page_id="$(_confl_check_id "$1")"; shift
    local _arr=()

    while read e;
    do
    _arr+=("$e")
    done < <(confl-info "$_page_id" | jq -r '.title,.version.number')
    local _title="${_arr[0]}"
    local _version_num="${_arr[1]}"
    _version_num=$(($_version_num + 1))

    curl -so- -H "$AUTH_HEADER" \
        -X PUT \
        -H 'Content-Type: application/json' \
        -d '{"type":"page","title":"'"$_title"'","id":'"$_page_id"',"space":{"key":"'"$CONFL_SPACE_KEY"'"},"body":{"storage":{"value":"'"$(cat | _confl_encode4confl)"'","representation":"storage"}},"version":{"number":'"$_version_num"'}}}' "$CONFL_API_END/$_page_id" \
        | jq .
}


confl-update-from-file () {
    local _page_id="$(_confl_check_id "$1")"
    local _infile="$2"

    curl -so- -H "$AUTH_HEADER" \
        -X PUT \
        -H 'Content-Type: application/json' \
        -d "$(generate_update_page_data "${_page_id}" "${_infile}")" \
        "$CONFL_API_END/$_page_id" \
        | jq .
}


CMD="${1-}"
shift
case "$CMD" in
  cat)
    confl-cat "$@"
    ;;
  cat-storage)
    confl-cat-storage "$@"
    ;;
  cat-storage-html)
    confl-cat-storage-html "$@"
    ;;
  ls)
    confl-ls "$@"
    ;;
  rm)
    confl-rm "$@"
    ;;
  mv)
    confl-mv "$@"
    ;;
  info)
    confl-info "$@"
    ;;
  create)
    confl-create "$@"
    ;;
  create-ffile)
    confl-create-page-from-file "$@"
    ;;
  update)
    confl-update "$@"
    ;;
  update-ffile)
    confl-update-from-file "$@"
    ;;
  --help|help)
    usage
    exit 0
    ;;
  *)
    usage
    exit 1
esac

