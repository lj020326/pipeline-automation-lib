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
package com.dettonville.pipeline.utils.logging

import com.dettonville.pipeline.utils.ConfigConstants

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
//   public LogLevel level = LogLevel.INFO
  public static LogLevel level = LogLevel.INFO

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

  // --- Constructors (all calling the new unified init method) ---

  Logger() {
    // Default constructor will auto-detect scope name from call stack if 'this' (script) is passed in later context.
    // However, for consistency, we expect a context to be passed to init.
    // If this constructor is used, init will need to be called explicitly later with context.
    // For a Jenkinsfile, this constructor is unlikely to be used alone.
  }

  /**
   * @param name The name of the logger
   */
  Logger(String scopeName) {
    init(scopeName: scopeName)
  }

  /**
   * @param context The object the logger is for.
   * The name of the logger is autodetected.
   */
  Logger(Object context) {
    init(context: context)
  }

  /**
   * @param context The object the logger is for.
   * The name of the logger is autodetected.
   */
  Logger(Object context, SimpleDateFormat dateFormat) {
    init(context: context, dateFormat: dateFormat)
  }

  Logger(Object context, LogLevel logLvl) {
     init(context: context, logLevel: logLvl)
  }

  /**
   * Initializes the logger with a DSL object and a scope name.
   * This is the preferred constructor for running without full script context.
   * @param dsl The DSL object for performing echo operations.
   * @param scopeName The name of the logger.
   */
  Logger(DSL dsl, String scopeName) {
    init(context: dsl, scopeName: scopeName)
  }

  /**
   * Initializes the logger with a DSL object, a scope name, and a log level.
   * @param dsl The DSL object for performing echo operations.
   * @param scopeName The name of the logger.
   * @param logLvl The initial log level.
   */
  Logger(DSL dsl, String scopeName, LogLevel logLvl) {
    init(context: dsl, scopeName: scopeName, logLevel: logLvl)
  }

  /**
   * Initializes the logger with a DSL object and a log level.
   * @param dsl The DSL object for performing echo operations.
   * @param logLvl The initial log level.
   */
  Logger(DSL dsl, LogLevel logLvl) {
    init(context: dsl, logLevel: logLvl)
  }

  // --- Unified init method ---

  /**
   * Unified initialization method for the Logger.
   *
   * @param args A map containing initialization arguments:
   * - `context`: (Optional) The context of the logger (WorkflowScript or DSL).
   * - `scopeName`: (Optional) The name of the logger.
   * - `logLevel`: (Optional) The initial log level (LogLevel enum or String/Integer convertible).
   * - `dateFormat`: (Optional) A SimpleDateFormat object for timestamps.
   * - `configMap`: (Optional) A map containing configuration (e.g., for logLevel).
   * @param context The object representing the execution context (e.g., `this` from Jenkinsfile, or a direct `DSL` object).
   */
  @NonCPS
  void init(Map args = [:]) {
    if (this.initialized) {
      return
    }

    if (args.containsKey('context') && args.context != null) {

        // Determine DSL and Script from context
        if (args.context instanceof DSL) {
          this.dsl = args.context
        } else if (args.context instanceof Script) {
            this.script = args.context
            try {
                this.dsl = (DSL) args.context.steps // This line still requires 'steps' to be available on Script
            } catch (MissingPropertyException e) {
                // Handle case where .steps is not yet available, e.g., if called too early in a Jenkinsfile
                System.err.println("Warning: 'steps' property not available on Script context during Logger initialization. " +
                                   "Echo operations may not work until DSL is available. Error: ${e.message}")
            }
        } else {
            System.err.println("Warning: Script context not available during Logger initialization.")
        }
    }

    // Set scopeName
    if (args.containsKey('scopeName') && args.scopeName != null) {
      this.scopeName = args.scopeName
    } else if (this.scopeName == "" && args.context != null) {
      // If scopeName wasn't explicitly provided, try to infer from context
      this.scopeName = getClassName(args.context) ?: "${args.context}"
    } else if (this.scopeName == "" && getEnclosingClassName() != null) {
      // As a fallback, try to get from the call stack
      this.scopeName = getEnclosingClassName()
    }

    // Set log level
    LogLevel desiredLevel = LogLevel.INFO
    if (args.containsKey('logLevel') && args.logLevel != null) {
      if (args.logLevel instanceof LogLevel) {
        desiredLevel = args.logLevel
      } else if (args.logLevel instanceof String) {
        desiredLevel = LogLevel.fromString(args.logLevel)
      } else if (args.logLevel instanceof Integer) {
        desiredLevel = LogLevel.fromInteger(args.logLevel)
      }
    } else if (args.containsKey('configMap') && args.configMap != null && args.configMap[ConfigConstants.LOGLEVEL] != null) {
      // Handle logLevel from configMap
      def configLvl = args.configMap[ConfigConstants.LOGLEVEL]
      if (configLvl instanceof LogLevel) {
        desiredLevel = configLvl
      } else if (configLvl instanceof String) {
        desiredLevel = LogLevel.fromString(configLvl)
      } else if (configLvl instanceof Integer) {
        desiredLevel = LogLevel.fromInteger(configLvl)
      }
    }
    this.level = desiredLevel

    // Set dateFormat
    if (args.containsKey('dateFormat') && args.dateFormat != null) {
      this.dateFormat = args.dateFormat
    }

    this.initialized = true
  }

  /**
   * Enable logger debugging
   */
  void enableDebug() {
    this.enableDebug = true
    if (this.dsl) {
      this.dsl.echo("this.enableDebug=" + this.enableDebug)
    }
  }

  /**
   * Disable logger debugging
   */
  void disableDebug() {
    this.enableDebug = false
    if (this.dsl) {
      this.dsl.echo("this.enableDebug=" + this.enableDebug)
    }
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
    if (sLevel == null) sLevel = LogLevel.INFO.toString()
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
    log(logLevel: LogLevel.TRACE, message: message, object: object)
  }

  /**
   * Logs a info message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void info(String message, Object object) {
    log(logLevel: LogLevel.INFO, message: message, object: object)
  }

  /**
   * Logs a debug message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void debug(String message, Object object) {
    log(logLevel: LogLevel.DEBUG, message: message, object: object)
  }

  /**
   * Logs warn message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void warn(String message, Object object) {
    log(logLevel: LogLevel.WARN, message: message, object: object)
  }

  /**
   * Logs a error message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void error(String message, Object object) {
    log(logLevel: LogLevel.ERROR, message: message, object: object)
  }

  /**
   * Logs a fatal message followed by object dump
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void fatal(String message, Object object) {
    log(logLevel: LogLevel.FATAL, message: message, object: object)
  }

  /**
   * Logs a trace message
   *
   * @param message The message to be logged
   */
  void trace(String message) {
    log(logLevel: LogLevel.TRACE, message: message)
  }

  /**
   * Logs a trace message
   *
   * @param message The message to be logged
   */
  void info(String message) {
    log(logLevel: LogLevel.INFO, message: message)
  }

  /**
   * Logs a debug message
   *
   * @param message The message to be logged
   */
  void debug(String message) {
    log(logLevel: LogLevel.DEBUG, message: message)
  }

  /**
   * Logs a warn message
   *
   * @param message The message to be logged
   */
  void warn(String message) {
    log(logLevel: LogLevel.WARN, message: message)
  }

  /**
   * Logs a error message
   *
   * @param message The message to be logged
   * @param object The object to be dumped
   */
  void error(String message) {
    log(logLevel: LogLevel.ERROR, message: message)
  }

  /**
   * Logs a deprecation message
   *
   * @param message The message to be logged
   */
  void deprecated(String message) {
    try {
      if (Logger.dsl) {
        Logger.dsl.addWarningBadge(message)
      }
    } catch (Exception ex) {
      // no badge plugin available or dsl not initialized
    }
    log(logLevel: LogLevel.DEPRECATED, message: message)
  }

  /**
   * Logs a deprecation message with deprecated and replacement
   *
   * @param deprecatedItem The item that is deprecated
   * @param newItem The replacement (if exist)
   */
  void deprecated(String deprecatedItem, String newItem) {
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
  void fatal(String message) {
    log(logLevel: LogLevel.FATAL, message: message)
  }

  // --- Unified log method ---

  /**
   * Unified logging method.
   *
   * @param args A map containing logging arguments:
   * - `logLevel`: (Required) The log level (LogLevel enum).
   * - `message`: (Required) The message to be logged.
   * - `object`: (Optional) The object to be dumped.
   */
  void log(Map args) {
    LogLevel logLevel = args.logLevel as LogLevel
    String message = args.message as String
    Object object = args.object

    if (isLogActive(logLevel)) {
      String fullMessage = message
      if (object != null) {
        String objectName = getClassName(object)
        if (objectName != null) {
          objectName = "($objectName) "
        } else {
          objectName = ""
        }
        String objectString = object.toString()
        fullMessage = "${message} -> ${objectName}${objectString}"
      }

      String finalMsg = "${scopeName} : ${fullMessage}"
      String functionName = getEnclosingFunctionName()
      if (functionName != null) {
        finalMsg = "${functionName}(): ${fullMessage}"
      }
      writeLogMsg(logLevel, finalMsg)
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
    // Ensure dsl is not null before calling echo
    try {
        if (this.dsl != null) {
          this.dsl.echo("$lvlString $msg")
        } else if (this.script != null) {
          this.script.echo("$lvlString $msg")
        } else {
            System.out.println("Logger: context not initialized for echo operations. Message: $lvlString $msg")
        }
    } catch (MissingPropertyException e) {
        // Fallback
        System.err.println("Logger Error: context not initialized for echo operations. Message: $lvlString $msg")
    }
  }

  /**
   * Wraps a string with color codes when terminal is available
   * @param colorCode
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
    // Use the stored script object if available
    if (script != null) {
      try {
        termEnv = script.env.TERM
      } catch (Exception ex) {
        // do nothing
      }
    } else if (dsl != null) {
      // If script is not available, try to access env via dsl if it has such a property (less common)
      // This is a weaker assumption and might not always work.
      try {
        termEnv = dsl.env.TERM // Most likely to fail without full script context
      } catch (MissingPropertyException e) {
        // dsl.env is not typical for raw DSL object
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

  /**
   * Utility function to determine if the given logLevel is active
   *
   * @param logLevel
   * @return true , when the loglevel should be displayed, false when the loglevel is disabled
   */
  public boolean isLogActive(LogLevel logLevel) {
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
  @NonCPS
  public String getEnclosingClassName() {
      String className = null
      def marker = new Throwable()
      int enclosingFunctionStackTraceFrameLevel = 2

      StackTraceElement[] stackTrace = StackTraceUtils.sanitize(marker).stackTrace

      if (stackTrace.length > enclosingFunctionStackTraceFrameLevel) {
          StackTraceElement ste = stackTrace[enclosingFunctionStackTraceFrameLevel]
          className = ste.getClassName()
      }
      loggerDebug("*** Enclosing className=" + className)
      return className
  }

}
