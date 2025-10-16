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
package com.dettonville.pipeline.credentials

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import com.dettonville.pipeline.utils.logging.Logger
import net.sf.json.JSON
import net.sf.json.JSONObject

// @formatter:off
/**
 * Parses an incoming json object into Credential objects
 *
 * Expected json file format:
 * [
 *      {
 *          "pattern": "subdomain\.domain\.tld[:/]group1",
 *          "id": "Id of the credential in the jenkins instance",
 *          "comment": "Comment for the credential"
 *      },
 *      { .. }
 * ]
 *
 * @see Credential
 */
// @formatter:on
class CredentialParser implements Serializable {

  private static final long serialVersionUID = 1L

  Logger log = new Logger(this)

  /**
   * Parses a json object containing a list of credential objects into a list of Credential
   * Only valid Credential objects are added to the returned list
   *
   * @param jsonContent The json content loaded via JsonLibraryResource
   * @return The parsed list of valid Credential objects
   */
  @NonCPS
  @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
  List<Credential> parse(JSON jsonContent) {
    Credential credential = null
    List<Credential> parsedCredentials = []
    // Walk through entries, try to parse them as Credential object and add it to the returned list
    jsonContent.each { JSONObject entry ->
      String comment = entry.comment ?: null
      String id = entry.id ?: null
      String pattern = entry.pattern ?: null
      String username = entry.username ?: null
      credential = new Credential(pattern, id, comment, username)
      log.trace("parsed credential file: ", credential)
      if (credential.isValid()) {
        parsedCredentials.push(credential)
      } else {
        log.debug("credential is invalid because id and/or pattern is missing")
      }
      log.trace("entry: ", entry)
    }

    return parsedCredentials
  }
}
