
import com.dettonville.pipeline.credentials.Credential
import com.dettonville.pipeline.ssh.SSHTarget
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

/**
 * Adapter step for one ssh target without credential aware parameter
 *
 * @param sshTarget the target to connect to
 * @param body the closure to execute inside the wrapper
 */
void call(String sshTarget, Closure body) {
    this.call([new SSHTarget(sshTarget)], body)
}

/**
 * Adapter step for one ssh target without credential aware parameter
 *
 * @param sshTarget the target to connect to as value object
 * @param body the closure to execute inside the wrapper
 */
void call(SSHTarget sshTarget, Closure body) {
    this.call([sshTarget], body)
}

/**
 * Step for encapsulating the provided body into a sshagent step with ssh credential autolookup
 *
 * @param sshTargets the targets to connect to
 * @param credentialAware The credential aware object where the step should set the found credentials for the first target
 * @param body the closure to execute inside the wrapper
 */
void call(List<SSHTarget> sshTargets, Closure body) {

    Map foundCredentials = [:]
    for (int i = 0; i < sshTargets.size(); i++) {
        SSHTarget sshTarget = sshTargets[i]

        // auto lookup ssh credentials
        log.trace("auto lookup credentials for : '${sshTarget.getHost()}'")
        Credential sshCredential = credentials.lookupSshCredential(sshTarget.getHost())
        if (sshCredential != null) {
            log.debug("auto lookup found the following credential for '${sshTarget.getHost()}' : '${sshCredential.id}'")
            foundCredentials[sshCredential.id] = sshCredential
            sshTarget.setCredential(sshCredential)
        } else {
            log.warn("No ssh credential was found for '$sshTarget' during auto lookup. Make sure to configure the credentials! See sshAgentWrapper.md for details.")
        }
    }

    // only use unique credentials
    List sshCredentials = []
    foundCredentials.each {
        String k, Credential v ->
            sshCredentials.push(v.getId())
    }


    log.trace("start ssh agent")
    sshagent(sshCredentials) {
        body()
    }
}
