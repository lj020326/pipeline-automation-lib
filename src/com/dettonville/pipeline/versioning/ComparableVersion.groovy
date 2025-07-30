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
package com.dettonville.pipeline.versioning

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import com.dettonville.pipeline.utils.TypeUtils
import com.dettonville.pipeline.utils.logging.Logger

/**
 * Jenkins groovy sandbox compatible version of
 * https://github.com/apache/maven/blob/master/maven-artifact/src/main/java/org/apache/maven/artifact/versioning/ComparableVersion.java
 */
class ComparableVersion implements Comparable<ComparableVersion>, Serializable {

  public String value

  public ListItem items

  public String canonical

  private static final long serialVersionUID = 1L

  Logger log = new Logger(this)

  ComparableVersion(String version) {
    log.trace("Constructor")
    parseVersion(version)
  }

  @Override
  @NonCPS
  int compareTo(ComparableVersion comparableVersion) {
    log.trace("compareTo")
    return items.compareTo(comparableVersion.items)
  }

  @NonCPS
  void parseVersion(String version) {
    log.trace("parseVersion '$version'")
    this.value = version

    log.trace("parseVersion pos 1")
    items = new ListItem()
    version = version.toLowerCase()

    ListItem list = items

    List stack = []
    stack.push(list)

    log.trace("parseVersion pos 2")

    boolean isDigit = false
    int startIndex = 0

    for (int i = 0; i < version.length(); i++) {
      char c = version.charAt(i)

      if (c == '.') {
        log.trace("parseVersion pos 2.1")
        if (i == startIndex) {
          list.add(IntegerItem.ZERO)
        } else {
          list.add(parseItem(isDigit, version.substring(startIndex, i)))
        }
        startIndex = i + 1
      } else if (c == '-') {
        log.trace("parseVersion pos 2.2")
        if (i == startIndex) {
          list.add(IntegerItem.ZERO)
        } else {
          list.add(parseItem(isDigit, version.substring(startIndex, i)))
        }
        startIndex = i + 1

        list.add(list = new ListItem())
        stack.push(list)
      } else if (c =~ '^\\d$') {
        log.trace("parseVersion pos 2.3")
        if (!isDigit && i > startIndex) {
          list.add(new StringItem(version.substring(startIndex, i), true))
          startIndex = i

          list.add(list = new ListItem())
          stack.push(list)
        }

        isDigit = true
      } else {
        if (isDigit && i > startIndex) {
          log.trace("parseVersion pos 2.4")
          list.add(parseItem(true, version.substring(startIndex, i)))
          startIndex = i

          list.add(list = new ListItem())
          stack.push(list)
        }

        isDigit = false
      }
    }

    log.trace("parseVersion pos 3")

    if (version.length() > startIndex) {
      list.add(parseItem(isDigit, version.substring(startIndex)))
    }

    log.trace("parseVersion pos 4")
    for (Integer i = stack.size() - 1; i >= 0; i--) {
      list = (ListItem) stack[i]
      list.normalize()
    }

    log.trace("parseVersion pos 5")
    canonical = items.toString()
  }

  @NonCPS
  Item parseItem(boolean isDigit, String buf) {
    return isDigit ? new IntegerItem(buf) : new StringItem(buf, false)
  }

  @Override
  @SuppressFBWarnings("EQ_UNUSUAL")
  @NonCPS
  boolean equals(Object o) {
    TypeUtils typeUtils = new TypeUtils()
    return (typeUtils.isComparableVersion(o)) && canonical.equals(((ComparableVersion) o).canonical)
  }

  @Override
  @NonCPS
  int hashCode() {
    return canonical.hashCode()
  }

  @Override
  @NonCPS
  String toString() {
    return value
  }

  @NonCPS
  String getCanonical() {
    return canonical
  }

}
