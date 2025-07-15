/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.dettonville.pipeline.utils

/**
 * org.dettonville.dcapi is a collection of dcapi utility tasks for jenkins dsl to perform pipeline related tasks.
 *
 * ref: https://gitrepository.dettonville.int/stash/projects/dcapi/repos/devops/browse/src/org/dettonville/dcapi/ArtifactoryUtil.groovy
 */


/**
 * Artifactory Utility functions used to extend functionality of pipeline related to JFrog Artifactory
 *
 */

class ArtifactoryUtil implements Serializable {

    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * a reference to the build directory for artifacts in release or snapshots repo
     */
    def buildDir

    /**
     * a reference to the target directory for artifacts stored in artifactory
     */
    def targetDir

    /**
     * a reference to the file extension for the artifact generated
     */
    def artifactFileType

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public ArtifactoryUtil(steps) { this.steps = steps }

    /**
     * An object of artifactory server using Artifactory Plugin DSL
     *
     * @param artifactoryCredentials name of the credentialsId you want to use to access artifactory
     */
    public void artifactoryServer(String artifactoryCredentials) {
        def artifactoryUrl = "https://gitrepository.dettonville.int/artifactory"
        steps.Artifactory.newServer( url: artifactoryUrl, credentialsId: artifactoryCredentials )
    }

    /**
     * Function to upload artifacts to artifactory
     *
     * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
     * @param artifactoryCredentials name of the credentialsId you want to use to access artifactory
     */
    public void artifactoryUpload(script, String artifactoryCredentials, String orgSpace) {
        def version = getVersion()
        def appName = getAppName()
//        def orgSpace = getOrgSpace(script.env.ARTIFACTORY_ORGSPACE)

        def artifactName = setArtifactName(appName, version)
        setArtifactTargetDir(orgSpace, appName, version)
        artifactoryServer(artifactoryCredentials).upload(getUploadSpec())

        script.env.ARTIFACT_ID = artifactName
        script.env.ARTIFACT_UPLOAD_PATH = getArtifactTargetDir()
    }

    /**
     *
     * @return orgSpace the space in artifactory where the app should be stored in either release/snapshot dir
     */
    public getOrgSpace(String customSpace = null) {
        def orgSpace = customSpace ? customSpace : 'com/dettonville/dcapi'
        return orgSpace
    }

    /**
     * 
     * @param buildPath
     */
    public void setBuildDir(String buildPath) {
        buildDir = buildPath
    }

    /**
     *
     * @return
     */
    public getBuildDir() {
        return buildDir
    }

    /**
     *
     * @param orgSpace
     * @param appName
     * @param version
     */
    public void setArtifactTargetDir(String orgSpace, String appName, String version) {
        def releaseOrSnap = version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
        targetDir = "${releaseOrSnap}/${orgSpace}/${appName}/${version}"
    }

    /**
     *
     * @return
     */
    public getArtifactTargetDir() {
        return targetDir
    }

    /**
     *
     * @param fileType
     */
    public void setArtifactFileType(String fileType) {
        artifactFileType = fileType
    }

    /**
     *
     * @return
     */
    public getArtifactFileType() {
        return artifactFileType
    }

    /**
     *
     * @return
     */
    public getVersion() {
        def version = ""
        def fromFile
        // Todo #3: check if each of the below files exists before trying to read them. Create precedence order (example check manifest.yml first, then pom.xml then build.gradle, then package.json and so on) to read file. If the required string value is found exit
        if (steps.fileExists("build.gradle") && version == "") {
            fromFile = "build.gradle"
            version = steps.sh(returnStdout: true, script: "grep 'version[ ]*=' ${fromFile} | head -1").trim()

            if ( version != "" ) {
                version = version.split('=')[1].replaceAll("\"|\'| ", "")
            }
            // Todo: copy manifest.yml file if exists to the build dir and update upload spec accordingly to upload manifest.yml file in artifactory along with the builld package.
            setBuildDir("build/libs")
            setArtifactFileType(".jar")
        }
        if (steps.fileExists("manifest.yml") && version == "") {
            fromFile = "manifest.yml"
            version = steps.sh(returnStdout: true, script: "grep 'version[ ]*:' ${fromFile} | head -1").trim()
            if ( version != "" ) {
                version = version.split(':')[1].replaceAll("\"|\'| ", "")
            }
        }
        if (steps.fileExists("package.json") && version == "") {
            fromFile = "package.json"
            version = steps.sh(returnStdout: true, script: "grep '\"version\"[ ]*:' ${fromFile} | head -1").trim()
            if ( version != "" ) {
                version = version.split(':')[1].replaceAll("\"|\'|,| ", "")
            }
        }
        return version
    }

