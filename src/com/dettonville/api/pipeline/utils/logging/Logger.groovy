/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.dettonville.api.pipeline.utils.logging

import com.dettonville.api.pipeline.utils.ConfigConstants

import com.cloudbees.groovy.cps.NonCPS
import org.codehaus.groovy.runtime.StackTraceUtils
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException
import org.jenkinsci.plugins.workflow.cps.DSL

import java.text.SimpleDateFormat

/**
 * Logging functionality for pipeline scripts.
 */
class Logger implements Serializable {

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
  public static LogLevel level = LogLevel.TRACE

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
  Logger(String name = "") {
    this.name = name
  }

  /**
   * @param logScope The object the logger is for. The name of the logger is autodetected.
   */
  Logger(Object logScope) {
    if (logScope instanceof Script) {
        init(logScope)
    } else if (logScope instanceof Object) {
      this.name = getClassName(logScope)
      if (this.name == null) {
        this.name = "$logScope"
      }
    }
  }

  /**
   * @param logScope The object the logger is for. The name of the logger is autodetected.
   */
  Logger(Object logScope, SimpleDateFormat dateFormat) {
    this(logScope)
    this.dateFormat = dateFormat
  }

  Logger(Object logScope, LogLevel logLvl) {
//     this(logScope)
//     setLevel(logLvl)
     init(logScope, logLvl)
  }

  /**
   * Initializes the logger with DSL/steps object
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   */
  @NonCPS
  static void init(DSL dsl) {
    if (Logger.initialized == true) {
      return
    }
    this.dsl = dsl
    this.initialized = true
    Logger tmpLogger = new Logger('Logger')
    tmpLogger.deprecated('Logger.init(DSL dsl, logLevel)','Logger.init(Script script, logLevel)')
  }

