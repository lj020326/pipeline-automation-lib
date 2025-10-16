
import com.dettonville.pipeline.utils.IntegrationTestHelper
import com.dettonville.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

/**
 * Assert utitlity function
 *
 * @param expected Expected object/value
 * @param actual Actual object/value
 */
void assertEquals(expected, actual) {
    if (expected != actual) {
        error("Assertion error -> expected: '$expected', got '$actual'")
    }
}

/**
 * Logs the test result
 *
 * @param results The test results as map
 */
void logTestResults(Map results) {
    List lines = []
    String separator = "##############################################################"

    lines.push("")
    lines.push(separator)
    lines.push("package test results")
    lines.push(separator)
    Integer maxLength = 0
    results.each {
        String k, List v ->
            v.each {
                Map item ->
                    if (item.name.length() > maxLength) {
                        maxLength = item.name.length();
                    }
            }
    }
    results.each {
        String k, List v ->
            lines.push("Results for package: '$k'")

            v.each {
                Map item ->
                    String result = "SUCCESS"
                    if (item.exception != null) {
                        result = "FAILURE"
                    }
                    lines.push(" - ${item.name.padRight(maxLength)} : ${result}")
            }
    }
    log.info(lines.join('\n'))
}

/**
 * Processes the test results and fails when an error is found
 *
 * @param results The test results as map
 */
void processFailedTests(Map results) {
    Logger log = new Logger(this)
    List lines = []
    Integer maxLength = 0
    List failureItems = []
    // check if there are any errors:
    results.each {
        String k, List v ->
            v.each {
                Map item ->
                    if (item.exception != null) {
                        maxLength = item.name.length();
                        failureItems.push(item)
                    }
            }
    }

    // return when there are no test failures
    if (failureItems.size() == 0) {
        log.info("no test failures found")
        return
    }

    String separator = "##############################################################"

    lines.push("")
    lines.push(separator)
    lines.push("package test failures")
    lines.push(separator)

    failureItems.each {
        Map item ->
            lines.push(" - ${item.name.padRight(maxLength)} exception: ${item.exception}")
    }
    String message = lines.join('\n')
    log.fatal(message)
    error(message)
}

/**
 * Utility function for running a test
 *
 * @param className The name of the Class to run the test for
 * @param testClosure Contains the test code
 */
void runTest(String className, Closure testClosure) {
    Map result = [
        "name"     : className,
        "exception": null
    ]
    try {
        testClosure()
    } catch (Exception ex) {
        result.exception = ex
    }
    IntegrationTestHelper.addTestResult(result)
}

/**
 * Wrapper function for reporting the test results for a package
 *
 * @param results The results object
 * @param packageName The name of the package used for display in Stage View
 * @param packageTestClosure The closure containing the code to be executed
 */
void runTestsOnPackage(String packageName, Closure packageTestClosure) {
    // reset package test results
    IntegrationTestHelper.addTestPackage(packageName)
    String displayName = packageName.replace("com.dettonville.pipeline.", "")
    stage(displayName) {
        packageTestClosure()
    }
}
