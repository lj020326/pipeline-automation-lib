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
   * Flag set when logger debugging is enabled
   */
  private Boolean enableDebug = false

  /**
   * Reference to the dsl/script object
   */
  private static DSL dsl

  /**
   * Reference to the CpsScript/WorkflowScript
   */
  private static Script script

  /**
   * The log level
   */
//   public LogLevel level = LogLevel.TRACE
  public LogLevel level = LogLevel.INFO

  /**
   * The name of the logger
   */
  public String scopeName = ""

  /**
   * Flag if the logger is initialized
   */
  public static Boolean initialized = false

  /**
   * The log timestamp
   */
//  public dateFormat = new SimpleDateFormat("HH:mm")
  private SimpleDateFormat dateFormat = null

  Logger() {
    String enclosingClassName = getEnclosingClassName()
    init(enclosingClassName)
  }

  /**
   * @param name The name of the logger
   */
  Logger(String scopeName) {
    init(logScope)
  }

  /**
   * @param logScope The object the logger is for. The name of the logger is autodetected.
   */
  Logger(Object logScope) {
    init(logScope)
  }

  /**
   * @param logScope The object the logger is for. The name of the logger is autodetected.
   */
  Logger(Object logScope, SimpleDateFormat dateFormat) {
    init(logScope)
    this.dateFormat = dateFormat
  }

  Logger(Object logScope, LogLevel logLvl) {
     init(logScope, logLvl)
  }

  /**
   * Initializes the logger with DSL/steps object
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   */
  @NonCPS
  void init(DSL dsl) {
    if (this.initialized == true) {
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
  void init(DSL dsl, LogLevel logLvl) {
    init(dsl)
    if (logLvl == null) logLvl = LogLevel.INFO
    this.level = logLvl
  }

  /**
   * Initializes the logger with DSL/steps object and configuration map
   *
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param map The configuration object of the pipeline
   * @deprecated
   */
  @NonCPS
  void init(DSL dsl, Map map) {
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
  void init(DSL dsl, String sLevel) {
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
  void init(DSL dsl, Integer iLevel) {
    if (iLevel == null) iLevel = LogLevel.INFO.getLevel()
    init(dsl, LogLevel.fromInteger(iLevel))
  }

  /**
   * Initializes the logger with CpsScript object
   *
   * @param script CpsScript object of the current pipeline script (available via this in pipeline scripts)
   */
  @NonCPS
  void init(Object logScope) {
//     if (this.initialized == true) {
//       return
//     }
    if (logScope instanceof Script) {
        this.script = logScope
        this.dsl = (DSL) logScope.steps
    }
    if (logScope instanceof Object) {
      this.scopeName = getClassName(logScope)
      if (this.scopeName == null) {
        this.scopeName = "${logScope}"
      }
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
  void init(Script script, LogLevel logLvl) {
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
  void init(Script script, Map map) {
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
  void init(Script script, String sLevel) {
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
  void init(Script script, Integer iLevel) {
    if (iLevel == null) iLevel = LogLevel.INFO.getLevel()
    init(script, LogLevel.fromInteger(iLevel))
  }

  /**
   * Enable logger debugging
   */
  void enableDebug() {
    this.enableDebug = true
    this.dsl.echo("this.enableDebug=" + this.enableDebug)
  }

  /**
   * Disable logger debugging
   */
  void disableDebug() {
    this.enableDebug = false
    this.dsl.echo("this.enableDebug=" + this.enableDebug)
  }

  /**
   * Set logger level with loglevel
   *
   * @param logLvl The log level to use during execution of the pipeline script
   *
   */
  @NonCPS
  void setLevel(LogLevel logLvl) {
    if (logLvl == null) logLvl = LogLevel.INFO
    this.level = logLvl
  }

  /**
   * Set logger with loglevel as string
   *
   * @param sLevel the log level as string
   */
  @NonCPS
  void setLevel(String sLevel) {
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
  void setLevel(Integer iLevel) {
    if (iLevel == null) iLevel = LogLevel.INFO.getLevel()
    setLevel(LogLevel.fromInteger(iLevel))
  }

  /**
   * Logs messages for debugging the Logger
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  @NonCPS
  private void loggerDebug(String message) {
    if (this.enableDebug) {
        if (this.dsl) {
            this.dsl.echo("Logger[DEBUG]: " + message)
        }
    }
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
      String functionName = getEnclosingFunctionName()

      String msg = "${scopeName} : ${message} -> ${objectName}${objectString}"
      if (functionName != null) {
        msg = "${scopeName}.${functionName}(): ${message} -> ${objectName}${objectString}"
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
      String msg = "${scopeName} : ${message}"
      String functionName = getEnclosingFunctionName()

      if (functionName != null) {
        msg = "${scopeName}.${functionName}(): ${message}"
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
  private void writeLogMsg(LogLevel logLevel, String msg) {
    String lvlString = "[${logLevel.toString()}]"
    if (this.dateFormat!=null) {
      def date = new Date()
      def logtime = dateFormat.format(date)

      lvlString = "[${logtime}][${logLevel.toString()}]"
    }

    lvlString = wrapColor(logLevel.getColorCode(), lvlString)
    this.dsl.echo("$lvlString $msg")
  }

  /**
   * Wraps a string with color codes when terminal is available
   * @param logLevel
   * @param str
   * @return
   */
  private String wrapColor(String colorCode, String str) {
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
  private Boolean hasTermEnv() {
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
   * Utility function to determine if the given logLevel is active
   *
   * @param logLevel
   * @return true , when the loglevel should be displayed, false when the loglevel is disabled
   */
  private boolean doLog(LogLevel logLevel) {
    if (logLevel.getLevel() >= level.getLevel()) {
      return true
    }
    return false
  }

  // Returns the method name from the java fully qualified class name
  public String extractMethodName(String className) {
    // Match $methodName($ or end)
    java.util.regex.Matcher m = java.util.regex.Pattern.compile('\\$(\\w+?)(\\$|$)').matcher(className);
    if (m.find()) {
        return m.group(1);
    }
    return null;
  }

  // ref: https://stackoverflow.com/questions/9540678/groovy-get-enclosing-functions-name
  // ref: https://stackoverflow.com/questions/11414782/how-to-check-if-a-java-class-is-part-of-the-current-stack-trace
  public String getEnclosingFunctionName() {

      def marker = new Throwable()
      int enclosingFunctionStackTraceFrameLevel = 3

      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace
      String methodName

      if (stackTrace.length > enclosingFunctionStackTraceFrameLevel) {
          StackTraceElement ste = stackTrace[enclosingFunctionStackTraceFrameLevel]
          String className = ste.getClassName()
          methodName = ste.getMethodName()
          loggerDebug("*** Enclosing className=" + className)
          loggerDebug("*** *Enclosing methodName=" + methodName)
          if (className != null) {
              try {
                  // ref: https://stackoverflow.com/a/54521730
                  String simpleClassName = Class.forName(className).getSimpleName();
                  loggerDebug("*** Enclosing simpleClassName="+simpleClassName)
                  if (!simpleClassName.equals(this.scopeName)) {
                      methodName = simpleClassName + "." + methodName
                  }
              } catch (java.lang.ClassNotFoundException ex) {
                  if (!className.equals(this.scopeName)) {
                      methodName = className + "." + methodName
                  }
              }
          }
      }
      loggerDebug("*** Enclosing methodName="+methodName)
      return methodName
  }

  // Returns the name of the invoking method (the caller of this method)
  public String getEnclosingFunctionName1() {
      String className = null
      String methodName = null
      def marker = new Throwable()
      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace

      boolean foundLogger = false

      for (StackTraceElement element : stackTrace) {
          className = element.getClassName()
          // Skip internal Groovy/Jenkins frames
          // First match is the logger itself, so skip it
//               if (!foundLogger && className == Logger.class.getName()) {
          if (className.startsWith(Logger.class.getName())) {
              foundLogger = true
              continue
          }
          // The next match is the true invoker
          if (foundLogger) {
              methodName = element.getMethodName()
          }
      }
      // Optionally log for debugging
      loggerDebug("*** Enclosing className="+className)
      loggerDebug("*** Enclosing methodName="+methodName)
      return methodName
  }

  // Returns the name of the invoking method (the caller of this method)
  public String getEnclosingFunctionName2() {
      String className = null
      String methodName = null

//       StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      def marker = new Throwable()
      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace

      boolean foundLogger = false

      for (StackTraceElement element : stackTrace) {
          className = element.getClassName()
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
                  methodName = element.getMethodName()
              }
          }
      }
      // Optionally log for debugging
      loggerDebug("*** Enclosing className="+className)
      loggerDebug("*** *Enclosing methodName="+methodName)
      return null
  }

  // ref: https://stackoverflow.com/a/5891326
  public String getEnclosingFunctionName3() {

    String methodName = new Object(){}.getClass().getEnclosingMethod().getName()
    loggerDebug("*** Enclosing methodName="+methodName)

    return methodName
  }

  // Returns the name of the invoking method (the caller of this method)
  private String getEnclosingFunctionName4() {
      // [0] is Thread.getStackTrace, [1] is getEnclosingFunctionName, [2] is the caller
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
  public String getClassName(Object object) {
    String className = null
    // try to retrieve as much information as possible about the class
    try {
        Class objectClass = object.getClass()
        className = objectClass.getName().toString()
        className = objectClass.getCanonicalName().toString()
    } catch (RejectedAccessException e) {
        // do nothing
    }
    loggerDebug("* Enclosing className="+className)

    return className
  }

  // ref: https://stackoverflow.com/questions/9540678/groovy-get-enclosing-functions-name
  // ref: https://stackoverflow.com/questions/11414782/how-to-check-if-a-java-class-is-part-of-the-current-stack-trace
//   @NonCPS
  public String getEnclosingClassName() {
      String className = null
      def marker = new Throwable()
      int enclosingFunctionStackTraceFrameLevel = 2

      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace
      String methodName

      if (stackTrace.length > enclosingFunctionStackTraceFrameLevel) {
          StackTraceElement ste = stackTrace[enclosingFunctionStackTraceFrameLevel]
          className = ste.getClassName()
      }
      loggerDebug("*** Enclosing className=" + className)
      return className
  }

}
