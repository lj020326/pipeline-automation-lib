#!/usr/bin/env bash
##!/bin/sh

## ref: http://sgeos.github.io/phoenix/elixir/sh/2016/03/19/a-shell-script-for-working-with-phoenix-json-apis.html

#chmod +x memo_api.sh
#./memo_api.sh -X POST -t "Memo Title" -b "Memo body here."
#./memo_api.sh -X GET -i 1
#./memo_api.sh -X POST -t "Another Memo" -b "This memo's body."
#./memo_api.sh -X PATCH -t "Patched title." -i 2
#./memo_api.sh -X PATCH -b "Patched body." -i 1
#./memo_api.sh -X PUT -t "New Title" -b "New body." -i 2
#./memo_api.sh -X GET
#./memo_api.sh -X DELETE -i 1
#
#reset() {
#  HOST=http://localhost:4000
#  SCOPE=api
#  ROUTE=memos
#  METHOD="GET"
#  HEADERS="Content-Type: application/json"
#  ID=""
#  TITLE=""
#  BODY=""
#}

usage() {
  reset
  echo "Usage:  ${0} [options]"
  echo "Options:"
  echo "  -o HOST : set URL host, defaults to \"${HOST}\""
  echo "  -s SCOPE : set URL scope, defaults to \"${SCOPE}\""
  echo "  -r ROUTE : set URL route, defaults to \"${ROUTE}\""
  echo "  -X METHOD : set HTTP method, defaults to \"${METHOD}\""
  echo "  -H HEADERS : set HTTP headers, defaults to \"${HEADERS}\""
  echo "  -i ID : set memo id, defaults to \"${ID}\""
  echo "  -t TITLE : set memo title, defaults to \"${TITLE}\""
  echo "  -b BODY : set memo body, defaults to \"${BODY}\""
  echo "  -h : display this help"
  echo "Examples:"
  echo "  ${0} -X GET"
  echo "  ${0} -X GET -i 7"
  echo "  ${0} -X POST -t \"Memo Title\" -b \"Memo body here.\""
  echo "  ${0} -X PATCH -t \"Patched title.\" -i 7"
  echo "  ${0} -X PATCH -b \"Patched body.\" -i 7"
  echo "  ${0} -X PUT -t \"New Title\" -b \"New body.\" -i 7"
  echo "  ${0} -X DELETE -i 7"
  exit ${1}
}

reset
while getopts "o:s:r:X:H:i:t:b:h" opt
do
  case "${opt}" in
    o) HOST="${OPTARG}" ;;
    s) SCOPE="${OPTARG}" ;;
    r) ROUTE="${OPTARG}" ;;
    X) METHOD="${OPTARG}" ;;
    H) HEADERS="${OPTARG}" ;;
    i) ID="${OPTARG}" ;;
    t) TITLE="${OPTARG}" ;;
    b) BODY="${OPTARG}" ;;
    h) usage 1 ;;
    \?) usage 2 ;;
  esac
done
shift $(expr ${OPTIND} - 1)

case "${METHOD}" in
  GET)
    curl -H "${HEADERS}" -X ${METHOD} "${HOST}/${SCOPE}/${ROUTE}${ID:+"/${ID}"}"
    ;;
  POST)
    PAYLOAD='{"memo": {"title": "'"${TITLE:-(no title)}"'", "body": "'"${BODY:-(no body)}"'"}}'
    curl -H "${HEADERS}" -X ${METHOD} -d "${PAYLOAD}" "${HOST}/${SCOPE}/${ROUTE}"
    ;;
  PUT)
    PAYLOAD='{"memo": {"title": "'"${TITLE:-(no title)}"'", "body": "'"${BODY:-(no body)}"'"}}'
    curl -H "${HEADERS}" -X ${METHOD} -d "${PAYLOAD}" "${HOST}/${SCOPE}/${ROUTE}/${ID:?'No ID specified.'}"
    ;;
  PATCH)
    # if defined replace individual fields with
    # JSON fragments followed by a comma and space
    TITLE=${TITLE:+"\"title\": \"${TITLE}\", "}
    BODY=${BODY:+"\"body\": \"${BODY}\", "}
    # strip trailing comma and space
    PAYLOAD="$(echo "${TITLE}${BODY}" | sed 's/, $//g')"
    # complete JSON payload
    PAYLOAD="{\"memo\": {${PAYLOAD}}}"
    curl -H "${HEADERS}" -X ${METHOD} -d "${PAYLOAD}" "${HOST}/${SCOPE}/${ROUTE}/${ID:?'No ID specified.'}"
    ;;
  DELETE)
    curl -H "${HEADERS}" -X ${METHOD} "${HOST}/${SCOPE}/${ROUTE}/${ID:?'No ID specified.'}"
    ;;
  *)
    usage 2
    ;;
esac
echo ""
