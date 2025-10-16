/*-
 * #%L
 * apps.dettonville.org
 * %%
 * Copyright (C) 2025 Dettonville
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

package com.dettonville.pipeline.java


import com.dettonville.pipeline.java.model.MavenBuildInfo
import com.dettonville.pipeline.utils.Maven;

/**
 * Class for executing build tasks for Maven applications.
 */
class MavenBuild implements Serializable {

    com.dettonville.pipeline.java.model.MavenBuildInfo info;
    com.dettonville.pipeline.utils.Maven maven;
    def steps

    /**
     * Creates a new MavenBuild.
     * @param steps the groovy dsl context
     * @param maven the maven util class
     * @param mavenBuildInfo all the information required for executing the different maven build actions
     */
    MavenBuild(steps, com.dettonville.pipeline.utils.Maven maven, com.dettonville.pipeline.java.model.MavenBuildInfo mavenBuildInfo) {
        this.steps = steps
        this.maven = maven
        this.info = mavenBuildInfo
    }

    /**
     * Create a build tag number based upon the convention, pom and <b>currentBuild</b> param from the context.
     * <br>
     * The convention is that the build version should be Major.Minor.Patch-${build number}. <br>
     * The buildTag will then be v${buildVersion}. <br>
     * Example: <br>
     * <ul>
     *     <li>current version in pom.xml is 1.2.0</li>
     *     <li>current jenkins build number is 43</li>
     *     <li>buildTag=<b>v1.2.0-43</b></li>
     * <ul>
     *
     *
     * @param pom the maven pom object, the outcome of readMavenPom()
     * @return the string to use as build tag
     */
    String createBuildTag(pom) {
        def version = pom.version.replace("-SNAPSHOT", "-${steps.currentBuild.number}")
        return "v${version}"
    }

    /**
     * The default way to build a branch (i.e. not Main or Trunk).
     * <br>
     * Assumption: current version is a snapshot. <br>
     * Outcome: current version ends up as snapshot in ${SystemLetterCode}-snapshots.
     */
    void mavenBranchBuild() {
        String buildCommand = "clean deploy -B -s settings.xml ${info.buildTargets}"
        maven.mvn(buildCommand)

        if (info.publishJUnit) {
            steps.step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/*.xml'])
        }

    }

    /**
     * The default maven build (assuming mainline: Trunk or Main)
     *
     * @param pom the maven pom, outcome of readMavenPom()
     * @return the version that is published to nexus/tag
     */
    String mavenBuild(def pom) {
        assert pom: 'No POM data was supplied!'

        /**
         * See: SOLO-2374
         * Either we do the JavaDoc separately or we do it in the release.
         * If done in the release, we do not fail on error.
         *
         * This way everyone can always get feedback on this
         */
        def javaDocAddition = '-Dmaven.javadoc.skip=true'
        if (info.publishJavaDoc) {
            maven.mvn("clean install javadoc:aggregate -s settings.xml ${info.javaDocBuildTargets}")
            steps.step([$class: 'JavadocArchiver', javadocDir: info.javaDocFolder, keepAll: false])
        } else {
            javaDocAddition = '-Dmaven.javadoc.failOnError=false'
        }

        def version = pom.version.replace("-SNAPSHOT", "-${steps.currentBuild.number}")
        String buildCommand = "-DreleaseVersion=${version} -DdevelopmentVersion=${pom.version} -DpushChanges=false -DlocalCheckout=true clean release:clean release:prepare release:perform -B -s settings.xml ${javaDocAddition} ${info.buildTargets}"
        maven.mvn(buildCommand)

        if (info.publishJUnit) {
            steps.step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/*.xml'])
        }

        return version
    }

    /**
     * Makes sure there is a settings.xml to work with. <br>
     * Every maven action assumes there is a settings.xml in the root of the workspace. <br>
     * <br>
     * If not overridden by <i>settingsXmlInWorkspace</i> or <i>copySettingsXmlFromJob</i> params, a settings.xml is generated.<br>
     * It is generated based upon the following assumptions:<br>
     * <ul>
     *     <li>you have provided the <b>systemLetterCode</b></li>
     *     <li>you are using the <b>systemLetterCode</b> in your pom.xml</li>
     *     <li>you are using the master-pom as parent pom</li>
     *     <li>you have a technical user ${systemLetterCode}_BUILDER</li>
     *     <li>you have the default maven repositories ${systemLetterCode}-releases,-snapshots and -releases-virtual</li>
     *     <li>your technical user has write rights to the ${systemLetterCode}-releases and -snapshot repositories</li>
     * </ul>
     * <br>
     */
    void prepareSettingsXml() {
        def settingsXmlExists = steps.fileExists 'settings.xml'
        if (info.settingsXmlInWorkspace && settingsXmlExists) {
            return // we don't have to do anything, there's a settings.xml
        }

        if (info.copySettingsXmlFromJob != 'false') {
            steps.step(
                    [$class              : 'CopyArtifact',
                     filter              : 'settings.xml',
                     fingerprintArtifacts: true,
                     projectName         : "${info.copySettingsXmlFromJob}",
                     selector            : [$class: 'StatusBuildSelector', stable: false]
                    ]
            )
        } else {
            String credentialsId = "${info.applicationCode}_BUILDER"
            maven.writeSettingsXml(info.applicationCode, info.mavenMirrorUrl, credentialsId, false )
        }
    }
}
