#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.Logger

import groovy.transform.Field
@Field Logger log = new Logger(this)

/**
 * Reusable helper function to package a directory and publish/overwrite a rolling asset on Gitea releases.
 * Uses native Jenkins HTTP Request Plugin step with secure credential binding.
 */
def call(Map params = [:]) {
    def sourceDir        = params.get('sourceDir', '.code_index')
    def archiveName      = params.get('archiveName', 'code_index.tar.gz')
    def giteaHost        = params.get('giteaHost', 'gitea.admin.dettonville.int')
    def releaseTagName   = params.get('releaseTagName', 'code-index')
    def credentialsId    = params.get('credentialsId', 'infra-jenkins-git-user')
    def releaseName      = params.get('releaseName', 'Latest Code Indexes')
    def targetCommitish  = params.get('targetCommitish', 'main')
    def body             = params.get('body', 'Release update.')
    def draft            = params.get('draft', false)
    def prerelease       = params.get('prerelease', false)

    log.info("Archiving directory '${sourceDir}' into '${archiveName}'...")
    sh "tar -czf ${archiveName} ${sourceDir}"

    def originalRemoteUrl = sh(script: "git config --get remote.origin.url", returnStdout: true).trim()
    log.info("Original remote origin URL detected: ${originalRemoteUrl}")

    // Parse 'owner/repo' from Git remote (e.g., 'infra/ansible-datacenter')
    def repoPath = originalRemoteUrl.replaceAll(/^.*[\/:]([^\/]+\/[^\/]+?)(\.git)?$/, '$1')
    log.info("Target Gitea repository path: ${repoPath}")

    def giteaApiUrl = "https://${giteaHost}/api/v1/repos/${repoPath}"

    try {
        def releaseId = null

        // 1. Fetch release info for tag
        log.info("Checking if release tag '${releaseTagName}' exists...")
        def tagResponse = httpRequest httpMode: 'GET',
                                      url: "${giteaApiUrl}/releases/tags/${releaseTagName}",
                                      authentication: credentialsId,
                                      validResponseCodes: '200,404',
                                      quiet: true

        if (tagResponse.status == 200) {
            def releaseData = readJSON text: tagResponse.content
            releaseId = releaseData.id
            log.info("Found existing '${releaseTagName}' release with ID: ${releaseId}")
        } else {
            log.info("Release '${releaseTagName}' not found. Creating continuous release...")
            def payload = JsonUtils.printToJsonString([
                tag_name: releaseTagName,
                target_commitish: targetCommitish,
                name: releaseName,
                body: body,
                draft: draft,
                prerelease: prerelease
            ])

            def createResponse = httpRequest httpMode: 'POST',
                                             contentType: 'APPLICATION_JSON',
                                             requestBody: payload,
                                             url: "${giteaApiUrl}/releases",
                                             authentication: credentialsId,
                                             quiet: true

            def createData = readJSON text: createResponse.content
            releaseId = createData.id
            log.info("Created new release ID: ${releaseId}")
        }

        // 2. Check for existing attachment with the same filename and remove to prevent bloat
        log.info("Fetching existing assets for release ID ${releaseId}...")
        def assetsResponse = httpRequest httpMode: 'GET',
                                         url: "${giteaApiUrl}/releases/${releaseId}/assets",
                                         authentication: credentialsId,
                                         quiet: true

        def assetsData = readJSON text: assetsResponse.content
        def existingAsset = assetsData.find { it.name == archiveName }

        if (existingAsset) {
            log.info("Deleting prior asset ID ${existingAsset.id} (${archiveName})...")
            httpRequest httpMode: 'DELETE',
                        url: "${giteaApiUrl}/releases/${releaseId}/assets/${existingAsset.id}",
                        authentication: credentialsId,
                        quiet: true
        }

        // 3. Upload fresh archive asset via multipart form upload
        log.info("Uploading updated ${archiveName} to release ${releaseId}...")
        httpRequest httpMode: 'POST',
                    url: "${giteaApiUrl}/releases/${releaseId}/assets?name=${archiveName}",
                    uploadFile: archiveName,
                    multipartName: 'attachment',
                    authentication: credentialsId,
                    quiet: true

//         httpRequest httpMode: 'POST',
//                     url: "${giteaApiUrl}/releases/${releaseId}/assets?name=${archiveName}",
//                     uploadFile: archiveName,
//                     multipartName: 'attachment',
//                     wrapAsMultipart: false,
//                     contentType: 'APPLICATION_OCTETSTREAM',
//                     authentication: credentialsId,
//                     quiet: true

        log.info("Successfully published ${archiveName} asset to Gitea release '${releaseTagName}'!")

    } catch (Exception apiEx) {
        log.error("Gitea release asset publication failed: ${apiEx.getMessage()}")
        throw apiEx
    }
}
