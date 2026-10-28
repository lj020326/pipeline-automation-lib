#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.Logger
import groovy.transform.Field

@Field Logger log = new Logger(this)

/**
 * Reusable step to stage, commit, rebase, push changes to a dedicated branch,
 * and open a Pull Request against Gitea REST API.
 *
 * Usage:
 *   createGiteaPullRequest(
 *       prBranchName: "wiki-update-build-${env.BUILD_NUMBER}",
 *       baseBranch: env.CHANGE_TARGET ?: 'main',
 *       commitMessage: "chore(wiki): auto-update from LLM pipeline [skip ci] ***NO_CI***",
 *       prTitle: "Automated Wiki Update (#${env.BUILD_NUMBER}) [skip ci] ***NO_CI***",
 *       prBody: "Automated pipeline run generated updated wiki files.",
 *       gitSSHCredentialsId: config.gitSSHCredentialsId,
 *       gitCredentialsId: config.gitCredentialsId,
 *       giteaApiRepoPullsUrl: config.giteaApiRepoPullsUrl,
 *       giteaHost: "gitea.admin.dettonville.int",
 *       giteaPort: "2222",
 *       gitUserName: "Jenkins Wiki Bot",
 *       gitUserEmail: "jenkins@dettonville.com"
 *   )
 */
def call(Map params = [:]) {
    String prBranchName         = params.get('prBranchName', "pr-update-build-${env.BUILD_NUMBER}")
    String baseBranch           = params.get('baseBranch', env.CHANGE_TARGET ?: 'main')
    String commitMessage        = params.get('commitMessage', "chore(bot): automated update [skip ci] [ci skip] ***NO_CI***")
    String prTitle              = params.get('prTitle', "Automated Update (#${env.BUILD_NUMBER}) [skip ci] [ci skip] ***NO_CI***")
    String prBody               = params.get('prBody', "Automated pipeline run generated updated files for review against ${baseBranch}.")
    String gitSSHCredentialsId  = params.get('gitSSHCredentialsId', 'gitea-ssh-jenkins')
    String gitCredentialsId     = params.get('gitCredentialsId', 'infra-jenkins-git-user')
    String giteaApiRepoPullsUrl = params.get('giteaApiRepoPullsUrl', '')
    String giteaHost            = params.get('giteaHost', 'gitea.admin.dettonville.int')
    String giteaPort            = params.get('giteaPort', '2222')
    String gitUserName          = params.get('gitUserName', 'Jenkins Bot')
    String gitUserEmail         = params.get('gitUserEmail', 'jenkins@dettonville.com')

    log.info("Preparing PR branch '${prBranchName}' targeting '${baseBranch}'...")

    sshagent([gitSSHCredentialsId]) {
        // 1. Configure SSH and Git user identity
        sh """
            mkdir -p ~/.ssh && chmod 700 ~/.ssh
            ssh-keyscan -p ${giteaPort} ${giteaHost} >> ~/.ssh/known_hosts 2>/dev/null || true

            git config user.name "${gitUserName}"
            git config user.email "${gitUserEmail}"

            git checkout -B ${prBranchName}
            git add . || true
        """

        // 2. Commit and rebase if changes exist
        boolean hasChanges = sh(script: "git diff --cached --quiet", returnStatus: true) != 0

        if (!hasChanges) {
            log.info("ℹ️ No local changes detected to commit.")
            return false
        }

        sh """
            git commit -m "${commitMessage}"
            git fetch origin ${baseBranch}
            git rebase -X theirs origin/${baseBranch}
            git push origin ${prBranchName} --force
        """

        // 3. Create Gitea Pull Request if branch was successfully pushed
        String branchExists = sh(script: "git ls-remote --heads origin ${prBranchName}", returnStdout: true).trim()

        if (branchExists && giteaApiRepoPullsUrl) {
            String payload = JsonUtils.printToJsonString([
                head: prBranchName,
                base: baseBranch,
                title: prTitle,
                body: prBody
            ])

            try {
                httpRequest httpMode: 'POST',
                            contentType: 'APPLICATION_JSON',
                            requestBody: payload,
                            url: giteaApiRepoPullsUrl,
                            authentication: gitCredentialsId,
                            quiet: true,
                            responseHandle: 'NONE'

                log.info("🚀 Pull Request created successfully via Gitea API for branch '${prBranchName}' targeting '${baseBranch}'.")
                return true
            } catch (Exception apiEx) {
                log.error("Gitea PR creation API call failed: ${apiEx.getMessage()}")
                throw apiEx
            }
        } else {
            log.info("ℹ️ No PR branch pushed or missing giteaApiRepoPullsUrl endpoint.")
            return false
        }
    }
}
