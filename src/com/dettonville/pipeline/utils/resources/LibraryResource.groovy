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
package com.dettonville.pipeline.utils.resources

import com.cloudbees.groovy.cps.NonCPS
import com.dettonville.pipeline.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.DSL

/**
 * Utility function for loading library resources
 */
class LibraryResource implements Serializable {

  private static final long serialVersionUID = 1L

  String file = null
  String content = null
  DSL dsl

  Logger log = new Logger(this)

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param file path to the file
   */
  LibraryResource(DSL dsl, String file) {
    this.file = file
    this.dsl = dsl
  }

  /**
   * Loads the file and returns the content as String
   *
   * @return The content of the loaded library resource
   */
  @NonCPS
  String load() {
    log.trace("loading $file", this)
    if (content != null) {
      return content
    }
    try {
      content = this.dsl.libraryResource(file)
      log.trace("content of $file: ${content}")
      return content
    } catch (Exception ex) {
      log.debug("Error loading $file from project pipeline library, error ${ex}")
      throw ex
    }
  }
}
