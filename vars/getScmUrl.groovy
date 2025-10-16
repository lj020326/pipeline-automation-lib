
import com.dettonville.pipeline.environment.EnvironmentConstants
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

/**
 * Tries to retrieve the current scm url by using some fallback steps
 *
 * @param config Configuration options for pipeline library
 */
String call(Map config = [:]) {
    Map scmConfig = (Map) config[SCM] ?: [:]
    // try to retrieve scm url from config constants, otherwise do fallback to SCM_URL environment variable
    String detectedScmUrl = scmConfig[SCM_URL] ?: null
    if (detectedScmUrl == null) {
        detectedScmUrl = env.getProperty(EnvironmentConstants.SCM_URL) ?: null
    }
    // log a warning when scm url is still null
    if (detectedScmUrl == null) {
        log.warn("Unable to detect scm url from config or environment variable!")
    }
    return detectedScmUrl
}
