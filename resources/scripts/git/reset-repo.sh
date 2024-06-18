#!/bin/sh

## How to reset/restart/blast repo:
## Make the current commit the only (initial) commit in a Git repository?
## ref: https://stackoverflow.com/questions/9683279/make-the-current-commit-the-only-initial-commit-in-a-git-repository

GIT_ORIGIN_REPO=ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git
GIT_PUBLIC_REPO=git@github.com:lj020326/pipeline-automation-lib.git

TIMESTAMP=$(date +%Y%m%d%H%M%S)

gitArchiveDir="save"
gitArchiveName="${gitArchiveDir}/.git.${TIMESTAMP}"

if [ -d ${gitArchiveName} ]; then
  echo "cannot save to ${gitArchiveName} - backup already exists..."
  exit 1
fi

echo "reinitializing git repo"
#mv .git save/
mv .git ${gitArchiveName}
git init
git add .
git commit -m 'initial commit'
git remote add origin ${GIT_ORIGIN_REPO}
git remote add github ${GIT_PUBLIC_REPO}
git push -u --force origin master
git push -u --force gitea master

echo "setup public repo"
git checkout -b public
echo "add and commit"
git add .
git commit -m 'updates'
echo "push public branch to github repo"
#git push --set-upstream github public
echo "push public branch to repos"
git push -u --force github public
git push -u --force origin public
git checkout master
git add .
git commit -m 'updates'
git push origin master
