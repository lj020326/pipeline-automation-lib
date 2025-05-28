
import com.dettonville.api.pipeline.environment.EnvironmentConstants
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

/**
 * Tries to retrieve the current scm url by using some fallback steps
 *
 * @param config Configuration options for pipeline library
 */
String call(Map config = [:]) {
    Logger log = new Logger(this)
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
