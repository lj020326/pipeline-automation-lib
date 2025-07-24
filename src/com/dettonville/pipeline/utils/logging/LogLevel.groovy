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
package com.dettonville.pipeline.utils.logging

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings

/**
 * Enumeration for log levels
 */
@SuppressFBWarnings('ME_ENUM_FIELD_SETTER')
enum LogLevel implements Serializable {

  ALL(0, 0),
  TRACE(2, 8),
  DEBUG(3, 12),
  INFO(4, 0),
  DEPRECATED(5, 93),
  WARN(6, 202),
  ERROR(7, 5),
  FATAL(8, 9),
  NONE(Integer.MAX_VALUE, 0)

  Integer level

  static COLOR_CODE_PREFIX = "1;38;5;"

  Integer color

  private static final long serialVersionUID = 1L

  LogLevel(Integer level, Integer color) {
    this.level = level
    this.color = color
  }

  @NonCPS
  static LogLevel fromInteger(Integer value) {
    for (lvl in values()) {
      if (lvl.getLevel() == value) return lvl
    }
    return INFO
  }

  @NonCPS
  static LogLevel fromString(String value) {
    for (lvl in values()) {
      if (lvl.toString().equalsIgnoreCase(value)) return lvl
    }
    return INFO
  }

  @NonCPS
  public String getColorCode() {
    return COLOR_CODE_PREFIX + color.toString()
  }


}
