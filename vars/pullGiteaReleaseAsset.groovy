#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.Logger
import groovy.transform.Field

@Field Logger log = new Logger(this)

/**
 * Reusable helper step to fetch and unpack release archive assets from Gitea.
 *
 * Usage:
 *   pullGiteaReleaseAsset(
 *       giteaApiRepoBaseUrl: config.giteaApiRepoBaseUrl,
 *       tagName: config.codeIndexTagName,
 *       credentialId: config.giteaTokenCredentialId,
 *       targetDir: config.indexDir,
 *       archiveExtension: '.tar.gz'
 *   )
 */
def call(Map params = [:]) {
    String giteaApiRepoBaseUrl = params.get('giteaApiRepoBaseUrl', '')
    String tagName             = params.get('tagName', 'code-index')
    String credentialId        = params.get('credentialId', 'infra-jenkins-git-user')
    String targetDir           = params.get('targetDir', '.code_index')
    String archiveExtension    = params.get('archiveExtension', '.tar.gz')
    String archiveFileName     = params.get('archiveFileName', 'index_package.tar.gz')

    if (!giteaApiRepoBaseUrl) {
        error("pullGiteaReleaseAsset: Missing required parameter 'giteaApiRepoBaseUrl'.")
    }

    String giteaApiReleaseUrl = "${giteaApiRepoBaseUrl}/releases/tags/${tagName}"
    log.info("Fetching latest release metadata from Gitea API: ${giteaApiReleaseUrl}")

    try {
        // Ensure destination directory exists
        sh "mkdir -p ${targetDir}"

        // 1. Query Gitea API for release metadata
        def response = httpRequest(
            httpMode: 'GET',
            url: giteaApiReleaseUrl,
            acceptType: 'APPLICATION_JSON',
            authentication: credentialId,
            validResponseCodes: '200,404',
            quiet: true
        )

        if (response.status == 404) {
            log.warn("⚠️ Release tag '${tagName}' not found at endpoint: ${giteaApiReleaseUrl}")
            return
        }

        // 2. Parse response JSON using Jenkins built-in readJSON step
        def releaseData = readJSON(text: response.content)
        def assets = releaseData.assets ?: []

        def targetAsset = assets.find { it.name.endsWith(archiveExtension) || it.name.endsWith('.zip') }

        if (targetAsset) {
            def downloadUrl = targetAsset.browser_download_url
            log.info("Downloading index package asset '${targetAsset.name}' from: ${downloadUrl}")

            // 3. Download the archive directly to workspace
            httpRequest(
                httpMode: 'GET',
                url: downloadUrl,
                authentication: credentialId,
                outputFile: archiveFileName,
                validResponseCodes: '200',
                quiet: true
            )

            // 4. Extract archive to destination directory and cleanup
            log.info("Extracting ${archiveFileName} into ${targetDir}/...")
            sh """
                tar -xzf ${archiveFileName} -C ${targetDir}/
                rm -f ${archiveFileName}
            """
            log.info("Successfully deployed release asset to ${targetDir}/")
        } else {
            log.warn("⚠️ No suitable asset matching '${archiveExtension}' found in Gitea release '${tagName}'.")
        }
    } catch (Exception apiEx) {
        log.error("Failed to retrieve Gitea release asset: ${apiEx.getMessage()}")
        throw apiEx
    }
}
