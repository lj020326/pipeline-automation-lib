#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map args=[:], String gitRemoteRepoType) {

    log.info("${gitRemoteRepoType} => args=${JsonUtils.printToJsonString(args)}")

    // Define valid states for Gitea Checks API
    // Status can be IN_PROGRESS or COMPLETED
    Set<String> VALID_GITEA_CHECK_STATUSES = ['QUEUED', 'IN_PROGRESS', 'COMPLETED']
    // Conclusion can be SUCCESS, FAILURE, NEUTRAL, SKIPPED, UNSTABLE, ABORTED
//     Set<String> VALID_GITEA_CHECK_CONCLUSIONS = ['SUCCESS', 'FAILURE', 'NEUTRAL', 'SKIPPED', 'UNSTABLE', 'ABORTED']
    Set<String> VALID_GITEA_CHECK_CONCLUSIONS = ['SUCCESS', 'FAILURE', 'NEUTRAL', 'CANCELED', 'SKIPPED', 'TIME_OUT', 'ACTION_REQUIRED', 'NONE']

    // Define valid states for Bitbucket Status API
    // BuildState can be INPROGRESS, SUCCESSFUL, FAILED
    Set<String> VALID_BITBUCKET_BUILD_STATES = ['INPROGRESS', 'SUCCESSFUL', 'FAILED']

    Map notifyArgs = [:]
    if (gitRemoteRepoType == "bitbucket") {
        notifyArgs['buildKey'] = args.gitRemoteBuildKey
        if (args?.gitRemoteBuildName) {
            notifyArgs['buildName'] = args.gitRemoteBuildName
        }
        if (args?.gitRemoteBuildStatus) {
            // Validate and map Bitbucket build states
            String bitbucketStatus = args.gitRemoteBuildStatus
            if (VALID_BITBUCKET_BUILD_STATES.contains(bitbucketStatus)) {
                notifyArgs['buildState'] = bitbucketStatus
            } else {
                log.warn("Invalid Bitbucket build state '${bitbucketStatus}' provided. Must be one of: ${VALID_BITBUCKET_BUILD_STATES.join(', ')}. Setting to null.")
                // Optionally, set a default or leave null, depending on desired behavior
                notifyArgs['buildState'] = null
            }
        }
        if (args?.gitRemoteBuildSummary) {
            notifyArgs['repoSlug'] = args.gitRemoteBuildSummary
        }
        if (args?.gitCommitId) {
            notifyArgs['commitId'] = args.gitCommitId
        }
        bitbucketStatusNotify(notifyArgs)
    } else if (gitRemoteRepoType == "gitea") {
        String giteaStatus = 'COMPLETED'
        String giteaConclusion = 'NEUTRAL'

        if (args?.gitRemoteBuildStatus) {
            String buildStatus = args.gitRemoteBuildStatus.toUpperCase()

            switch(buildStatus) {
                case 'INPROGRESS':
                case 'IN_PROGRESS':
                case 'QUEUED':
                    giteaStatus = 'IN_PROGRESS'
                    giteaConclusion = null
                    break
                case 'COMPLETED':
                case 'SUCCESS':
                case 'SUCCESSFUL':
                    giteaStatus = 'COMPLETED'
                    giteaConclusion = 'SUCCESS'
                    break
                case 'FAILED':
                case 'FAILURE':
                    giteaStatus = 'COMPLETED'
                    giteaConclusion = 'FAILURE'
                    break
                case 'ABORTED':
                    giteaStatus = 'COMPLETED'
                    giteaConclusion = 'ABORTED'
                    break
                case 'UNSTABLE':
                    giteaStatus = 'COMPLETED'
                    giteaConclusion = 'UNSTABLE'
                    break
                case 'SKIPPED':
                    giteaStatus = 'COMPLETED'
                    giteaConclusion = 'SKIPPED'
                    break
                case 'NEUTRAL':
                    giteaStatus = 'COMPLETED'
                    giteaConclusion = 'NEUTRAL'
                    break
                default:
                    log.warn("notifyGitRemoteRepo.call(): Unexpected Git remote build status '${buildStatus}'. Defaulting Gitea Check status to COMPLETED and conclusion to NEUTRAL.")
                    break
            }
        }

        notifyArgs['status'] = giteaStatus
        if (giteaConclusion) {
            notifyArgs['conclusion'] = giteaConclusion
        }

        if (args?.gitRemoteBuildConclusion && VALID_GITEA_CHECK_CONCLUSIONS.contains(args.gitRemoteBuildConclusion)) {
            notifyArgs['conclusion'] = args.gitRemoteBuildConclusion
        }

        if (args?.gitRemoteBuildSummary) {
            notifyArgs['summary'] = args.gitRemoteBuildSummary
        }

        notifyArgs['name'] = args?.gitRemoteBuildName ?: "Jenkins Job Run"

        try {
            log.info("Attempting to publish via Jenkins Checks API...")
            publishChecks(notifyArgs)
        } catch (Exception ex) {
            log.warn("publishChecks failed to find a valid publisher: ${ex.getMessage()}. Falling back to manual REST API notification...")

            // 1. Get the commit hash dynamically
            String commitId = args?.gitCommitId ?: env.GIT_COMMIT

            // 2. Get the Git URL dynamically from environment variables
            String gitUrl = env.GIT_URL ?: ""

            if (!gitUrl && env.getEnvironment().containsKey('CHANGE_URL')) {
                // Multibranch pipeline alternative if generic GIT_URL isn't populated
                gitUrl = env.GIT_URL
            }

            if (!commitId || !gitUrl) {
                log.error("Cannot fall back to Gitea Status API: Missing context. gitCommitId: ${commitId}, gitUrl: ${gitUrl}")
                return
            }

            // 3. Dynamically derive Gitea API endpoint from SCM Git URL
            // Handles transformations like: ssh://git@gitea.admin.dettonville.int:2222/infra/ansible-datacenter.git
            // -> http://gitea.admin.dettonville.int:3000/api/v1/repos/infra/ansible-datacenter/statuses/...
            String giteaApiUrl = deriveGiteaStatusApiUrl(gitUrl, commitId)
            log.info("Derived Gitea API URL: ${giteaApiUrl}")

            String apiState = 'pending'
            if (giteaStatus == 'COMPLETED') {
                apiState = (giteaConclusion == 'SUCCESS') ? 'success' : 'failure'
            }

            withCredentials([usernamePassword(credentialsId: 'infra-jenkins-git-user', passwordVariable: 'GITEA_TOKEN', usernameVariable: 'GITEA_USER')]) {
                try {
                    def payload = JsonUtils.printToJsonString([
                        state: apiState,
                        target_url: env.BUILD_URL ?: "",
                        description: args?.gitRemoteBuildSummary ?: "Jenkins Build Status",
                        context: notifyArgs['name']
                    ])

                    httpRequest httpMode: 'POST',
                                contentType: 'APPLICATION_JSON',
                                requestBody: payload,
                                customHeaders: [[name: 'Authorization', value: "token ${GITEA_TOKEN}"]],
                                url: giteaApiUrl,
                                quiet: true
                    log.info("Successfully updated Gitea commit status via dynamic REST fallback.")
                } catch (Exception apiEx) {
                    log.error("Gitea direct REST API notification fallback failed: ${apiEx.getMessage()}")
                }
            }
        }
    }
}

