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
package com.dettonville.pipeline.utils.resources

import com.cloudbees.groovy.cps.NonCPS
import com.dettonville.pipeline.utils.logging.Logger
import net.sf.json.JSON
import org.jenkinsci.plugins.workflow.cps.DSL

/**
 * Utility function for loading JSON library files
 *
 * @see LibraryResource
 */
class JsonLibraryResource implements Serializable {

  private static final long serialVersionUID = 1L

  Logger log = new Logger(this)

  LibraryResource libraryResource

  DSL dsl

  String file

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param file Path to the file
   */
  JsonLibraryResource(DSL dsl, String file) {
    this.dsl = dsl
    this.file = file
    libraryResource = new LibraryResource(dsl, file)
  }

  /**
   * Loads the resource file via LibraryResource and uses the Pipeline Utility step readJSON to parse the content into
   * a JSON object
   *
   * @return The loaded file as JSON object
   */
  @NonCPS
  JSON load() {
    def jsonStr = libraryResource.load()
    try {
      JSON json = dsl.readJSON(text: jsonStr)
      log.trace("parsed json: ${json}")
      return json
    } catch (Exception ex) {
      log.debug("Error parsing '$file' from project pipeline library: ${ex}")
      throw ex
    }
  }
}
