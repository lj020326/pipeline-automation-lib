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
package vars.notifyMail

import hudson.model.Result
import com.dettonville.testing.jenkins.pipeline.LibraryIntegrationTestBase
import com.dettonville.api.pipeline.environment.EnvironmentConstants
import org.junit.Test

import static com.dettonville.testing.jenkins.pipeline.StepConstants.EMAILEXT
import static com.dettonville.testing.jenkins.pipeline.recorder.StepRecorderAssert.assertNone
import static com.dettonville.testing.jenkins.pipeline.recorder.StepRecorderAssert.assertOnce
import static com.dettonville.api.pipeline.utils.ConfigConstants.*
import static org.junit.Assert.assertEquals

class NotifyMailDefaultsIT extends LibraryIntegrationTestBase {

  @Override
  void setUp() throws Exception {
    super.setUp()
    this.setEnv("BUILD_NUMBER", "2")
    this.setEnv(EnvironmentConstants.GIT_BRANCH, "DETECTED_GIT_BRANCH")
  }

  @Test
  void shouldNotNotifyOnSuccess() {
    this.runWrapper.setResult(Result.SUCCESS.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertNone(EMAILEXT)
  }

  @Test
  void shouldNotNotifyOnAbort() {
    this.runWrapper.setResult(Result.ABORTED.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertNone(EMAILEXT)
  }

  @Test
  void shouldNotNotifyOnNotBuild() {
    this.runWrapper.setResult(Result.NOT_BUILT.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertNone(EMAILEXT)
  }

  @Test
  void shouldNotifyOnFixed() {
    this.runWrapper.setResult(Result.SUCCESS.toString())
    this.runWrapper.setPreviousBuildResult(Result.FAILURE.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertOnce(EMAILEXT)
  }

  @Test
  void shouldNotifyOnUnstable() {
    this.runWrapper.setResult(Result.UNSTABLE.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    LinkedHashMap extmailCall = assertOnce(EMAILEXT)
    assertCorrectExtmailCall(extmailCall)
  }

  @Test
  void shouldNotifyOnStillUnstable() {
    this.runWrapper.setResult(Result.UNSTABLE.toString())
    this.runWrapper.setPreviousBuildResult(Result.UNSTABLE.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertOnce(EMAILEXT)
  }

  @Test
  void shouldNotifyOnFailure() {
    this.runWrapper.setResult(Result.FAILURE.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertOnce(EMAILEXT)
  }

  @Test
  void shouldNotifyOnStillFailing() {
    this.runWrapper.setResult(Result.FAILURE.toString())
    this.runWrapper.setPreviousBuildResult(Result.FAILURE.toString())
    loadAndExecuteScript("vars/notifyMail/jobs/notifyMailDefaultsJob.groovy")
    assertOnce(EMAILEXT)
  }

  void assertCorrectExtmailCall(LinkedHashMap extmailCall) {
    assertEquals("subject is wrong", '${PROJECT_NAME} - Build # ${BUILD_NUMBER} - UNSTABLE', extmailCall[NOTIFY_SUBJECT] ?: 'subjectNotSet')
    assertEquals("body is wrong", '${DEFAULT_CONTENT}', extmailCall[NOTIFY_BODY] ?: 'bodyNotSet')
    assertEquals("attachmentsPattern is wrong", '', extmailCall[NOTIFY_ATTACHMENTS_PATTERN] != null ? extmailCall.attachmentsPattern : 'attachmentsPatternNotSet')
    assertEquals("attachLog is wrong", false, extmailCall[NOTIFY_ATTACH_LOG] != null ? extmailCall.attachLog : 'attachLogNotSet')
    assertEquals("compressLog is wrong", false, extmailCall[NOTIFY_COMPRESS_LOG] != null ? extmailCall.compressLog : 'compressLogNotSet')
    assertEquals("mimeType is wrong", null, extmailCall[NOTIFY_MIME_TYPE])
    assertEquals("to is wrong", null, extmailCall[NOTIFY_TO])


    String expectedRecipientProviderList = '[[$class:CulpritsRecipientProvider], [$class:DevelopersRecipientProvider], [$class:FirstFailingBuildSuspectsRecipientProvider], [$class:RequesterRecipientProvider], [$class:UpstreamComitterRecipientProvider]]'

    assertEquals("expectedRecipientProviderList is wrong", expectedRecipientProviderList, extmailCall[NOTIFY_RECIPIENT_PROVIDERS] ? extmailCall[NOTIFY_RECIPIENT_PROVIDERS].toString() : 'recipientProvidersNotSet')
  }
}
