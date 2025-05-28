
import com.dettonville.api.pipeline.environment.EnvironmentConstants
import com.dettonville.api.pipeline.utils.ConfigConstants
import com.dettonville.api.pipeline.utils.logging.Logger

/**
 * Utility step to retrieve scm url when checkout was done via default scm variable (e.g. checkout scm)
 *
 * @param config
 */
void call(Map config = [:]) {
    // set default versions
    Logger log = new Logger(this)
    Map scmConfig = config[ConfigConstants.SCM] ?: [:]
    String scmUrl = scmConfig[ConfigConstants.SCM_URL] ?: null
    if (!scmUrl) {
        // scm config has no url property, assuming multibranch build and try to detect with git from command line
        try {
            scmUrl = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
        } catch (Exception ex) {
            // catch exception when checkout to subfolder
            // TODO: Add support for checking out into subfolder
        }
    }
    if (scmUrl) {
        log.info("Setting environment variable " + EnvironmentConstants.SCM_URL + " to $scmUrl")
        env.setProperty(EnvironmentConstants.SCM_URL, scmUrl)
    }
}
