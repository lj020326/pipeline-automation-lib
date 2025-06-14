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
package com.dettonville.api.pipeline.utils.logging

import com.cloudbees.groovy.cps.NonCPS
import com.dettonville.api.pipeline.utils.ConfigConstants
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException
import org.jenkinsci.plugins.workflow.cps.DSL

import java.text.SimpleDateFormat

/**
 * Logging functionality for pipeline scripts.
 */
class StaticLogger implements Serializable {

  private static final long serialVersionUID = 1L

  /**
   * Reference to the dsl/script object
   */
  static DSL dsl

  /**
   * Reference to the CpsScript/WorkflowScript
   */
  static Script script

  /**
   * The log level
   */
  static LogLevel level = LogLevel.TRACE

  /**
   * The name of the logger
   */
  public static String name = ""

  /**
   * Flag if the logger is initialized
   */
  public static Boolean initialized = false

  /**
   * The log timestamp
   */
//  public static dateFormat = new SimpleDateFormat("HH:mm")
  public static SimpleDateFormat dateFormat = null

  /**
   * @param name The name of the logger
   */
  StaticLogger(String name = "") {
    this.name = name
  }

  /**
   * @param logScope The object the logger is for. The name of the logger is autodetected.
   */
  StaticLogger(Object logScope) {
    if (logScope instanceof Object) {
      this.name = getClassName(logScope)
      if (this.name == null) {
        this.name = "$logScope"
      }
    }
  }

  /**
   * @param logScope The object the logger is for. The name of the logger is autodetected.
   */
  StaticLogger(Object logScope, SimpleDateFormat dateFormat) {
    this(logScope)
    this.dateFormat = dateFormat
  }

  StaticLogger(Object logScope, LogLevel logLvl) {
    this(logScope)
    this.init(logScope, logLvl)
  }

  /**
   * Initializes the logger with DSL/steps object and LogLevel
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param logLvl The log level to use during execution of the pipeline script
   */
  @NonCPS
  static void init(DSL dsl, LogLevel logLvl = LogLevel.INFO) {
    if (logLvl == null) logLvl = LogLevel.INFO
    level = logLvl
    if (StaticLogger.initialized == true) {
      return
    }
    this.dsl = dsl
    initialized = true
    StaticLogger tmpLogger = new StaticLogger('StaticLogger')
    tmpLogger.deprecated('StaticLogger.init(DSL dsl, logLevel)','StaticLogger.init(Script script, logLevel)')
  }

  /**
   * Initializes the logger with DSL/steps object and configuration map
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param map The configuration object of the pipeline
   * @deprecated
   */
  @NonCPS
  static void init(DSL dsl, Map map) {
    LogLevel lvl
    if (map) {
      lvl = map[ConfigConstants.LOGLEVEL] ?: LogLevel.INFO
    } else {
      lvl = LogLevel.INFO
    }
    init(dsl, lvl)
  }

  /**
   * Initializes the logger with DSL/steps object and loglevel as string
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param sLevel the log level as string
   * @deprecated
   */
  @NonCPS
  static void init(DSL dsl, String sLevel) {
    if (sLevel == null) sLevel = LogLevel.INFO
    init(dsl, LogLevel.fromString(sLevel))
  }

  /**
   * Initializes the logger with DSL/steps object and loglevel as integer
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param iLevel the log level as integer
   *
   * @deprecated
   */
  @NonCPS
  static void init(DSL dsl, Integer iLevel) {
    if (iLevel == null) iLevel = LogLevel.INFO.getLevel()
    init(dsl, LogLevel.fromInteger(iLevel))
  }

  /**
   * Initializes the logger with CpsScript object and LogLevel
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   * @param map The configuration object of the pipeline
   */
  @NonCPS
  static void init(Script script, LogLevel logLvl = LogLevel.INFO) {
    if (logLvl == null) logLvl = LogLevel.INFO
    level = logLvl
    if (StaticLogger.initialized == true) {
      return
    }
    this.script = script
    this.dsl = (DSL) script.steps
    initialized = true
  }

  /**
   * Initializes the logger with CpsScript object and configuration map
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   * @param map The configuration object of the pipeline
   */
  @NonCPS
  static void init(Script script, Map map) {
    LogLevel lvl
    if (map) {
      lvl = map[ConfigConstants.LOGLEVEL] ?: LogLevel.INFO
    } else {
      lvl = LogLevel.INFO
    }
    init(script, lvl)
  }

  /**
   * Initializes the logger with CpsScript object and loglevel as string
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   * @param sLevel the log level as string
   */
  @NonCPS
  static void init(Script script, String sLevel) {
    if (sLevel == null) sLevel = LogLevel.INFO
    init(script, LogLevel.fromString(sLevel))
  }

  /**
   * Initializes the logger with DSL/steps object and loglevel as integer
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   * @param iLevel the log level as integer
   *
   */
  @NonCPS
  static void init(Script script, Integer iLevel) {
    if (iLevel == null) iLevel = LogLevel.INFO.getLevel()
    init(script, LogLevel.fromInteger(iLevel))
  }

  /**
   * Set logger level with loglevel
   *
   * @param logLvl The log level to use during execution of the pipeline script
   *
   */
  @NonCPS
  static void setLevel(LogLevel logLvl = LogLevel.INFO) {
    if (logLvl == null) logLvl = LogLevel.INFO
    level = logLvl
  }

  /**
   * Set logger with loglevel as string
   *
   * @param sLevel the log level as string
   */
  @NonCPS
  static void setLevel(String sLevel) {
    if (sLevel == null) sLevel = LogLevel.INFO
    setLevel(LogLevel.fromString(sLevel))
  }

