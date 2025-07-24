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

import static com.dettonville.pipeline.utils.ConfigConstants.*

/**
 * Runs execAnsible step with path to custom ansible executable
 *
 * @return The script
 * @see vars.execAnsible.ExecAnsibleIT
 */
def execute() {

  Map config = [
      (ANSIBLE): [
          (ANSIBLE_INSTALLATION) : "ansible-inject-params-installation",
          (ANSIBLE_INVENTORY)    : "ansible-inject-params-inventory",
          (ANSIBLE_PLAYBOOK)     : "ansible-inject-params-playbook",
          (ANSIBLE_EXTRA_VARS)   : ["param": "value"],
          (ANSIBLE_INJECT_PARAMS): true
      ]
  ]

  ansible.execPlaybook(config)
}

return this
