/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
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
package com.dettonville.pipeline.shell

/**
 * Interface for command builders that support the initialization with configuration
 */
interface ConfigAwareCommandBuilder {

  /**
   * Initializes the command builder with the provided configuration
   *
   * @param config The configuration for the commandbuilder
   * @return The instance of the command builder
   */
  ConfigAwareCommandBuilder applyConfig(Map config)

}
