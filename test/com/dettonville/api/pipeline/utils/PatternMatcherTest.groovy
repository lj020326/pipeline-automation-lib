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
package com.dettonville.api.pipeline.utils

import com.dettonville.testing.jenkins.pipeline.DSLTestBase
import com.dettonville.api.pipeline.credentials.Credential
import com.dettonville.api.pipeline.model.PatternMatchable
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class PatternMatcherTest extends DSLTestBase {

  PatternMatcher underTest

  @Override
  @Before
  void setUp() {
    super.setUp()
    underTest = new PatternMatcher()
  }

  @Test
  void shouldFindMatch() {
    PatternMatchable result = underTest.getBestMatch("pattern1", this.createTestCredentials())
    assertNotNull("The CredentialUtilTest should find one ManagedFile", result)
    assertEquals("pattern1-id", result.getId())
  }

  @Test
  void shouldFindFirstMatch() {
    PatternMatchable result = underTest.getBestMatch("pattern", this.createTestCredentials())
    assertNotNull("The CredentialUtilTest should find one ManagedFile", result)
    assertEquals("pattern-id", result.getId())
  }

  @Test
  void shouldFindBetterMatch() {
    PatternMatchable result = underTest.getBestMatch("pattern-better", this.createTestCredentials())
    assertNotNull("The CredentialUtilTest should find one ManagedFile", result)
    assertEquals("i-am-a-better-match-id", result.getId())
  }

  List<Credential> createTestCredentials() {
    List<Credential> files = new ArrayList<Credential>()
    files.push(new Credential("pattern1", "pattern1-id", "pattern1-name"))
    files.push(new Credential("pattern2", "pattern2-id", "pattern2-name"))
    files.push(new Credential("pattern", "pattern-id", "pattern2-name"))
    files.push(new Credential("pattern", "i-should-not-be-returned-id", "i-should-not-be-returned-name"))
    files.push(new Credential("pattern-b", "i-am-a-better-match-id", "i-am-a-better-match-name"))

    return files
  }

}
