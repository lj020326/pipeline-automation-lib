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

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

/**
 * Runs execMaven step with custom configuration where goals, defines and arguments are represented by a list
 *
 * @return The script
 * @see vars.execMaven.ExecMavenIT
 */
def execute() {
  execMaven(
      (SCM): [(SCM_URL): "https://subdomain.domain-new.tld/group/project1.git"],
      (MAVEN): [
          (MAVEN_POM)            : "path/to/customPom1.xml",
          (MAVEN_GOALS)          : ["customGoal1", "customGoal2"],
          (MAVEN_DEFINES)        : ["defineValue1": true, "defineFlag1": null],
          (MAVEN_GLOBAL_SETTINGS): "CUSTOM_GLOBAL_SETTINGS_VARIANT1",
          (MAVEN_SETTINGS)       : "CUSTOM_SETTINGS_VARIANT1",
          (MAVEN_ARGUMENTS)      : ["-B", "-U"]
      ]
  )
}

return this
