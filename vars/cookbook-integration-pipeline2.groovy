#!groovy

/*
 */
@Library('pipeline-utils')

def pipeline

node ('GIT') {
    echo "Checking out pipeline template. SCM=${scm}"
    checkout poll: false, changelog: false, scm: scm
    pipeline = load('cookbook-integration-pipeline.groovy')
    stash name: "Helpers", includes: "modules/*"
    stash name: "Static Files", includes: "pipeline_files/*"
    git changelog: false, poll: false, url: "${env.COOKBOOK_REGISTRY}"
    stash name: "Cookbook Registry", includes: "cookbooks.yaml"
}

try {
    project = pipeline.getProject(gitRepoUrl)
    cookbook = pipeline.getCookbook(gitRepoUrl)

    checkoutClosure = {
        git poll: true, branch: delegate.branch, url: gitRepoUrl
    }

    pipelineResult = pipeline.runPipeline(project, cookbook, checkoutClosure, '', dryRun)

    def userNotificationEmail = pipelineResult['userNotificationEmail']

    if (userNotificationEmail == null) {
        return
    }

    if (pipelineResult['versionIntegrated']) {
        pipeline.sendEmail(userNotificationEmail,
                "Cookbook [${cookbook}] version ${pipelineResult['version']} build ran successfully but it is already integrated.",
                "Pipeline Job: ${env.BUILD_URL}/consoleFull.")
    } else if (dryRun) {
        pipeline.sendEmail(userNotificationEmail,
                "Dry run for Cookbook [${cookbook}] version ${pipelineResult['version']} ran successfully. This Cookbook version is not integrated yet.",
                "Dry run for Cookbook [${cookbook}] version ${pipelineResult['version']} ran successfully. This Cookbook version is not integrated yet.\n\nPipeline Job: ${env.BUILD_URL}/consoleFull.")
    } else {
        pipeline.sendEmail(userNotificationEmail,
                "Cookbook [${cookbook}] version ${pipelineResult['version']} integrated successfully.",
                "Cookbook [${cookbook}] version ${pipelineResult['version']} has integrated successfully to the ${pipelineResult['env']} environment(s) via Cookbook Integration Pipeline.\n\nPipeline Job: ${env.BUILD_URL}/consoleFull.")
    }
} catch (err) {
    node('GIT') {
        if (pipeline.isCookbookRegistered(cookbook)) {
            userNotificationEmail = pipeline.getMaintainerEmail(pipeline.getCookbookRegistry(cookbook))
        } else {
            userNotificationEmail = null
        }
    }
    if (err.toString().trim() != 'hudson.AbortException: Rejected' && userNotificationEmail != null) {
        pipeline.sendEmail(userNotificationEmail,
                "Integration of Cookbook [${cookbook}] failed!",
                "${err}\n\nPipeline Job: ${env.BUILD_URL}/consoleFull.")
    }
    throw err
}
