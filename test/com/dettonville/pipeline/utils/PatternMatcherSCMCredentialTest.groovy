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
package com.dettonville.pipeline.utils

import com.dettonville.testing.jenkins.pipeline.DSLTestBase
import com.dettonville.pipeline.credentials.Credential
import com.dettonville.pipeline.credentials.CredentialConstants
import com.dettonville.pipeline.credentials.CredentialParser
import com.dettonville.pipeline.model.PatternMatchable
import com.dettonville.pipeline.utils.resources.JsonLibraryResource
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class PatternMatcherSCMCredentialTest extends DSLTestBase {

  PatternMatcher underTest

  List<PatternMatchable> credentials

  @Override
  void setUp() throws Exception {
    super.setUp()
    underTest = new PatternMatcher()
    JsonLibraryResource res = new JsonLibraryResource(this.dslMock.getMock(), CredentialConstants.SCM_CREDENTIALS_PATH)
    CredentialParser parser = new CredentialParser()
    credentials = parser.parse(res.load())
  }

  @Test
  void shouldReturnConfigForSSH() throws Exception {
    Credential foundCredential = underTest.getBestMatch("git@git-ssh.domain.tld/group1/project1", credentials)
    assertNotNull("SCMCredentialProvider should find one match", foundCredential)
    assertEquals("ssh-git-credentials-id", foundCredential.getId())
  }

  @Test
  void shouldReturnConfigForHTTP() throws Exception {
    Credential foundCredential = underTest.getBestMatch("https://git-http.domain.tld", credentials)
    assertNotNull("SCMCredentialProvider should find one match", foundCredential)
    assertEquals("https-git-credentials-id", foundCredential.getId())
  }
}
