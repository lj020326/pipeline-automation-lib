#!/usr/bin/env groovy

/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2018 dettonville.org DevOps
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

import com.dettonville.api.pipeline.docker.parser.ConfigParser;
import com.dettonville.api.pipeline.docker.*;

def call(String yamlName) {
    def yaml = readYaml file: yamlName;

    def buildNumber = Integer.parseInt(env.BUILD_ID)

    // load project's configuration
    ProjectConfiguration projectConfig = ConfigParser.parse(yaml, env);

    def imageName = projectConfig.dockerConfiguration.imageName().toLowerCase();

    // build the image specified in the configuration
    def customImage = docker.build(imageName, "--file ${projectConfig.dockerfile} .");

    // adds the last step of the build.
    def closure = buildSteps(projectConfig, customImage);

    // each service is a closure that when called it executes its logic and then calls a closure, the next step.
    projectConfig.services.each {

        closure = "${it.service.getVar()}"(projectConfig, it.version, closure);
    }

    // we execute the top level closure so that the cascade starts.
    try {
        closure([:]);
    } finally{
        deleteDockerImages(projectConfig);
    }
}
