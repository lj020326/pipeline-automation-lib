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
package vars.ansible.jobs

import com.dettonville.pipeline.tools.ansible.Role

/**
 * Runs execAnsible step with path to custom ansible executable
 *
 * @return The script
 * @see vars.execAnsible.ExecAnsibleIT
 */
def execute() {

  Role notExistingRole = new Role("not.existingrole")
  return ansible.getGalaxyRoleInfo(notExistingRole)
}

return this
