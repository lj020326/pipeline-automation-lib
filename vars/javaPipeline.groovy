import com.dettonville.api.pipeline.java.Git
import com.dettonville.api.pipeline.java.SonarQube
import com.dettonville.api.pipeline.java.model.JavaPipelineInfo
import com.dettonville.api.pipeline.java.MavenBuild
import com.dettonville.api.pipeline.java.model.MavenBuildInfo
import com.dettonville.api.pipeline.summary.ImpactfulAction
import com.dettonville.api.pipeline.summary.PipelineSummary
import com.dettonville.api.pipeline.summary.SkippedStep
import com.dettonville.api.pipeline.utils.Maven
import com.dettonville.api.pipeline.utils.Utilities

def call(def buildSlaveLabel) {
    node(buildSlaveLabel) {

        // VARS AND CLASSES
        Utilities utilities = new Utilities(this)
        PipelineSummary pipelineSummary = new PipelineSummary(this)
        String scmType = getScmType(scm)
        Git git = new Git(this)
        git.git = tool name: 'GIT', type: 'hudson.plugins.git.GitTool'

        JavaPipelineInfo javaPipelineInfo = new JavaPipelineInfo(this, utilities, scmType)
        javaPipelineInfo.isMainlineBuild = utilities.isMainLineBranch(scmType, env.BRANCH_NAME)

        MavenBuild mavenBuild
        Maven maven
        MavenBuildInfo mavenBuildInfo
        String tagName

        // HOUSE KEEPING
        currentBuild.description = "Build @${env.NODE_NAME}[${env.EXECUTOR_NUMBER}]"
        deleteDir()

        // THE PIPELINE
        stage('Checkout') {
            checkout scm
        }

        try {
            stage('Prepare Build Configuration') {
                def propsFileName = 'jenkins.properties'
                def propertiesFileExists = fileExists propsFileName
                if (propertiesFileExists) {
                    mavenBuildInfo = processJenkinsProperties(utilities, propsFileName)
                    mavenBuildInfo.branchName = env.BRANCH_NAME
                    mavenBuildInfo.pom = readMavenPom()
                } else {
                    error 'No jenkins.properties file found.'
                }

                def jdkTool = utilities.getJDKTool(mavenBuildInfo.jdkVersion)
                def mavenTool = utilities.getMavenTool(mavenBuildInfo.mavenVersion)
                maven = new Maven(this, mavenTool, jdkTool)
                mavenBuild = new MavenBuild(this, maven, mavenBuildInfo)

                tagName = mavenBuild.createBuildTag(mavenBuildInfo.pom)
                if (scmType == 'git') {
                    git.cleanAndResetToBranch(mavenBuildInfo.branchName)
                    git.createTag(tagName)
                }

                mavenBuild.prepareSettingsXml()
            }

            stage('Build') {
                if (javaPipelineInfo.isMainlineBuild) {

                    String version = mavenBuild.mavenBuild(mavenBuildInfo.pom)
                    ImpactfulAction action = new ImpactfulAction('Maven release', "Released ${version}")
                    pipelineSummary.addAction(action)

                    if (mavenBuildInfo.builderCredentialsId && scmType == 'git') { // currently no other tag pushing supported
                        git.pushTagToRepo(tagName, mavenBuildInfo.builderCredentialsId)
                        ImpactfulAction impactfulAction = new ImpactfulAction('Push Tag', "Pushed tag ${tagName} to scm (${scmType}")
                        pipelineSummary.addAction(impactfulAction)
                    } else {
                        println "****WARNING*******: Did not find env.builderCredentialsId, cannot push tag!"
                        SkippedStep step = new SkippedStep("Push Tag", "Did not find builderCredentialsId, cannot push tag")
                        pipelineSummary.addStep(step)
                    }
                } else { // Non-Mainline
                    mavenBuild.mavenBranchBuild()
                    SkippedStep step = new SkippedStep("Maven Build", "Its a non mainline branch (not Master or Trunk) so not doing a maven release")
                    pipelineSummary.addStep(step)
                }

            }

            if (mavenBuildInfo.archiveFiles) {
                stage('Archive') {
                    utilities.archiveFiles(mavenBuildInfo.archiveFilesSet)
                }
            }

            if (javaPipelineInfo.isMainlineBuild) {
                stage('Sonar') {
                    SonarQube sonarQube = new SonarQube(this)
                    sonarQube.sonarAnalysis(mavenBuildInfo, utilities)
                }
            } else { // Non-Mainline
                SkippedStep step = new SkippedStep("Code analysis", "Its a non mainline branch (not Master or Trunk) so not doing Sonar or NexusIQ")
                pipelineSummary.addStep(step)
            }
        } finally {
            stage('Summary') {
                pipelineSummary.createSummaryHtml()
                publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: true, reportDir: 'pipelineSummary', reportFiles: 'index.html', reportName: 'Pipeline Summary'])
            }
            stage('CleanUp') {
                step([$class: 'WsCleanup', notFailBuild: true])
            }
        }
    }
}

