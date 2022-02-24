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
package com.dettonville.api.pipeline.model

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings

@SuppressFBWarnings('ME_ENUM_FIELD_SETTER')
enum Tool {

  MAVEN("MAVEN_HOME"),
  JDK("JAVA_HOME"),
  ANSIBLE("ANSIBLE_HOME"),
  GIT("GIT_HOME"),
  GROOVY("GROOVY_HOME"),
  MSBUILD("MSBUILD_HOME"),
  ANT("ANT_HOME"),
  PYTHON("PYTHON_HOME"),
  DOCKER("DOCKER_HOME"),
  NODEJS("NPM_HOME")

  String envVar

  Tool(String envVar) {
    this.envVar = envVar
  }
}
