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
import com.dettonville.api.pipeline.managedfiles.ManagedFile
import com.dettonville.api.pipeline.managedfiles.ManagedFileConstants
import com.dettonville.api.pipeline.managedfiles.ManagedFileParser
import com.dettonville.api.pipeline.model.PatternMatchable
import com.dettonville.api.pipeline.utils.resources.JsonLibraryResource
import org.junit.Test

class PatternMatcherMavenSettingsTest extends DSLTestBase {

  PatternMatcher underTest

  List<PatternMatchable> managedFiles

  @Override
  void setUp() throws Exception {
    super.setUp()
    underTest = new PatternMatcher()
    JsonLibraryResource res = new JsonLibraryResource(this.dslMock.getMock(), ManagedFileConstants.MAVEN_SETTINS_PATH)
    ManagedFileParser parser = new ManagedFileParser()
    managedFiles = parser.parse(res.load())
  }

  @Test
  void shouldFindSSHFormat() {
    ManagedFile settings = underTest.getBestMatch("git@subdomain.domain.tld:group1", this.managedFiles)
    assertNotNull("resulting managed file is null", settings)

    assertEquals("group1-maven-settings-id", settings.getId())
    assertEquals("group1-maven-settings-name", settings.getName())
    assertEquals("group1-maven-settings-comment", settings.getComment())
    assertEquals("subdomain.domain.tld[:/]group1", settings.getPattern())
  }

  @Test
  void shouldFindUrlFormat() {
    ManagedFile settings = underTest.getBestMatch("https://subdomain.domain.tld/group1", this.managedFiles)
    assertNotNull("resulting managed file is null", settings)
    assertEquals("group1-maven-settings-id", settings.getId())
    assertEquals("group1-maven-settings-name", settings.getName())
    assertEquals("group1-maven-settings-comment", settings.getComment())
    assertEquals("subdomain.domain.tld[:/]group1", settings.getPattern())
  }

  @Test
  void shouldFindBetterMatchWithSSHFormat() {
    ManagedFile settings = underTest.getBestMatch("git@subdomain.domain.tld:group1/project1", this.managedFiles)
    assertNotNull("resulting managed file is null", settings)
    assertEquals("group1-project1-maven-settings-id", settings.getId())
    assertEquals("group1-project1-maven-settings-name", settings.getName())
    assertEquals("group1-project1-maven-settings-comment", settings.getComment())
    assertEquals("subdomain.domain.tld[:/]group1/project1", settings.getPattern())
  }

  @Test
  void shouldFindBetterMatchWithURLFormat() {
    ManagedFile settings = underTest.getBestMatch("https://subdomain.domain.tld/group1/project2", this.managedFiles)
    assertNotNull("resulting managed file is null", settings)
    assertEquals("group1-project2-maven-settings-id", settings.getId(),)
    assertEquals("group1-project2-maven-settings-name", settings.getName(),)
    assertEquals("group1-project2-maven-settings-comment", settings.getComment(),)
    assertEquals("subdomain.domain.tld[:/]group1/project2", settings.getPattern())
  }

  @Test
  void shouldFindNothing() {
    ManagedFile settings = underTest.getBestMatch("should-not-find-me", this.managedFiles)
    assertNull("There should be no found file", settings)
  }
}
