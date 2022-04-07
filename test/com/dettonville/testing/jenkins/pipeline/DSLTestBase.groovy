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
package com.dettonville.testing.jenkins.pipeline

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import org.junit.Before

/**
 * Utility base class for all test classes that need a mocked DSL object
 */
class DSLTestBase {

  DSLMock dslMock

  @Before
  void setUp() throws Exception {
    this.dslMock = new DSLMock()
    Logger.initialized = false
    // init logger
    Logger.init(this.dslMock.getMock(), LogLevel.ALL)
  }
}
