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

import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class TypeUtilsTest {

  TypeUtils underTest

  @Before
  void setUp() {
    underTest = new TypeUtils()
  }

  @Test
  void isListShouldReturnTrue() {
    assertTrue(underTest.isList(new ArrayList()))
  }

  @Test
  void isListShouldReturnFalse() {
    assertFalse(underTest.isList(""))
    assertFalse(underTest.isList(true))
    assertFalse(underTest.isList(false))
    assertFalse(underTest.isList(1))
    assertFalse(underTest.isList([:]))
  }

  @Test
  void isMapShouldReturnTrue() {
    assertTrue(underTest.isMap([:]))
    assertTrue(underTest.isMap(new HashMap()))
    assertTrue(underTest.isMap(new LinkedHashMap()))
  }

  @Test
  void isMapShouldReturnFalse() {
    assertFalse(underTest.isMap(""))
    assertFalse(underTest.isMap(true))
    assertFalse(underTest.isMap(false))
    assertFalse(underTest.isMap(1))
    assertFalse(underTest.isMap(new ArrayList()))
  }
}
