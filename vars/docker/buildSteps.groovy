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

import com.dettonville.api.pipeline.docker.*;
import com.dettonville.api.pipeline.docker.steps.Step;

def call(ProjectConfiguration projectConfig, def dockerImage) {
    return { variables ->
        List<Step> stepsA = projectConfig.steps.steps
        def links = variables.collect { k, v -> "--link ${v.id}:${k}" }.join(" ")
        dockerImage.inside(links) {
            stepsA.each { step ->
                stage(step.name) {
                    step.commands.each { command ->
                        sh command
                    }
                }
            }
        }
    }
}
