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
package vars.execMavenRelease.jobs

import com.dettonville.pipeline.environment.EnvironmentConstants

import static com.dettonville.pipeline.utils.ConfigConstants.*

/**
 * Runs execMavenRelease step with not supported https url
 *
 * @return The script
 * @see vars.execMavenRelease.ExecMavenReleaseIT
 */
def execute() {
  env.setProperty(EnvironmentConstants.GIT_BRANCH, null)
  execMavenRelease(
      (SCM): [(SCM_URL): "git@git-ssh.domain.tld:group/project.git"],
  )
}

return this