MavenBuildInfo processJenkinsProperties(Utilities util, String propsFileName) {
    def jenkinsProperties
    timeout(time: 10, unit: 'SECONDS') {
        jenkinsProperties = readProperties file: propsFileName
    }

    MavenBuildInfo buildInfo = new MavenBuildInfo()
    buildInfo.deployable = util.getPropertyOrDefault(jenkinsProperties, 'deployable', 'false')
    buildInfo.jdkVersion = util.getPropertyOrDefault(jenkinsProperties, 'jdkVersion', '1.7')
    buildInfo.mavenVersion = util.getPropertyOrDefault(jenkinsProperties, 'mavenVersion', '3.3.3')
    buildInfo.buildTargets = util.getPropertyOrDefault(jenkinsProperties, 'buildTargets', '')
    if (util.getPropertyOrDefault(jenkinsProperties, 'publishJavaDoc', 'false') == 'true') {
        buildInfo.publishJavaDoc = true
    }
    buildInfo.javaDocBuildTargets = util.getPropertyOrDefault(jenkinsProperties, 'javaDocBuildTargets', '')
    buildInfo.javaDocFolder = util.getPropertyOrDefault(jenkinsProperties, 'javaDocFolder', 'target/site')
    if (util.getPropertyOrDefault(jenkinsProperties, 'publishJUnit', 'false') == 'true') {
        buildInfo.publishJUnit = true
    }
    buildInfo.snapshotDeployTarget = util.getPropertyOrDefault(jenkinsProperties, 'snapshotDeployTarget', '')
    if (util.getPropertyOrDefault(jenkinsProperties, 'archiveFiles', 'false') == 'true') {
        buildInfo.archiveFiles = true
    }
    buildInfo.archiveFilesSet = util.getPropertyOrDefault(jenkinsProperties, 'archiveFilesSet', '')
    buildInfo.sonarSkipModules = util.getPropertyOrDefault(jenkinsProperties, 'sonarSkipModules', ';')
    buildInfo.applicationCode = jenkinsProperties.applicationCode
    if (util.getPropertyOrDefault(jenkinsProperties, 'settingsXmlInWorkspace', 'false') == 'true') {
        buildInfo.settingsXmlInWorkspace = true
    }
    buildInfo.copySettingsXmlFromJob = util.getPropertyOrDefault(jenkinsProperties, 'copySettingsXmlFromJob', 'false')1

    buildInfo.sonarInstallation = util.getPropertyOrDefault(jenkinsProperties, 'sonarInstallation', '')
    if (util.getPropertyOrDefault(jenkinsProperties, 'sonarQubeWithRunner', 'false') == 'true') {
        buildInfo.sonarQubeWithRunner = true
    }

    buildInfo.mavenMirrorUrl = util.getPropertyOrDefault(mavenMirrorUrl, 'mavenMirrorUrl', '')

    buildInfo.builderCredentialsId = util.getPropertyOrDefault(jenkinsProperties, 'builderCredentialsId', 'false')
    return buildInfo
}
