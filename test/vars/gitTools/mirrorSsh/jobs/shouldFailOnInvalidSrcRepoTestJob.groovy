/*-
 * #%L
 * dettonville.org
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
package vars.gitTools.mirrorSsh.jobs

import com.dettonville.pipeline.shell.CommandBuilder
import com.dettonville.pipeline.shell.CommandBuilderImpl

def execute() {
  gitTools.mirrorRepository("invalidRepository","git@gitrepository.dettonville.int/stash/scm/api/pipeline-automation-lib.git")
}

return this
