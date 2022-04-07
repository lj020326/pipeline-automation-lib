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
package com.dettonville.api.pipeline.managedfiles


import com.dettonville.testing.jenkins.pipeline.DSLTestBase
import org.junit.Test

import static org.junit.Assert.assertEquals

class ManagedFileParserTest extends DSLTestBase {

  com.dettonville.api.pipeline.utils.resources.JsonLibraryResource jsonLibraryResource
  Object testContent

  @Override
  void setUp() throws Exception {
    super.setUp()
    jsonLibraryResource = new com.dettonville.api.pipeline.utils.resources.JsonLibraryResource(this.dslMock.getMock(), "managedfiles/maven/parser-test.json")
    testContent = jsonLibraryResource.load()

  }

  @Test
  void shouldOnlyReturnValidResources() {
    ManagedFileParser underTest = new ManagedFileParser()
    List<ManagedFile> parseResult = underTest.parse(testContent)
    assertEquals("should only contain one managed file", 1, parseResult.size())
    ManagedFile parsedFile = parseResult.get(0)
    assertEquals("should-be-parsed-pattern", parsedFile.getPattern())
    assertEquals("should-be-parsed-id", parsedFile.getId())
    assertEquals("should-be-parsed-name", parsedFile.getName())
    assertEquals("should-be-parsed-comment", parsedFile.getComment())
  }
}
