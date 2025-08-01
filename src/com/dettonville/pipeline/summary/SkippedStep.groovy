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
package com.dettonville.pipeline.summary

/**
 * Class for keeping tabs on skipped steps in the pipeline.
 */
class SkippedStep implements Serializable {
    private String name
    private String message

    /**
     * Creates a new SkippedStep.
     * @param name the name of the step that was skipped
     * @param message the reasoning why the step was skipped
     */
    SkippedStep(String name, String message) {
        this.name = name
        this.message = message
    }


    @Override
    String toString() {
        return "<li>" +
                "<b>" + name + '</b>: ' +
                message + '</li>'
    }
}
