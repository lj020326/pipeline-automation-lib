
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

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Utility step to transfer files via scp.
 * This step uses the sshAgentWrapper for ssh credential auto lookup
 *
 * @param config Configuration options for the step
 */
void call(Map config = null) {
    config = config ?: [:]

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
