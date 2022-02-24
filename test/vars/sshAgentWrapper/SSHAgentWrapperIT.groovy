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
package vars.sshAgentWrapper

import com.dettonville.testing.jenkins.pipeline.LibraryIntegrationTestBase
import com.dettonville.testing.jenkins.pipeline.StepConstants
import com.dettonville.api.pipeline.credentials.Credential
import com.dettonville.api.pipeline.credentials.CredentialAware
import com.dettonville.api.pipeline.ssh.SSHTarget
import org.junit.Test

import static com.dettonville.testing.jenkins.pipeline.recorder.StepRecorderAssert.assertOnce
import static com.dettonville.testing.jenkins.pipeline.recorder.StepRecorderAssert.assertOneShellCommand
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class SSHAgentWrapperIT extends LibraryIntegrationTestBase {

  @Test
  void shouldWrapWithoutCommandBuilder() {
    String expectedCommand = "echo 'with string ssh target'"
    loadAndExecuteScript("vars/sshAgentWrapper/jobs/shouldWrapWithStringSSHTarget.groovy")
    assertOneShellCommand(expectedCommand)

    // assert that the sshagent step was called once
    List keyAgentCredentialList = assertOnce(StepConstants.SSH_AGENT)
    assertEquals("provided ssh credentials are wrong", ['ssh-key-for-testservers'], keyAgentCredentialList)
  }

  @Test
  void shouldWrapWithCommandBuilder() {
    String expectedCommand = "echo 'with command builder'"
    CredentialAware commandBuilder = loadAndExecuteScript("vars/sshAgentWrapper/jobs/shouldWrapWithCommandBuilderTestJob.groovy")
    assertOneShellCommand(expectedCommand)

    // assert that the sshagent step was called once
    List keyAgentCredentialList = assertOnce(StepConstants.SSH_AGENT)
    assertEquals("provided ssh credentials are wrong", ['ssh-key-for-testservers'], keyAgentCredentialList)

    assertNotNull("command builder should not be null", commandBuilder)
    Credential credential = commandBuilder.getCredential()
    assertNotNull("Credentials should be stored in command builder", credential)
    assertEquals('testserveruser', credential.getUserName())
    assertEquals('ssh-key-for-testservers', credential.getId())
  }

  @Test
  void shouldWrapWithMultipleSSHTargets() {
    String expectedCommand = "echo 'multiple ssh targets'"
    List<SSHTarget> sshTargets = loadAndExecuteScript("vars/sshAgentWrapper/jobs/shouldWrapWithMultipleSSHTargetsTestJob.groovy")
    assertOneShellCommand(expectedCommand)

    // assert that the sshagent step was called once
    List keyAgentCredentialList = assertOnce(StepConstants.SSH_AGENT)
    assertEquals("provided ssh credentials are wrong", ['host1-ssh-credential-id', 'host2-ssh-credential-id', 'host3-ssh-credential-id'], keyAgentCredentialList)

    assertEquals("host1-ssh-credential-id", sshTargets[0].getCredential().getId())
    assertEquals("host2-ssh-credential-id", sshTargets[1].getCredential().getId())
    assertEquals("host3-ssh-credential-id", sshTargets[2].getCredential().getId())
    assertEquals("host3-ssh-credential-id", sshTargets[3].getCredential().getId())
  }
}
