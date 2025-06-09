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
package com.dettonville.api.pipeline.utils

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import com.dettonville.api.pipeline.model.PatternMatchable
import com.dettonville.api.pipeline.utils.logging.Logger

import java.util.regex.Matcher

/**
 * Utility function to match incoming strings (scm urls) against a list of PatternMatchable objects.
 * Used to get necessary ManagedFile or Credential Objects for an URL (scm url)
 *
 * @see PatternMatchable
 * @see com.dettonville.api.pipeline.credentials.Credential
 * @see com.dettonville.api.pipeline.managedfiles.ManagedFile
 */
class PatternMatcher implements Serializable {

  com.dettonville.api.pipeline.utils.logging.Logger log = new com.dettonville.api.pipeline.utils.logging.Logger(this)

  /**
   * Returns the best match for the searchValue out of a list of PatternMatchable list.
   * As score the length of the match is used. The more characters match the better the score.
   *
   * @param searchValue The String to match against the patterns of the proviced items
   * @param items A list of PatternMatchable items in which the algorithm is searching for the best match
   * @return The match with the best score (length of match)
   */
  @NonCPS
  @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
  PatternMatchable getBestMatch(String searchValue, List<PatternMatchable> items) {
    log.debug("getBestPatternMatch '$searchValue'")
    PatternMatchable result = null
    int matchScore = 0
    // Walk through list and match each pattern of the PatternMatchable against the searchvalue
    items.each {
      item ->
        log.debug("try to match file: " + item + " with pattern " + item.getPattern())
        Matcher matcher = searchValue =~ item.getPattern()
        // check if there is a match
        if (matcher) {
          String group = matcher[0]
          // check if matcher has a group and if the matched length/score is better as the last found match
          if (group && (group.length() > matchScore)) {
            matchScore = group.length()
            log.trace("match found with score $matchScore")
            result = item
          }
        }
    }
    return result
  }

}
