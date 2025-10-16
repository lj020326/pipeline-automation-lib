/*-
 * #%L
 * apps.dettonville.org
 * %%
 * Copyright (C) 2025 Dettonville
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

package com.dettonville.pipeline.tools.ansible

import com.cloudbees.groovy.cps.NonCPS
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

/**
 * Utility class for a loaded requirements YAML file.
 * Provides parsing into Role objects and transforming them into checkout configurations.
 */
class RoleRequirements implements Serializable {

  private static final long serialVersionUID = 1L

  List<Role> _roles = []

  com.dettonville.pipeline.utils.logging.Logger log = new com.dettonville.pipeline.utils.logging.Logger(this)

  List ymlContent

  boolean _parsed = false

  /**
   * @param ymlContent The loaded YAML content from a requirements YAML file
   */
  RoleRequirements(List ymlContent) {
    this.ymlContent = ymlContent
  }

  /**
   * parses the ymlContent into Role objects
   *
   * @see Role
   */
  @NonCPS
  public void parse() {
    if (_parsed == true) {
      return
    }
    for (Map requirement in this.ymlContent) {
      String src = requirement.src ?: null
      String scm = requirement.scm ?: null
      String name = requirement.name ?: null
      String version = requirement.version ?: null

      Role role = new Role(src)
      if (scm != null) role.setScm(scm)
      if (name != null) role.setName(name)
      if (version != null) role.setVersion(version)

      if (role.isValid()) {
        log.trace("adding role")
        _roles.push(role)
      }
    }

    this._parsed = true
  }

  /**
   * Getter function for roles
   * @return
   */
  @NonCPS
  public List<Role> getRoles() {
    this.parse()
    return this._roles
  }

  /**
   * Transforms the parsed ansible roles into checkout configurations which can be used with the checkoutScm step
   * @return A list of checkout configurations for scmCheckout
   */
  @NonCPS
  public List<Map> getCheckoutConfigs() {
    List ret = []
    for (Role role in this.getRoles()) {
      log.debug("getCheckoutConfigs role: " + role.getSrc())
      if (role.isScmRole()) {
        log.debug("getCheckoutConfigs role is scmRole!")
        Map scmConfig = [
            (SCM): [
                (SCM_URL)       : role.getSrc(),
                (SCM_BRANCHES)  : [[name: role.getVersion()]],
                (SCM_EXTENSIONS): [
                    [$class: 'LocalBranch'],
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: role.getName()],
                    [$class: 'ScmName', name: role.getName()]
                ]
            ]
        ]
        ret.push(scmConfig)
      }
    }
    return ret
  }
}
