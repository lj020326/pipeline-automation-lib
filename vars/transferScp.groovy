/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2018 dettonville.org DevOps
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
import com.dettonville.api.pipeline.credentials.Credential
import com.dettonville.api.pipeline.credentials.CredentialConstants
import com.dettonville.api.pipeline.credentials.CredentialParser
import com.dettonville.api.pipeline.shell.ScpCommandBuilderImpl
    import com.dettonville.api.pipeline.ssh.SSHTarget
    import com.dettonville.api.pipeline.utils.PatternMatcher
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.resources.JsonLibraryResource
import net.sf.json.JSON
import org.jenkinsci.plugins.workflow.cps.DSL

import static com.dettonville.api.pipeline.utils.ConfigConstants.SCP

/**
 * Utility step to transfer files via scp.
 * This step uses the sshAgentWrapper for ssh credential auto lookup
 *
 * @param config Configuration options for the step
 */
void call(Map config = null) {
    config = config ?: [:]
    Logger log = new Logger(this)

    // retrieve the configuration and set defaults
    Map scpConfig = (Map) config[SCP] ?: [:]

    log.trace("SCP config: ", scpConfig)

    // initialize the command builder
    ScpCommandBuilderImpl commandBuilder = new ScpCommandBuilderImpl((DSL) this.steps)
    commandBuilder.applyConfig(scpConfig)

    SSHTarget sshTarget = new SSHTarget(commandBuilder.getHost())

    // use the sshAgentWrapper for ssh credential auto lookup
    sshAgentWrapper([sshTarget]) {
        // provide credentials from sshAgentWrapper to commandbuilder
        commandBuilder.setCredential(sshTarget.getCredential())
        command = commandBuilder.build()
        log.info("The following scp command will be executed", command)
        // execute the command
        sh(command)
    }
}
