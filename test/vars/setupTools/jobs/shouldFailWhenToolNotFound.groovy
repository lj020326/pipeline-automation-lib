/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2017 dettonville.org DevOps
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
package vars.setupTools.jobs

import com.dettonville.api.pipeline.model.Tool

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

/**
 * Runs the setupTools step with invalid tool to test failure when tool is not found
 *
 * @return The script
 * @see vars.setupTools.SetupToolsIT
 */
def execute() {
  setupTools((TOOLS): [
      [(TOOL_NAME): "invalid-maven-tool", (TOOL_TYPE): Tool.MAVEN]
  ])
}

return this
