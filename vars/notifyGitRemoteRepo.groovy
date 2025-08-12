#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map args=[:], String gitRemoteRepoType) {

    log.info("${gitRemoteRepoType} => args=${JsonUtils.printToJsonString(args)}")

    // Define valid states for Gitea Checks API
    // Status can be IN_PROGRESS or COMPLETED
    Set<String> VALID_GITEA_CHECK_STATUSES = ['QUEUED', 'IN_PROGRESS', 'COMPLETED']
    // Conclusion can be SUCCESS, FAILURE, NEUTRAL, SKIPPED, UNSTABLE, ABORTED
    Set<String> VALID_GITEA_CHECK_CONCLUSIONS = ['SUCCESS', 'FAILURE', 'NEUTRAL', 'SKIPPED', 'UNSTABLE', 'ABORTED']

    // Define valid states for Bitbucket Status API
    // BuildState can be INPROGRESS, SUCCESSFUL, FAILED
    Set<String> VALID_BITBUCKET_BUILD_STATES = ['INPROGRESS', 'SUCCESSFUL', 'FAILED']

    Map notifyArgs = [:]
    if (gitRemoteRepoType=="bitbucket") {
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
    } else if (gitRemoteRepoType=="gitea") {
        notifyArgs['title'] = args.gitRemoteBuildKey
        if (args?.gitRemoteBuildName) {
            notifyArgs['name'] = args.gitRemoteBuildName
        }

        // Validate and map Gitea Checks status and conclusion
        if (args?.gitRemoteBuildStatus) {
            String incomingStatus = args.gitRemoteBuildStatus

            // Map common pipeline results to Gitea Checks API status and conclusion
            switch (incomingStatus) {
                case 'IN_PROGRESS':
                case 'QUEUED':
                    if (VALID_GITEA_CHECK_STATUSES.contains(incomingStatus)) {
                        notifyArgs['status'] = incomingStatus
                    } else {
                        log.warn("Invalid Gitea Check status '${incomingStatus}' provided. Must be one of: ${VALID_GITEA_CHECK_STATUSES.join(', ')}. Setting to IN_PROGRESS as default.")
                        notifyArgs['status'] = 'IN_PROGRESS' // Default to IN_PROGRESS if invalid
                    }
                    // No conclusion for IN_PROGRESS or QUEUED
                    break
                case 'SUCCESSFUL':
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'SUCCESS'
                    break
                case 'FAILED':
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'FAILURE'
                    break
                case 'ABORTED':
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'ABORTED'
                    break
                case 'UNSTABLE':
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'UNSTABLE'
                    break
                case 'SKIPPED':
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'SKIPPED'
                    break
                case 'NEUTRAL':
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'NEUTRAL'
                    break
                default:
                    // For any other unexpected status, default to COMPLETED and log a warning
                    log.warn("Unexpected Git remote build status '${incomingStatus}'. Setting Gitea Check status to COMPLETED and conclusion to NEUTRAL.")
                    notifyArgs['status'] = 'COMPLETED'
                    notifyArgs['conclusion'] = 'NEUTRAL'
                    break
            }
        }

        // The 'conclusion' field is derived from gitRemoteBuildStatus in the switch above,
        // but if there's a separate 'gitRemoteBuildConclusion' parameter, we can validate it here.
        // This assumes gitRemoteBuildConclusion would explicitly override or supplement the derived conclusion.
        if (args?.gitRemoteBuildConclusion) {
            String explicitConclusion = args.gitRemoteBuildConclusion
            if (VALID_GITEA_CHECK_CONCLUSIONS.contains(explicitConclusion)) {
                notifyArgs['conclusion'] = explicitConclusion
            } else {
                log.warn("Invalid Gitea Check conclusion '${explicitConclusion}' provided. Must be one of: ${VALID_GITEA_CHECK_CONCLUSIONS.join(', ')}. Ignoring invalid conclusion.")
                // Do not set, or set to null, to avoid passing an invalid value
                // If a conclusion was already derived, this won't overwrite it unless explicitly handled.
            }
        }

        if (args?.gitRemoteBuildSummary) {
            notifyArgs['summary'] = args.gitRemoteBuildSummary
        }
        publishChecks(notifyArgs)
    }
}
