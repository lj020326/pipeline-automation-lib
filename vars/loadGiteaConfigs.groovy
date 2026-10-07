#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.logging.Logger

import groovy.transform.Field
@Field Logger log = new Logger(this)

Map call(Map baseConfig = [:]) {
    Map giteaConfigs = [:]

    // Dynamically extract owner and repo name from the git remote URL
//     String remoteUrl = sh(script: "git remote get-url origin", returnStdout: true).trim()
    String remoteUrl = env.GIT_URL

    // Extract repoOwner and repoName
    def matcher = remoteUrl =~ /(?::|\/)([^\/]+)\/([^\/]+?)(?:\.git)?$/
    if (!matcher) {
        error("Could not parse repository owner and name from remote URL: ${remoteUrl}")
    }

    String repoOwner = matcher[0][1]
    String repoName = matcher[0][2]

    // Extract the host portion (handles both standard URIs and SCP-like SSH URLs)
    String repoRemoteHost = ""
    if (remoteUrl =~ /^[^:]+:\/\//) {
        // Handle URIs with scheme (e.g., ssh://git@gitea.admin.dettonville.int:2222/infra/ansible-datacenter.git)
        URI uri = new URI(remoteUrl)
        repoRemoteHost = uri.getHost()
    } else {
        // Handle SCP-style URLs (e.g., git@gitea.admin.dettonville.int:infra/ansible-datacenter.git)
        def hostMatcher = remoteUrl =~ /^(?:[^@]+@)?([^:\/]+)/
        if (hostMatcher) {
            repoRemoteHost = hostMatcher[0][1]
        }
    }

    log.debug("remoteURL: ${remoteUrl}")
    log.debug("repoRemoteHost: ${repoRemoteHost}")
    log.debug("repoOwner: ${repoOwner}")
    log.debug("repoName: ${repoName}")

    giteaConfigs.giteaHost = baseConfig.get('giteaHost', repoRemoteHost)
    giteaConfigs.giteaApiBaseUrl = baseConfig.get('giteaApiBaseUrl', "https://${giteaConfigs.giteaHost}/api/v1")
    giteaConfigs.gitCommitId = env.GIT_COMMIT

    log.debug("giteaApiBaseUrl: ${giteaConfigs.giteaApiBaseUrl}")

    giteaConfigs.giteaApiRepoBaseUrl = "${giteaConfigs.giteaApiBaseUrl}/repos/${repoOwner}/${repoName}"
    giteaConfigs.giteaApiRepoPullsUrl = "${giteaConfigs.giteaApiBaseUrl}/repos/${repoOwner}/${repoName}/pulls"

    Map config = MapMerge.merge(baseConfig, giteaConfigs)

    log.info("Merged config=${JsonUtils.printToJsonString(config)}")

    return config
}