  /**
   * Initializes the logger with DSL/steps object and LogLevel
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param logLvl The log level to use during execution of the pipeline script
   */
  @NonCPS
  static void init(DSL dsl, LogLevel logLvl) {
    init(dsl)
    if (logLvl == null) logLvl = LogLevel.INFO
    level = logLvl
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
   * Initializes the logger with CpsScript object
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   */
  @NonCPS
//   static void init(Script script) {
  static void init(Object logScope) {
    if (Logger.initialized == true) {
      return
    }
    if (logScope instanceof Object) {
      this.name = getClassName(logScope)
      if (this.name == null) {
        this.name = "${logScope}"
      }
    }
    if (logScope instanceof Script) {
        this.script = logScope
        this.dsl = (DSL) logScope.steps
    }
    this.initialized = true
  }

  /**
   * Initializes the logger with CpsScript object and LogLevel
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   * @param logLvl The log level of the logger
   */
  @NonCPS
  static void init(Script script, LogLevel logLvl) {
    init(script)
    if (logLvl == null) logLvl = LogLevel.INFO
    this.level = logLvl
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
//     if (logLvl == null) logLvl = LogLevel.INFO
    this.level = logLvl
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
  void trace(String message, Object object) {
    log(LogLevel.TRACE, message, object)
  }

  /**
   * Logs a info message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
//  @NonCPS
  void info(String message, Object object) {
    log(LogLevel.INFO, message, object)
  }

  /**
   * Logs a debug message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void debug(String message, Object object) {
    log(LogLevel.DEBUG, message, object)
  }

  /**
   * Logs warn message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void warn(String message, Object object) {
    log(LogLevel.WARN, message, object)
  }

  /**
   * Logs a error message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void error(String message, Object object) {
    log(LogLevel.ERROR, message, object)
  }

  /**
   * Logs a fatal message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void fatal(String message, Object object) {
    log(LogLevel.FATAL, message, object)
  }

  /**
   * Logs a trace message
   *
   * @param message The message to be logged
   */
  void trace(String message) {
    log(LogLevel.TRACE, message)
  }

  /**
   * Logs a trace message
   *
   * @param message The message to be logged
   */
  void info(String message) {
    log(LogLevel.INFO, message)
  }

  /**
   * Logs a debug message
   *
   * @param message The message to be logged
   */
  void debug(String message) {
    log(LogLevel.DEBUG, message)
  }

  /**
   * Logs a warn message
   *
   * @param message The message to be logged
   */
  void warn(String message) {
    log(LogLevel.WARN, message)
  }

  /**
   * Logs a error message
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void error(String message) {
    log(LogLevel.ERROR, message)
  }

  /**
   * Logs a deprecation message
   *
   * @param message The message to be logged
   */
  void deprecated(String message) {
    try {
      Logger.dsl.addWarningBadge(message)
    } catch (Exception ex) {
      // no badge plugin available
    }
    log(LogLevel.DEPRECATED, message)
  }

  /**
   * Logs a deprecation message with deprecated and replacement
   *
   * @param deprecatedItem The item that is deprecated
   * @param newItem The replacement (if exist)
   */
  void deprecated(String deprecatedItem, String newItem) {
    String message = "The step/function/class '$deprecatedItem' is marked as depecreated and will be removed in future releases. " +
      "Please use '$newItem' instead."
    deprecated(message)
  }

  /**
   * Logs a fatal message
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void fatal(String message) {
    log(LogLevel.FATAL, message)
  }

  /**
   * Helper function for logging/dumping a object at the given log level
   *
   * @param logLevel the loglevel to be used
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void log(LogLevel logLevel, String message, Object object) {
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
        msg = "$name.$functionName(): $message -> $objectName$objectString"
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
  void log(LogLevel logLevel, String message) {
    if (doLog(logLevel)) {
      String msg = "$name : $message"
      String functionName = getInvokingFunctionName()
//       dsl.echo("*** functionName="+functionName)

      if (functionName != null) {
        msg = "$name.$functionName(): $message"
      }
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

  // Returns the method name from the java fully qualified class name
  public static String extractMethodName(String className) {
    // Match $methodName($ or end)
    java.util.regex.Matcher m = java.util.regex.Pattern.compile('\\$(\\w+?)(\\$|$)').matcher(className);
    if (m.find()) {
        return m.group(1);
    }
    return null;
  }

  // Returns the name of the invoking method (the caller of this method)
  public static String getInvokingFunctionName() {
      def marker = new Throwable()
      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace

      boolean foundLogger = false

      for (StackTraceElement element : stackTrace) {
          String className = element.getClassName()
          // Skip internal Groovy/Jenkins frames
          // First match is the logger itself, so skip it
//               if (!foundLogger && className == Logger.class.getName()) {
          if (className.startsWith(Logger.class.getName())) {
              foundLogger = true
              continue
          }
          // The next match is the true invoker
          if (foundLogger) {
              // Optionally log for debugging
//               dsl.echo("Enclosing className="+className)
//               dsl.echo("Enclosing methodName="+element.getMethodName())
              return element.getMethodName()
          }
      }
      return null
  }

  // Returns the name of the invoking method (the caller of this method)
  public static String getInvokingFunctionName1() {
//       StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      def marker = new Throwable()
      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace

      boolean foundLogger = false

      for (StackTraceElement element : stackTrace) {
          String className = element.getClassName()
          // Skip internal Groovy/Jenkins frames
          if (
              !className.startsWith("java.") &&
              !className.startsWith("jdk.") &&
              !className.startsWith("sun.") &&
              !className.startsWith("groovy.") &&
              !className.startsWith("hudson.") &&
              !className.startsWith("jenkins.") &&
              !className.startsWith("com.cloudbees.") &&
              !className.startsWith("org.codehaus.groovy.") &&
              !className.startsWith("org.kohsuke.groovy.") &&
              !className.startsWith("org.jenkinsci.")
          ) {
              // First match is the logger itself, so skip it
//               if (!foundLogger && className == Logger.class.getName()) {
              if (className.startsWith(Logger.class.getName())) {
                  foundLogger = true
                  continue
              }
              // The next match is the true invoker
              if (foundLogger) {
                  // Optionally log for debugging
//                   dsl.echo("Enclosing className="+className)
//                   dsl.echo("Enclosing methodName="+element.getMethodName())
                  return element.getMethodName()
              }
          }
      }
      return null
  }

  // ref: https://stackoverflow.com/questions/9540678/groovy-get-enclosing-functions-name
  public static String getInvokingFunctionName2() {

      def marker = new Throwable()
      int enclosingFunctionStackTraceFrameLevel = 3
      String methodName = StackTraceUtils.sanitize(marker).stackTrace[enclosingFunctionStackTraceFrameLevel].methodName
//       dsl.echo("Enclosing functionName="+methodName)
      return methodName
  }

  // ref: https://stackoverflow.com/a/5891326
  public static String getInvokingFunctionName3() {

    String methodName = new Object(){}.getClass().getEnclosingMethod().getName()
//     dsl.echo("methodName="+methodName)

    return methodName
  }

  // Returns the name of the invoking method (the caller of this method)
  private static String getInvokingFunctionName4() {
      // [0] is Thread.getStackTrace, [1] is getInvokingFunctionName, [2] is the caller
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      if (stackTrace.length > 2) {
          return stackTrace[2].getMethodName();
      } else {
          return null; // or throw an exception if desired
      }
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