/**
 * Parses out SSH or HTTP Git remote URLs to build the exact Gitea REST API endpoint.
 */
String deriveGiteaStatusApiUrl(String gitUrl, String commitId) {
    // Standardize URL by clearing trailing .git suffix
    String cleanUrl = gitUrl.trim()
    if (cleanUrl.endsWith('.git')) {
        cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 4)
    }

    String host = "gitea.admin.dettonville.int:3000" // Fallback default domain if extraction fails
    String repoPath = ""

    if (cleanUrl.startsWith("ssh://") || cleanUrl.contains("@")) {
        // Example: ssh://git@gitea.admin.dettonville.int:2222/infra/ansible-datacenter
        // Splitting past the host/port demarcator
        def matches = cleanUrl =~ /(?:ssh:\/\/)?[-_a-zA-Z0-9.]+@([-_a-zA-Z0-9.]+)(?::\d+)?\/(.+)/
        if (matches.matches()) {
            host = matches[0][1] + ":3000" // Remap SSH host domain to Gitea's standard HTTP API port
            repoPath = matches[0][2]
        }
    } else if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
        // Example: http://gitea.admin.dettonville.int:3000/infra/ansible-datacenter
        def urlObj = new URL(cleanUrl)
        host = urlObj.getAuthority()
        repoPath = urlObj.getPath().replaceAll(/^\//, "")
    }

    if (!repoPath) {
        // Safe programmatic extraction failure fallback if regex breaks on specific edge-case layout
        log.warn("Regex parsing could not safely isolate repository path from: ${gitUrl}. Attempting token slice fallback.")
        repoPath = cleanUrl.tokenize('/')[-2..-1].join('/')
    }

    return "http://${host}/api/v1/repos/${repoPath}/statuses/${commitId}"
}
