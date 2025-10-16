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
package com.dettonville.pipeline.utils.maps

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import com.dettonville.pipeline.utils.TypeUtils

import com.dettonville.pipeline.utils.logging.Logger
// import com.dettonville.pipeline.utils.logging.JenkinsLogger

/**
 * Utility functions for Map objects
 */
@SuppressWarnings("UnnecessaryQualifiedReference")
class MapUtils implements Serializable {

  private static final long serialVersionUID = 1L

  static Logger log = new Logger(this)
//   static JenkinsLogger log = new JenkinsLogger(this)

  static typeUtils = new TypeUtils()

  /**
   * Merges 0 to n Map objects recursively into one Map
   *
   * Overlapping keys will be overwritten by N+1 values.
   * E.g.
   *  map[0] has "key" with "value"
   *  map[1] has "key" with "newValue"
   *
   *  Resulting will have "key" with "newValue"
   *
   * @param maps 0 to n maps that have to me merged.
   * @return The merged map
   */
  @NonCPS
  @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
  static transient Map merge(Map... maps) {
    Map result

    if (maps.length == 0) {
      result = [:]
    } else if (maps.length == 1) {
      result = maps[0]
    } else {
      result = [:]
      maps.each { map ->
        map.each { k, v ->
          log.trace("result[k]: ", result[k])
          log.trace("v: ", v)
          /*log.trace("isList result[k]: ", TypeUtils.isList(result[k]))
          log.trace("isList v: ", TypeUtils.isList(v))*/
          if (result[k] != null && typeUtils.isMap(result[k])) {
            // unnecessary qualified reference is necessary here otherwise CPS / Sandbox will be violated
            result[k] = MapUtils.merge((Map) result[k], (Map) v)
          } else if (result[k] != null && typeUtils.isList(result[k]) && typeUtils.isList(v)) {
            // execute a list merge
            List list1 = (List) result[k]
            List list2 = (List) v

            for (Object list2Item : list2) {
              if (!list1.contains(list2Item))
                list1.add(list2Item)
            }
            result[k] = list1
          } else {
            result[k] = v
          }
        }
      }
    }

    result
  }


}
