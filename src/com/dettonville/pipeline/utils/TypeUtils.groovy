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
package com.dettonville.pipeline.utils

import com.cloudbees.groovy.cps.NonCPS
import com.dettonville.pipeline.versioning.ComparableVersion

/**
 * Utility class for detecting type of variables since instanceof is forbidden in groovy pipeline sandbox.
 * This utiltiy uses simple methods with type overloading to simply return true or false.
 */
class TypeUtils implements Serializable {

  private static final long serialVersionUID = 1L

  /**
   * Utility function to return false for all non Map objects
   *
   * @param object Any other object that is not of type Map
   * @return false
   */
  @NonCPS
  Boolean isMap(Object object) {
    return false
  }

  /**
   * Utility function to return true for all Map objects
   *
   * @param object Map object
   * @return true
   */
  @NonCPS
  Boolean isMap(Map object) {
    return true
  }

  /**
   * Utility function to return false for all non List objects
   *
   * @param object Any other object that is not of type List
   * @return false
   */
  @NonCPS
  Boolean isList(Object object) {
    return false
  }

  /**
   * Utility function to return true for all List objects
   *
   * @param object List object
   * @return true
   */
  @NonCPS
  Boolean isList(List object) {
    return true
  }

  /**
   * Utility function to return false for all non ListItem objects
   *
   * @param object Comparable Version object
   * @return true
   */
  @NonCPS
  Boolean isComparableVersion(com.dettonville.pipeline.versioning.ComparableVersion object) {
    return true
  }

  /**
   * Utility function to return false for all non ComparableVersion objects
   *
   * @param object Any other object that is not of type ComparableVersion
   * @return false
   */
  @NonCPS
  Boolean isComparableVersion(Object object) {
    return false
  }
}
