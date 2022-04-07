/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2018 Dettonville DevOps
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
package com.dettonville.api.pipeline.environment

/**
 * Constants for environment variables used by Pipeline scripts and by Jenkins
 */
class EnvironmentConstants implements Serializable {

  private static final long serialVersionUID = 1L

  static final public String GIT_BRANCH = "GIT_BRANCH"
  static final public String BRANCH_NAME = "BRANCH_NAME"
  static final public String SCM_URL = "SCM_URL"
  static final public String TERM = "TERM"
  static final public String WORKSPACE = "WORKSPACE"


}