    /**
     *
     * @return
     */
    public getAppName() {
        def appName = ""
        def fromFile
        // Todo #4: check if each of the below files exists before trying to read them. Create precedence order (example check manifest.yml first, then pom.xml then build.gradle, then package.json and so on) to read file. If the required string value is found exit
        if (steps.fileExists("build.gradle") && appName == "") {
            fromFile = "build.gradle"
            appName = steps.sh(returnStdout: true, script: "grep 'name[ ]*=' ${fromFile} | head -1").trim()
            if ( appName != "" ) {
                appName = appName.split('=')[1].replaceAll("\"|\'| ", "")
            }
        }
        if (steps.fileExists("manifest.yml") && appName == "") {
            fromFile = "manifest.yml"
            appName = steps.sh(returnStdout: true, script: "grep 'name[ ]*:' ${fromFile} | head -1").trim()
            if ( appName != "" ) {
                appName = appName.split(':')[1].replaceAll("\"|\'| ", "")
            }
        }
        if (steps.fileExists("package.json") && appName == "") {
            fromFile = "package.json"
            appName = steps.sh(returnStdout: true, script: "grep '\"name\"[ ]*:' ${fromFile} | head -1").trim()
            if ( appName != "" ) {
                appName = appName.split(':')[1].replaceAll("\"|\'|,| ", "")
            }
        }
        return appName
    }

    /**
     *
     * @param appName
     * @param version
     * @return
     */
    public setArtifactName(String appName, String version) {
        def fileType = getArtifactFileType()
        def shortCommitHash = steps.sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
        // Todo: Prefer to use groovy syntax to retrieve timestamp in desired format
        def timestamp = steps.sh(returnStdout: true, script: "date +%Y%m%d-%H%M%S").trim()
        def buildPath = getBuildDir()
        def artifactName = "${version}-${shortCommitHash}-${timestamp}"
        steps.sh "mv ${buildPath}/*${fileType} ${buildPath}/${artifactName}${fileType} 2> /dev/null"
        return artifactName + fileType
    }

    /**
     *
     * @return
     */
    public getUploadSpec() {
        def buildPath = getBuildDir()
        def targetPath = getTargetDir()
        def artifactSpec = """{
            "files": [
                {
                  "pattern": "${buildPath}/*.jar",
                  "target": "${targetPath}/"
                }
            ]
        }"""
        return artifactSpec
    }

    /**
     * Function to download artifacts from artifactory
     *
     * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
     * @param artifactId name of the artifact you want to download from artifactory
     */
    public void artifactoryDownload(script, String artifactoryCredentials, String artifactUploadPath = null) {
        def artifactDir = getBuildDir()
        def artifactId = script.env.ARTIFACT_ID
        if ( ! artifactUploadPath ) {
            if ( ! script.env.ARTIFACT_UPLOAD_PATH ) {
                steps.echo "ERROR: Please provide a path to download artifacts using ARTIFACT_TARGET environment variable"
                steps.sh "exit 1"
            }
            artifactUploadPath = script.env.ARTIFACT_UPLOAD_PATH
        }
        if ( artifactId ) {
            artifactUploadPath = "${artifactUploadPath}/${artifactId}"
        }
        // Todo: Check if manifest.yml file exists and if so might not require doing a get checkout since that is the only missing file required from the source code for deployment
        try {
            steps.sh (returnStdout: true, script: "git rev-parse --is-inside-work-tree" ).trim()
        } catch (Exception ex) {
            steps.checkout(steps.scm)
        }
        // Todo #5: Prefer to use Jenkins File class over Shell script to check if Dir exists else create it
        steps.sh(returnStdout: true, script: "mkdir -p ${artifactDir}")

        artifactoryServer(artifactoryCredentials).download(getDownloadSpec(artifactUploadPath, artifactDir))

        if (steps.fileExists("manifest.yml")) {
            steps.echo "Found manifest.yml file, will update the path value with the downloaded artifact path"
            def manifestData = steps.readFile("manifest.yml")
            steps.writeFile(file: "manifest.yml", text: manifestData.replaceAll(/path:[a-zA-Z0-9. \/-]+/,"path: ${artifactDir}/${artifactId}"))
        }
    }

    /**
     *
     * @param artifactId
     * @param artifactDir
     * @return
     */
    public getDownloadSpec(String artifactId, String artifactDir) {
        def artifactSpec = """{
            "files": [
                {
                  "pattern": "${artifactId}",
                  "target": "${artifactDir}/",
                  "flat": "true"
                }
            ]
        }"""
        return artifactSpec
    }
}