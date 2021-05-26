/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2018 Dettonville DevOps
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
package com.dettonville.api.pipeline.utils

/**
 * Class for Maven specific utilities that can be used by all pipelines.
 */
class Maven implements Serializable {

    def steps
    String mavenTool
    String jdkTool

    /**
     * Creates a new Maven utility class.
     *
     * @param steps the pipeline dsl context
     * @param mavenTool string of the mavenTool (retrieved via the 'tool' dsl command)
     * @param jdkTool string of the jdkTool (retrieved via the 'tool' dsl command)
     */
    Maven(steps, String mavenTool, String jdkTool) {
        assert steps: 'I need steps to be valid'
        assert mavenTool: 'I need mavenTool to be valid'
        assert jdkTool: 'I need jdkTool to be valid'

        this.steps = steps
        this.mavenTool = mavenTool
        this.jdkTool = jdkTool
    }

    /**
     * Executes a shell command, using the appropriate method depending on the platform.
     * It will use 'sh' for linux and else 'bat'.
     *
     * @param command the command to execute
     */
    private void shellCommand(String command) {
        if (steps.isUnix()) {
            steps.sh command
        } else {
            steps.bat command
        }
    }

    /**
     * Executes a maven command.
     * It will make sure the context is set for based upon the supplied JDK and Maven tools.
     *
     * The command will be prefixed with the maven executable, so to achieve 'mvn -v', you call mvn('-v').
     *
     * @param mavenCommand the maven command to execute
     */
    void mvn(String mavenCommand) {
        steps.withEnv(["M2_HOME=$mavenTool", "JAVA_HOME=${jdkTool}"]) {
            String buildCommand = "${mavenTool}/bin/mvn ${mavenCommand}"
            shellCommand(buildCommand)
        }
    }

    /**
     * Will write a settings.xml into the current directory.
     *
     * The settings will use the CODE for the service id's and the mirror.
     * Utilizing the standard maven setup of the master-pom and the standard nexus setup.
     *
     * Assumed:
     * <ul>
     *  <li>there is a ${CODE}-releases (hosted) repository</li>
     *  <li>there is a ${CODE}-releases-virtual (group) repository</li>
     *  <li>there is a ${CODE}-snapshot (hosted) repository</li>
     *  <li>there is a technical user with the supplied credentialsId, that can use above repositories</li>
     * </ul>
     *
     * @param CODE the system letter code (i.e. SOLO)
     * @param credentialsId the credentialsId to use for the Nexus reopositories
     * @param isMainlineBuild if true, only adds releases mirror, if false also a snapshots mirror
     */
    void writeSettingsXml(String CODE, String mavenMirrorUrl, String credentialsId, boolean isMainlineBuild) {
        assert CODE: 'I need CODE to be valid'
        assert credentialsId: 'I need credentialsId to be valid'

        def releasesMirror = """
            <mirror>
                <id>${CODE}-Mirror</id>
                <mirrorOf>central</mirrorOf>
                <name>${CODE}-Mirror</name>
                <url>${mavenMirrorUrl}
                </url>
            </mirror>
            """

        steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, passwordVariable: 'PSS', usernameVariable: 'USR']]) {

            mvn("--encrypt-password ${steps.env.PSS} > enc.txt")

            def encFile = steps.readFile 'enc.txt'
            def ENC = encFile.trim()

            steps.writeFile encoding: 'UTF-8', file: 'settings.xml', text: """
                 <settings>
                    <mirrors>
                        ${releasesMirror}
                    </mirrors>
                
                    <servers>
                        <server>
                            <id>${CODE}-releases</id>
                            <username>${steps.env.USR}</username>
                            <password>${ENC}</password>
                        </server>
                        <server>
                            <id>${CODE}-snapshots</id>
                            <username>${steps.env.USR}</username>
                            <password>${ENC}</password>
                        </server>
                    </servers>
                </settings>
                """
        }
    }
}
