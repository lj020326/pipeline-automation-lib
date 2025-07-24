/**
 * Jenkins Logger Utility Class
 *
 * A flexible logging utility that supports variable log levels and works with both
 * Pipeline DSL and Job DSL contexts in Jenkins.
 *
 * Usage Examples:
 *
 * Pipeline DSL:
 *   @Library('your-shared-library') _
 *   def logger = new JenkinsLogger(this, logLevel: 'INFO')
 *   logger.info("Pipeline started")
 *   logger.error("Something went wrong")
 *
 * Job DSL:
 *   def logger = new JenkinsLogger(this, logLevel: 'DEBUG')
 *   logger.debug("Job configuration details")
 *   logger.warn("Deprecated configuration detected")
 */
package com.dettonville.pipeline.utils.logging


@Grab('org.slf4j:slf4j-api:1.7.32')
import java.text.SimpleDateFormat
import java.util.Date

class JenkinsLogger implements Serializable {

    // Log level constants
    static final Map<String, Integer> LOG_LEVELS = [
        'DEBUG': 0,
        'INFO': 1,
        'WARN': 2,
        'ERROR': 3
    ]

    // ANSI color codes for console output
    static final Map<String, String> COLORS = [
        'DEBUG': '\033[36m',  // Cyan
        'INFO': '\033[32m',   // Green
        'WARN': '\033[33m',   // Yellow
        'ERROR': '\033[31m',  // Red
        'RESET': '\033[0m'    // Reset
    ]

    // Instance variables
    private def context
    private String currentLogLevel
    private Integer currentLogLevelValue
    private String logPrefix
    private SimpleDateFormat dateFormat
    private boolean useColors

    /**
     * Constructor
     * @param context The Jenkins context (this in Pipeline DSL or Job DSL)
     * @param logLevel The minimum log level to display (DEBUG, INFO, WARN, ERROR)
     * @param prefix Optional prefix for log messages
     * @param useColors Whether to use ANSI colors in output (default: true)
     */
    JenkinsLogger(Map args=[:], def context)
    {
        // ref: https://stackoverflow.com/a/46512017/2791368
        String logLevel = args.get('logLevel', 'INFO')
        String prefix = args.get('prefix', '')
        boolean useColors = args.get('useColors', true)
        boolean includeDates = args.get('includeDates', false)

        this.context = context
        this.logPrefix = prefix ? "${prefix}->" : ""
        if (includeDates) {
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        }
        this.useColors = useColors
        // context.println("JenkinsLogger::constructor(): here")

        setLogLevel(logLevel)
    }

    /**
     * Set the current log level
     * @param logLevel The minimum log level to display
     */
    void setLogLevel(String logLevel) {
        String upperLogLevel = logLevel.toUpperCase()
        if (!LOG_LEVELS.containsKey(upperLogLevel)) {
            throw new IllegalArgumentException("Invalid log level: ${logLevel}. Valid levels are: ${LOG_LEVELS.keySet()}")
        }

        this.currentLogLevel = upperLogLevel
        this.currentLogLevelValue = LOG_LEVELS[upperLogLevel]
        // context.println("JenkinsLogger::setLogLevel(): currentLogLevel=${this.currentLogLevel}")
    }

    /**
     * Get the current log level
     * @return Current log level as string
     */
    String getLogLevel() {
        return currentLogLevel
    }

    /**
     * Check if a log level is enabled
     * @param level The log level to check
     * @return true if the level is enabled, false otherwise
     */
    boolean isEnabled(String level) {
        return LOG_LEVELS[level.toUpperCase()] >= currentLogLevelValue
    }

    /**
     * Generic log method
     * @param level Log level
     * @param message Message to log
     * @param throwable Optional throwable for error logging
     */
    private void log(String level, String message, Throwable throwable = null) {
        // context.println("JenkinsLogger::log(): message=${message}")
        if (!isEnabled(level)) {
            return
        }

        String colorStart = useColors ? COLORS[level] : ""
        String colorEnd = useColors ? COLORS['RESET'] : ""

        String formattedMessage = "${colorStart}[${level.padRight(5)}] ${logPrefix}${message}${colorEnd}"
        if (dateFormat) {
            String timestamp = dateFormat.format(new Date())
            formattedMessage = "${colorStart}${timestamp} [${level.padRight(5)}] ${logPrefix}${message}${colorEnd}"
        }

        // Handle different Jenkins contexts
        try {
            if (context.metaClass.respondsTo(context, 'echo')) {
                // Pipeline DSL context
                context.echo(formattedMessage)
            } else if (context.metaClass.respondsTo(context, 'println')) {
                // Job DSL context
                context.println(formattedMessage)
            } else {
                // Fallback to system out
                System.out.println(formattedMessage)
            }
        } catch (Exception e) {
            // Ultimate fallback
            System.out.println(formattedMessage)
        }

        // Log throwable if provided
        if (throwable) {
            String stackTrace = getStackTrace(throwable)
            try {
                if (context.hasProperty('echo')) {
                    context.echo("${colorStart}${stackTrace}${colorEnd}")
                } else if (context.hasProperty('println')) {
                    context.println("${colorStart}${stackTrace}${colorEnd}")
                } else {
                    System.out.println("${colorStart}${stackTrace}${colorEnd}")
                }
            } catch (Exception e) {
                System.out.println("${colorStart}${stackTrace}${colorEnd}")
            }
        }
    }

