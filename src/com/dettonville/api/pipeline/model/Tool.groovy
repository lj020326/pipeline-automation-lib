/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Lee Johnson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
