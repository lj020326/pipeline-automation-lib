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
    } else if (gitRemoteRepoType == "gitea" || gitRemoteRepoType == "git") {
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

        notifyArgs['name'] = args?.gitRemoteBuildName ?: args?.gitRemoteBuildKey ?: "Jenkins Job Run"

        // Since publishChecks exits cleanly with a [WARN] message instead of throwing an exception,
        // we force standard Git SCM integration paths directly into our HTTP REST handler.
        log.info("Attempting to publish via generic Jenkins Checks API...")
        executeGiteaRestAPI(args, notifyArgs, giteaStatus, giteaConclusion)
//         if (gitRemoteRepoType == "git") {
//             log.info("Standard Git SCM detected. Utilizing direct Gitea Status REST API payload execution...")
//             executeGiteaRestAPI(args, notifyArgs, giteaStatus, giteaConclusion)
//         } else {
//             log.info("Attempting to publish via generic Jenkins Checks API...")
//             try {
//                 log.info("Attempting to publish via generic Jenkins Checks API...")
//                 publishChecks(notifyArgs)
//             } catch (Exception ex) {
//                 log.warn("publishChecks hard-faulted: ${ex.getMessage()}. Falling back to manual REST API...")
//                 executeGiteaRestAPI(args, notifyArgs, giteaStatus, giteaConclusion)
//             }
//         }
    }
}

/**
 * Encapsulates the explicit HTTP Request logic to update the commit status via Gitea API.
 * Uses native Jenkins HTTP Request Plugin step with secure credential binding.
 */
def executeGiteaRestAPI(Map args, Map notifyArgs, String giteaStatus, String giteaConclusion) {
    String commitId = args?.gitCommitId ?: env.GIT_COMMIT
    String gitUrl = args?.gitRepoUrl ?: env.GIT_URL ?: ""
    String gitCredentialId = "infra-jenkins-git-user"

    if (!commitId || !gitUrl) {
        log.error("Cannot execute status update: Missing context parameters. gitCommitId: ${commitId}, gitUrl: ${gitUrl}")
        return
    }

    // Uses the fixed port/host derivation mapping confirmed by your test runs
    String giteaApiUrl = deriveGiteaStatusApiUrl(gitUrl, commitId)
    log.info("Resolved Gitea REST Endpoint: ${giteaApiUrl}")

    String apiState = 'pending'
    if (giteaStatus == 'COMPLETED') {
        apiState = (giteaConclusion == 'SUCCESS') ? 'success' : 'failure'
    }

    try {
        def payload = JsonUtils.printToJsonString([
            state: apiState,
            target_url: env.BUILD_URL ?: "",
            description: args?.gitRemoteBuildSummary ?: "Jenkins Build Status",
            context: notifyArgs['name']
        ])

        // Fix the security warning by delegating credential handling to the plugin's native authentication parameter.
        // This removes the need for both the 'withCredentials' wrapper block and the explicit 'customHeaders' token token assignment.
        httpRequest httpMode: 'POST',
                    contentType: 'APPLICATION_JSON',
                    requestBody: payload,
                    url: giteaApiUrl,
                    authentication: gitCredentialId,
                    quiet: true,
                    responseHandle: 'NONE'

        log.info("Successfully posted commit status update to Gitea via native REST API handler.")
    } catch (Exception apiEx) {
        log.error("Gitea direct REST API notification failed: ${apiEx.getMessage()}")
    }
}

/**
 * Encapsulates the explicit HTTP Request logic to update the commit status via Gitea API.
 * Securely binds credentials and custom parameters to the shell execution environment.
 */
def executeGiteaRestAPICurl(Map args, Map notifyArgs, String giteaStatus, String giteaConclusion) {
    String commitId = args?.gitCommitId ?: env.GIT_COMMIT
    String gitUrl = args?.gitRepoUrl ?: env.GIT_URL ?: ""
    String gitCredentialId = "infra-jenkins-git-user"

    if (!commitId || !gitUrl) {
        log.error("Cannot execute status update: Missing context parameters. gitCommitId: ${commitId}, gitUrl: ${gitUrl}")
        return
    }

    String giteaApiUrl = deriveGiteaStatusApiUrl(gitUrl, commitId)
    log.info("Resolved Gitea REST Endpoint: ${giteaApiUrl}")

    String apiState = 'pending'
    if (giteaStatus == 'COMPLETED') {
        apiState = (giteaConclusion == 'SUCCESS') ? 'success' : 'failure'
    }

    withCredentials([usernamePassword(credentialsId: gitCredentialId, passwordVariable: 'GITEA_TOKEN', usernameVariable: 'GITEA_USER')]) {
        try {
            def payloadMap = [
                state: apiState,
                target_url: env.BUILD_URL ?: "",
                description: args?.gitRemoteBuildSummary ?: "Jenkins Build Status",
                context: notifyArgs['name']
            ]
            String payload = JsonUtils.printToJsonString(payloadMap)

            // Fix: Use native 'withEnv' to supply variables to the underlying shell context safely
            withEnv(["GITEA_API_URL=${giteaApiUrl}", "TARGET_PAYLOAD=${payload}"]) {
                sh(
                    script: '''
                        curl -s -X POST "${GITEA_API_URL}" \
                             -H "Content-Type: application/json" \
                             -H "Authorization: token ${GITEA_TOKEN}" \
                             -d "${TARGET_PAYLOAD}" > /dev/null
                    ''',
                    returnStdout: false
                )
            }
            log.info("Successfully posted commit status update to Gitea via secure shell curl handler.")
        } catch (Exception apiEx) {
            log.error("Gitea direct REST API notification failed: ${apiEx.getMessage()}")
        }
    }
}

/**
 * Parses out parameters across SSH or HTTP Git schemas to assemble the Gitea API endpoint.
 * Remaps SSH host domains to the standard proxy web layout without hardcoded port 3000.
 */
String deriveGiteaStatusApiUrl(String gitUrl, String commitId) {
    String cleanUrl = gitUrl.trim()
    if (cleanUrl.endsWith('.git')) {
        cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 4)
    }

    // Defaulting to standard HTTP host. If your Gitea UI runs behind an HTTPS proxy,
    // change the "http" prefix below to "https".
    String host = "gitea.admin.dettonville.int"
    String repoPath = ""

    if (cleanUrl.startsWith("ssh://") || cleanUrl.contains("@")) {
        def matches = cleanUrl =~ /(?:ssh:\/\/)?[-_a-zA-Z0-9.]+@([-_a-zA-Z0-9.]+)(?::\d+)?\/(.+)/
        if (matches.matches()) {
            host = matches[0][1]
            repoPath = matches[0][2]
        }
    } else if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
        def urlObj = new URL(cleanUrl)
        host = urlObj.getAuthority()
        repoPath = urlObj.getPath().replaceAll(/^\//, "")
    }

    if (!repoPath) {
        log.warn("Regex matching could not confidently extract the repo slug. Utilizing structural slice fallback.")
        repoPath = cleanUrl.tokenize('/')[-2..-1].join('/')
    }

    // Dropped explicit port 3000 to route via standard web server ports (80/443 proxy setups)
    return "https://${host}/api/v1/repos/${repoPath}/statuses/${commitId}"
}
