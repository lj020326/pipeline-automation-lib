#!/usr/bin/env bash

#
# Script which, when run from Jenkins, will hunt and kill any other browserstacklocal
# processes running on the same machine from a different build.
#
# This is done by looking for all of the browserstacklocal processes owned by the jenkins user,
# checking their BUILD_ID environment variable, and killing any whose BUILD_ID
# does not match the current $BUILD_ID environment.  This assumes that there are
# no concurrent builds configured in Jenkins, for obvious reasons.
#
# Set $DRY_RUN before running this script to just see what would be killed.

set -e

JENKINS_USER=${1:-jenkins}
PROJECT=${2:-DCAPI}
PROCESS_NAME=${3:-browserstacklocal}
CURRENT_BUILD=$BUILD_ID
#DRY_RUN=1

if [ "$USER" != "$JENKINS_USER" ]; then
  echo Not running as user \'$JENKINS_USER\'
  exit 1
fi

if [ -z "$CURRENT_BUILD" ]; then
  echo Not running in the context of a Jenkins build
  exit 1
fi

echo "BUILD_ID=${BUILD_ID}"

if [[ "$OSTYPE" == "linux-gnu" ]]; then
   PS_FORMAT_OPTIONS="cmd"
elif [[ "$OSTYPE" == "darwin"* ]]; then
   PS_FORMAT_OPTIONS="command"
else
   PS_FORMAT_OPTIONS="cmd"
fi

## ref: https://askubuntu.com/questions/111422/how-to-find-zombie-process
#echo "RUNNING ZOMBIE PROCESSES with name=${PROCESS_NAME}:"
echo "ZOMBIE PROCESSES:"
echo "#####"
#ps -ef | grep -v grep | grep ${JENKINS_USER} | grep -i ${PROCESS_NAME}
ps axo pid=,stat=,ppid,user=,etime=,${PS_FORMAT_OPTIONS}= | awk '$2~/^Z/ { print }'
echo "#####"

JENKINS_ZOMBIE_PIDS=$(ps axo pid=,stat=,ppid=,user=,etime,${PS_FORMAT_OPTIONS}= | awk '$2~/^Z/ { print }' | grep ${JENKINS_USER} | grep -i ${PROCESS_NAME} | awk '{ print $1 }')

echo "OLD (>24hrs) PROCESSES:"
echo "#####"
#ps -ef | grep -v grep | grep ${JENKINS_USER} | grep -i ${PROCESS_NAME}
ps axo pid=,stat=,ppid=,user=,etime,lstart=,${PS_FORMAT_OPTIONS}= | grep ${JENKINS_USER} | grep -i ${PROCESS_NAME} | awk '{ if (substr($5,1,index($5,"-")-1)-0>1) print }'
echo "#####"

JENKINS_OLD_PIDS=$(ps axo pid=,stat=,ppid=,user=,etime,lstart=,${PS_FORMAT_OPTIONS}= | grep ${JENKINS_USER} | grep -i ${PROCESS_NAME} | awk '{ if (substr($5,1,index($5,"-")-1)-0>1) print $1 }')

#JENKINS_ORPHANED_PIDS=$JENKINS_ZOMBIE_PIDS
#JENKINS_ORPHANED_PIDS+=$JENKINS_OLD_PIDS

#JENKINS_ORPHANED_PIDS=("${JENKINS_ZOMBIE_PIDS[@]}" "${JENKINS_OLD_PIDS[@]}" )
JENKINS_ORPHANED_PIDS=( ${JENKINS_ZOMBIE_PIDS[*]} ${JENKINS_OLD_PIDS[*]} )

JENKINS_ORPHANED_PIDS=($(echo "${JENKINS_ORPHANED_PIDS[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))

echo "STOPPING JENKINS_ORPHANED_PIDS=[${JENKINS_ORPHANED_PIDS[*]}] ..."

for pid in "${JENKINS_ORPHANED_PIDS[@]}"; do
    echo "inspecting /proc/$pid"
    cmdline=$(ps h -p $pid -o ${PS_FORMAT_OPTIONS} || echo '[pid exited]')
    build_env=$(cat /proc/$pid/environ 2>/dev/null | tr '\0' '\n' | egrep '^BUILD_ID=' || :)
    #  project_env=$(cat /proc/$pid/environ 2>/dev/null | tr '\0' '\n' | egrep '^PROJECT=' || :)
    if [ -z ${build_env+x} ]; then
        # Some Jenkins processes, like the slave itself, don't have a BUILD_ID
        # set. We shouldn't kill those.
        echo "Process $pid ($cmdline) not associated with any build. Skipping..."
        continue
    fi
    build_id=$(echo $build_env | cut -d= -f2)
    #  project_id=$(echo $project_env | cut -d= -f2)
    #  if [ "$project_id" == "$PROJECT" ] || [ "$project_env" == "" ]; then
    if [ "$build_id" != "$CURRENT_BUILD" ]; then
        echo "Killing zombie process $pid (from build $build_id)"
        ps -fww -p $pid || :
        echo "kill -9 $pid"
        if [ -z ${DRY_RUN+x} ]; then
            kill -9 $pid || :
        else
            echo "dry run - kill skipped"
        fi
        echo ----------
    else
        echo "pid $pid ($cmdline) is from the current build. Not killing"
    fi
    #  fi
done
