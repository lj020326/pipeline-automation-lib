#!/usr/bin/env bash

CONFIRM=0
SCRIPT_NAME=$(basename $0)

## How to reset/restart/blast repo:
## Make the current commit the only (initial) commit in a Git repository?
## ref: https://stackoverflow.com/questions/9683279/make-the-current-commit-the-only-initial-commit-in-a-git-repository

GIT_REPO_REMOTE_PRIVATE="origin"
GIT_REPO_REMOTE_LOCAL="gitea"
GIT_REPO_REMOTE_PUBLIC="github"

GIT_REPO_BRANCH_PUBLIC="public"

GIT_REPO_URL_ORIGIN=ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git
GIT_REPO_URL_PUBLIC=git@github.com:lj020326/pipeline-automation-lib.git

usage() {
  retcode=${1:-1}
  echo "" 1>&2
  echo "Usage: ${SCRIPT_NAME} [options] target_branch" 1>&2
  echo "" 1>&2
  echo "     options:" 1>&2
  echo "       -y : provide answer yes to skip confirmation" 1>&2
  echo "       -h : help" 1>&2
  echo "     target_branch: name of the branch to target the reset" 1>&2
  echo "" 1>&2
  echo "  Examples:" 1>&2
  echo "     ${SCRIPT_NAME} -y ${GIT_REPO_BRANCH_PUBLIC}" 1>&2
  echo "     ${SCRIPT_NAME} master" 1>&2
  echo "" 1>&2
  exit ${retcode}
}

while getopts "yhf" opt; do
    case "${opt}" in
        y) CONFIRM=1 ;;
        h) usage 1 ;;
        \?) usage 2 ;;
        :)
            echo "Option -$OPTARG requires an argument." >&2
            usage 3
            ;;
        *)
            usage 4
            ;;
    esac
done
shift $((OPTIND-1))

if [ $# -lt 1 ]; then
    echo "required target_branch not specified" >&2
    usage 5
fi

#TARGET_BRANCH="main"
## https://stackoverflow.com/questions/1593051/how-to-programmatically-determine-the-current-checked-out-git-branch
CURRENT_GIT_BRANCH=$(git symbolic-ref HEAD 2>/dev/null)
TARGET_BRANCH=${1:-"${CURRENT_GIT_BRANCH}"}

if [ $CONFIRM -eq 0 ]; then
  ## https://www.shellhacks.com/yes-no-bash-script-prompt-confirmation/
  read -p "Are you sure you want to re-init branch ${TARGET_BRANCH}? " -n 1 -r
  echo    # (optional) move to a new line
  if [[ ! $REPLY =~ ^[Yy]$ ]]
  then
      exit 1
  fi
fi

## ref: https://intoli.com/blog/exit-on-errors-in-bash-scripts/
# exit when any command fails
set -e

# keep track of the last executed command
trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
# echo an error message before exiting
trap 'echo "\"${last_command}\" command filed with exit code $?."' EXIT

TIMESTAMP=$(date +%Y%m%d%H%M%S)

GIT_ARCHIVE_DIR="save"
GIT_ARCHIVE_NAME="${GIT_ARCHIVE_DIR}/.git.${TIMESTAMP}"

if [ -d ${GIT_ARCHIVE_NAME} ]; then
  echo "cannot save to ${GIT_ARCHIVE_NAME} - backup already exists..."
  exit 1
fi

echo "reinitializing git repo"
#mv .git save/
mv .git ${GIT_ARCHIVE_NAME}
git init
git add .
git commit -m 'initial commit'
git remote add "${GIT_REPO_REMOTE_PRIVATE}" ${GIT_REPO_URL_ORIGIN}
git remote add "${GIT_REPO_REMOTE_PUBLIC}" ${GIT_REPO_URL_PUBLIC}
git push -u --force "${GIT_REPO_REMOTE_PRIVATE}" "${TARGET_BRANCH}"
git push -u --force "${GIT_REPO_REMOTE_LOCAL}" "${TARGET_BRANCH}"

echo "re-initialize repos for the ${GIT_REPO_BRANCH_PUBLIC} branch"
git checkout -b "${GIT_REPO_BRANCH_PUBLIC}"
echo "add and commit"
git add .
git commit -m 'updates'
echo "push ${GIT_REPO_BRANCH_PUBLIC} branch to repos"
echo "push ${GIT_REPO_BRANCH_PUBLIC} branch to ${GIT_REPO_REMOTE_PUBLIC} repo"
#git push --set-upstream "${GIT_REPO_REMOTE_PUBLIC}" "${GIT_REPO_BRANCH_PUBLIC}"
git push -u --force "${GIT_REPO_REMOTE_PUBLIC}" "${GIT_REPO_BRANCH_PUBLIC}"
echo "push ${GIT_REPO_BRANCH_PUBLIC} branch to ${GIT_REPO_REMOTE_PRIVATE} repo"
git push -u --force "${GIT_REPO_REMOTE_PRIVATE}" "${GIT_REPO_BRANCH_PUBLIC}"
git checkout "${TARGET_BRANCH}"
git add .
git commit -m 'updates'
git push "${GIT_REPO_REMOTE_PRIVATE}" "${TARGET_BRANCH}"
