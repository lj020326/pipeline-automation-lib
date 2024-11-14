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

package com.dettonville.api.pipeline.java


import com.dettonville.api.pipeline.utils.Utilities
import com.dettonville.api.pipeline.java.model.MavenBuildInfo
import com.dettonville.api.pipeline.utils.Maven

/**
 * Class for SonarQube actions for the JavaPipeline.
 */
class SonarQube implements Serializable {

    def steps

    SonarQube(steps) {
        this.steps = steps
    }

    /**
     * Execute a Sonar Analysis for this application. <br>
     * It will generate a sonar.properties file before running the analysis.
     * @param pom the maven pom, retrieved from readMavenPom()
     * @param sonarSkipModules a semi-colon separated list of maven modules that should not be analysed
     * @param util Utilities class
     */
    void sonarAnalysis(com.dettonville.api.pipeline.java.model.MavenBuildInfo mavenBuildInfo, com.dettonville.api.pipeline.utils.Utilities util, com.dettonville.api.pipeline.utils.Maven maven) {
        assert mavenBuildInfo.pom: 'Pom data is not present!'
        assert mavenBuildInfo.sonarSkipModules: 'sonarSkipModules data is not present!'
        assert mavenBuildInfo.sonarQubeWithRunner: 'sonarQubeWithRunner data is not present!'
        assert util: 'Utilities class is not present!'

        if (!mavenBuildInfo.sonarQubeWithRunner) {
            withSonarQubeEnv(mavenBuildInfo.sonarInstallation) {
                maven.mvn("sonar:sonar -Dsonar.links.ci=${env.BUILD_URL}")
            }
        } else {

            def sonarQubeRunner
            if (steps.isUnix()) {
                sonarQubeRunner = steps.tool name: 'Linux sonar-runner-2.4', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            } else {
                sonarQubeRunner = steps.tool name: 'sonar-runner-2.4', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            }

            def modules = pom.modules
            def sourcesList = ""
            def moduleCount = 0
            for (module in modules) {
                if (!util.splittedStringContainsValue(sonarSkipModules, ';', module)) {
                    if (moduleCount > 0) {
                        sourcesList += ", "
                    }
                    moduleCount++
                    sourcesList += "${module}/src/main/java"
                }
            }

            def sonarPropertiesFile = 'sonar-project.properties'
            steps.writeFile encoding: 'UTF-8', file: sonarPropertiesFile, text: """
            sonar.projectKey=$pom.groupId:$pom.artifactId
            sonar.projectName=$pom.name
            sonar.projectVersion=$pom.version
            sonar.sources=$sourcesList
            """
            try {
                if (steps.isUnix()) {
                    steps.sh "$sonarQubeRunner/bin/sonar-runner -e"
                } else {
                    steps.bat "$sonarQubeRunner\\bin\\sonar-runner.bat -e"
                }
            } finally {
                steps.archive sonarPropertiesFile
            }
        }
    }
}
