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
package vars.execMaven.jobs

import static com.dettonville.api.pipeline.utils.ConfigConstants.MAVEN
import static com.dettonville.api.pipeline.utils.ConfigConstants.MAVEN_GOALS
import static com.dettonville.api.pipeline.utils.ConfigConstants.MAVEN_INJECT_PARAMS
import static com.dettonville.api.pipeline.utils.ConfigConstants.SCM
import static com.dettonville.api.pipeline.utils.ConfigConstants.SCM_URL

/**
 * Runs execMaven step with default configuration to test auto lookup for global and local maven settings
 *
 * @return The script
 * @see vars.execMaven.ExecMavenIT
 */
def execute() {
  execMaven(
      (SCM): [
          (SCM_URL): "https://subdomain.evenbetterdomain.tld/group3/project1.git"
      ],
      (MAVEN): [
          (MAVEN_GOALS)        : ["clean", "verify"],
          (MAVEN_INJECT_PARAMS): true
      ]
  )
}

return this
