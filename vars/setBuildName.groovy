
import com.dettonville.api.pipeline.environment.EnvironmentConstants
import com.dettonville.api.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Sets the build name depending on the availability of the GIT_BRANCH environment variable.
 *
 */
void call() {
    // set default versions
    String versionNumberString = '#${BUILD_NUMBER}'
    // check if GIT_BRANCH env var is available
    if (env.getProperty(EnvironmentConstants.GIT_BRANCH) != null) {
        versionNumberString = '#${BUILD_NUMBER}_${' + EnvironmentConstants.GIT_BRANCH + '}'
    }
    // create the versionNumber string
    def version = VersionNumber(projectStartDate: '1970-01-01', versionNumberString: versionNumberString, versionPrefix: '')
    log.info("created versionNumber number", version)
    // set the builds display name
    currentBuild.setDisplayName(version)
}
