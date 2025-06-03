
import com.dettonville.api.pipeline.environment.EnvironmentConstants
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.ANSI_COLOR
import static com.dettonville.api.pipeline.utils.ConfigConstants.ANSI_COLOR_XTERM

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Enables color output in Jenkins console by using the ansiColor step
 * Please refer to the documentation for details about the configuration options
 *
 * @param config The configuration options
 * @param body The closure to be executed
 */
void color(Map config = [:], Closure body) {
    String ansiColorMap = (String) config[ANSI_COLOR] ?: ANSI_COLOR_XTERM

    String currentAnsiColorMap = env.getProperty(EnvironmentConstants.TERM)
    if (currentAnsiColorMap == ansiColorMap) {
        log.debug("Do not wrap with color scheme: '${ansiColorMap}' because wrapper with same color map is already active")
        // current ansi color map is new color map, do not wrap again
        body()
    } else {
        log.debug("Wrapping build with color scheme: '${ansiColorMap}'")
        ansiColor(ansiColorMap) {
            body()
        }
    }

}
