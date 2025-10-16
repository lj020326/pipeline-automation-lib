/*-
 * #%L
 * apps.dettonville.org
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
package com.dettonville.pipeline.environment

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
