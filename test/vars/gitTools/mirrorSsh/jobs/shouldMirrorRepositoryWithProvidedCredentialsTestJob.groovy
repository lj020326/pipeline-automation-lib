/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2017 - 2018 dettonville.org DevOps
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
package vars.gitTools.mirrorSsh.jobs

import com.dettonville.api.pipeline.shell.CommandBuilder
import com.dettonville.api.pipeline.shell.CommandBuilderImpl

def execute() {
  List srcCredentialIds = [
    "src-cred-1",
    "src-cred-2",
    "src-cred-3",
  ]
  List targetCredentialIds = [
    "target-cred-1",
    "target-cred-2",
    "target-cred-3",
  ]
  gitTools.mirrorRepository("git@host1.domain.tld:api/pipeline-automation-lib.git","git@host2.domain.tld:api/pipeline-automation-lib.git", srcCredentialIds, targetCredentialIds)
}

return this