  /**
   * Set logger with loglevel as integer
   *
   * @param iLevel the log level as integer
   *
   */
  @NonCPS
  static void setLevel(Integer iLevel) {
    if (iLevel == null) iLevel = LogLevel.INFO.getLevel()
    setLevel(LogLevel.fromInteger(iLevel))
  }

  /**
   * Logs a trace message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void trace(String message, Object object) {
    log(LogLevel.TRACE, message, object)
  }

  /**
   * Logs a info message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void info(String message, Object object) {
    log(LogLevel.INFO, message, object)
  }

  /**
   * Logs a debug message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void debug(String message, Object object) {
    log(LogLevel.DEBUG, message, object)
  }

  /**
   * Logs warn message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void warn(String message, Object object) {
    log(LogLevel.WARN, message, object)
  }

  /**
   * Logs a error message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void error(String message, Object object) {
    log(LogLevel.ERROR, message, object)
  }

  /**
   * Logs a fatal message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void fatal(String message, Object object) {
    log(LogLevel.FATAL, message, object)
  }

  /**
   * Logs a trace message
   *
   * @param message The message to be logged
   */
  @NonCPS
  static void trace(String message) {
    log(LogLevel.TRACE, message)
  }

  /**
   * Logs a trace message
   *
   * @param message The message to be logged
   */
  @NonCPS
  static void info(String message) {
    log(LogLevel.INFO, message)
  }

  /**
   * Logs a debug message
   *
   * @param message The message to be logged
   */
  @NonCPS
  static void debug(String message) {
    log(LogLevel.DEBUG, message)
  }

  /**
   * Logs a warn message
   *
   * @param message The message to be logged
   */
  @NonCPS
  static void warn(String message) {
    log(LogLevel.WARN, message)
  }

  /**
   * Logs a error message
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void error(String message) {
    log(LogLevel.ERROR, message)
  }

  /**
   * Logs a deprecation message
   *
   * @param message The message to be logged
   */
  @NonCPS
  static void deprecated(String message) {
    try {
      StaticLogger.dsl.addWarningBadge(message)
    } catch (Exception ex) {
      // no badge plugin available
    }
    log(LogLevel.DEPRECATED, message)
  }

  /**
   * Logs a deprecation message with deprecated and replacement
   *
   * @param deprecatedItem The item that is depcrecated
   * @param newItem The replacement (if exist)
   */
  @NonCPS
  static void deprecated(String deprecatedItem, String newItem) {
    String message = "The step/function/class '$deprecatedItem' is marked as deprecated and will be removed in future releases. " +
      "Please use '$newItem' instead."
    deprecated(message)
  }

  /**
   * Logs a fatal message
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void fatal(String message) {
    log(LogLevel.FATAL, message)
  }

  /**
   * Helper function for logging/dumping a object at the given log level
   *
   * @param logLevel the loglevel to be used
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  static void log(LogLevel logLevel, String message, Object object) {
    if (doLog(logLevel)) {
      String objectName = getClassName(object)
      if (objectName != null) {
        objectName = "($objectName) "
      } else {
        objectName = ""
      }

      String objectString = object.toString()
      String functionName = getInvokingFunctionName()

      String msg = "$name : $message -> $objectName$objectString"
      if (functionName != null) {
        msg = "$name.$functionName : $message -> $objectName$objectString"
      }
      writeLogMsg(logLevel, msg)
    }
  }

  /**
   * Helper function for logging at the given log level
   *
   * @param logLevel the loglevel to be used
   * @param message The message to be logged
   */
  @NonCPS
  static void log(LogLevel logLevel, String message) {
    if (doLog(logLevel)) {
      String msg = "$name : $message"
      writeLogMsg(logLevel, msg)
    }
  }

  /**
   * Utility function for writing to the jenkins console
   *
   * @param logLevel the loglevel to be used
   * @param msg The message to be logged
   */
  @NonCPS
  private static void writeLogMsg(LogLevel logLevel, String msg) {
    String lvlString = "[${logLevel.toString()}]"
    if (this.dateFormat!=null) {
      def date = new Date()
      def logtime = dateFormat.format(date)

      lvlString = "[${logtime}][${logLevel.toString()}]"
    }

    lvlString = wrapColor(logLevel.getColorCode(), lvlString)

    if (dsl != null) {
      dsl.echo("$lvlString $msg")
    }
  }

  /**
   * Wraps a string with color codes when terminal is available
   * @param logLevel
   * @param str
   * @return
   */
  @NonCPS
  private static String wrapColor(String colorCode, String str) {
    String ret = str
    if (hasTermEnv()) {
      ret = "\u001B[${colorCode}m${str}\u001B[0m"
    }
    return ret
  }

  /**
   * Helper function to detect if a term environment is available
   * @return
   */
  @NonCPS
  private static Boolean hasTermEnv() {
    String termEnv = null
    if (script != null) {
      try {
        termEnv = script.env.TERM
      } catch (Exception ex) {

      }
    }
    return termEnv != null
  }

  /**
   * Utiltiy function to determine if the given logLevel is active
   *
   * @param logLevel
   * @return true , when the loglevel should be displayed, false when the loglevel is disabled
   */
  @NonCPS
  private static boolean doLog(LogLevel logLevel) {
    if (logLevel.getLevel() >= level.getLevel()) {
      return true
    }
    return false
  }

  /**
   * Helper function to get the name of the object
   * @param object
   * @return
   */
  @NonCPS
  public static String getClassName(Object object) {
    String objectName = null
    // try to retrieve as much information as possible about the class
    try {
        Class objectClass = object.getClass()
        objectName = objectClass.getName().toString()
        objectName = objectClass.getCanonicalName().toString()
    } catch (RejectedAccessException e) {
        // do nothing
    }

    return objectName
  }
}
