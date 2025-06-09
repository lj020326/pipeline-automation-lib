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
package com.dettonville.api.pipeline.versioning

import com.cloudbees.groovy.cps.NonCPS

/**
 * Jenkins groovy sandbox compatible version of
 * https://github.com/apache/maven/blob/master/maven-artifact/src/main/java/org/apache/maven/artifact/versioning/ComparableVersion.java / IntegerItem
 */
class IntegerItem implements Item, Serializable {

  private static final long serialVersionUID = 1L

  public Integer value

  public static final Integer INTEGER_ZER0 = 0

  public static final IntegerItem ZERO = new IntegerItem()

  IntegerItem() {
    value = INTEGER_ZER0
  }

  IntegerItem(String str) {
    this.value = str.toInteger()
  }

  @Override
  @NonCPS
  int compareTo(Item item) {
    if (item == null) {
      return INTEGER_ZER0.equals(value) ? 0 : 1 // 1.0 == 1, 1.1 > 1
    }

    switch (item.getType()) {
      case INTEGER_ITEM:
        return value.compareTo(((IntegerItem) item).value)

      case STRING_ITEM:
        return 1 // 1.1 > 1-sp

      case LIST_ITEM:
        return 1 // 1.1 > 1-1

      default:
        throw new RuntimeException("invalid item: " + item.getClass())
    }
  }

  @Override
  @NonCPS
  int getType() {
    return INTEGER_ITEM
  }

  @Override
  @NonCPS
  boolean isNull() {
    return INTEGER_ZER0 == value
  }

  @NonCPS
  String toString() {
    return value.toString()
  }
}
