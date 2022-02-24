package com.dettonville.api.pipeline.java.model
/**
 * Container class for information of the maven build.
 */
class MavenBuildInfo implements Serializable {
    String buildTargets
    String javaDocBuildTargets
    String javaDocFolder
    String snapshotDeployTarget
    boolean publishJUnit = false
    boolean publishJavaDoc = false
    boolean archiveFiles = false
    String archiveFilesSet
    String sonarSkipModules
    String applicationCode
    boolean settingsXmlInWorkspace = false
    String copySettingsXmlFromJob
    boolean deployable = false
    boolean sonarQubeWithRunner = false
    String sonarInstallation
    String jdkVersion
    String mavenVersion
    String branchName
    String builderCredentialsId
    String mavenMirrorUrl
    def pom
}
