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
package vars.sshAgentWrapper.jobs

import com.dettonville.pipeline.shell.ScpCommandBuilderImpl
import com.dettonville.pipeline.ssh.SSHTarget
import org.jenkinsci.plugins.workflow.cps.DSL

/**
 * Runs the transferScp step with ssh credential auto lookup (key + username)
 *
 * @return The script
 * @see vars.setScmUrl.SetScmUrlIT
 */
def execute() {
  ScpCommandBuilderImpl commandBuilder = new ScpCommandBuilderImpl((DSL) this.steps)
  SSHTarget target = new SSHTarget("testserver1.testservers.domain.tld")
  sshAgentWrapper(target) {
    commandBuilder.setCredential(target.getCredential())
    sh "echo 'with command builder'"
  }
  return commandBuilder
}

return this