    /**
     * Debug level logging
     * @param message Message to log
     */
    void debug(String message) {
        log('DEBUG', message)
    }

    /**
     * Info level logging
     * @param message Message to log
     */
    void info(String message) {
//         context.println("JenkinsLogger::info(): message=${message}")
        log('INFO', message)
    }

    /**
     * Warning level logging
     * @param message Message to log
     */
    void warn(String message) {
        log('WARN', message)
    }

    /**
     * Warning level logging with throwable
     * @param message Message to log
     * @param throwable Throwable to log
     */
    void warn(String message, Throwable throwable) {
        log('WARN', message, throwable)
    }

    /**
     * Error level logging
     * @param message Message to log
     */
    void error(String message) {
        log('ERROR', message)
    }

    /**
     * Error level logging with throwable
     * @param message Message to log
     * @param throwable Throwable to log
     */
    void error(String message, Throwable throwable) {
        log('ERROR', message, throwable)
    }

    /**
     * Conditional logging - only logs if condition is true
     * @param condition Condition to check
     * @param level Log level
     * @param message Message to log
     */
    void logIf(boolean condition, String level, String message) {
        if (condition) {
            log(level.toUpperCase(), message)
        }
    }

    /**
     * Log method entry (DEBUG level)
     * @param methodName Name of the method
     * @param params Optional parameters
     */
    void entering(String methodName, Object... params) {
        if (isEnabled('DEBUG')) {
            String paramStr = params ? " with params: ${params.join(', ')}" : ""
            debug("Entering method: ${methodName}${paramStr}")
        }
    }

    /**
     * Log method exit (DEBUG level)
     * @param methodName Name of the method
     * @param result Optional return value
     */
    void exiting(String methodName, Object result = null) {
        if (isEnabled('DEBUG')) {
            String resultStr = result ? " returning: ${result}" : ""
            debug("Exiting method: ${methodName}${resultStr}")
        }
    }

    /**
     * Log execution time of a closure
     * @param description Description of the operation
     * @param closure Closure to execute and time
     * @return Result of the closure execution
     */
    def timed(String description, Closure closure) {
        long startTime = System.currentTimeMillis()
        info("Starting: ${description}")

        try {
            def result = closure()
            long duration = System.currentTimeMillis() - startTime
            info("Completed: ${description} (took ${duration}ms)")
            return result
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime
            error("Failed: ${description} (took ${duration}ms)", e)
            throw e
        }
    }

    /**
     * Create a child logger with additional prefix
     * @param childPrefix Additional prefix for child logger
     * @return New logger instance with combined prefix
     */
    JenkinsLogger createChild(String childPrefix) {
        String combinedPrefix = logPrefix ? "${logPrefix.replaceAll(/[\[\]]/, '')}:${childPrefix}" : childPrefix
        return new JenkinsLogger(context, logLevel: currentLogLevel, prefix: combinedPrefix, useColors: useColors)
    }

    /**
     * Utility method to get stack trace as string
     * @param throwable Throwable to get stack trace from
     * @return Stack trace as string
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter()
        PrintWriter pw = new PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    /**
     * Static factory method for quick logger creation
     * @param context Jenkins context
     * @param logLevel Log level
     * @return New logger instance
     */
    static JenkinsLogger create(def context, String logLevel = 'INFO') {
        return new JenkinsLogger(context, logLevel: logLevel)
    }

    /**
     * Log a separator line for better readability
     * @param level Log level for the separator
     * @param character Character to use for separator (default: '-')
     * @param length Length of separator (default: 50)
     */
    void separator(String level = 'INFO', String character = '-', int length = 50) {
        log(level.toUpperCase(), character * length)
    }

    /**
     * Log a banner message
     * @param level Log level
     * @param message Message to display in banner
     * @param character Character for banner borders (default: '=')
     */
    void banner(String level = 'INFO', String message, String character = '=') {
        int bannerWidth = Math.max(message.length() + 4, 50)
        String border = character * bannerWidth
        String paddedMessage = " ${message} ".center(bannerWidth)

        log(level.toUpperCase(), border)
        log(level.toUpperCase(), paddedMessage)
        log(level.toUpperCase(), border)
    }
}
