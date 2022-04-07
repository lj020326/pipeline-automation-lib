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
package vars.libraryIntegrationTestBase.jobs

import com.dettonville.api.pipeline.environment.EnvironmentConstants

import static com.dettonville.api.pipeline.utils.ConfigConstants.SCM

/**
 * Runs execNpm step  with default configuration by providing only the scm url
 *
 * @return The script
 * @see vars.execMaven.ExecMavenIT
 */
def execute() {
  return findFiles(glob: "**/notfound.json")
}

return this
