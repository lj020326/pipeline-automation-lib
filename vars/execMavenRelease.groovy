
import com.dettonville.pipeline.environment.EnvironmentConstants
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.maps.MapUtils
import com.dettonville.pipeline.versioning.ComparableVersion
import org.apache.maven.model.Model
import org.apache.maven.model.Plugin

import static com.dettonville.pipeline.utils.ConfigConstants.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Utility step for performing a release with maven
 * This step implements
 *  - check for prerequisites (git ssh scm url, correct maven release plugin version)
 *  - enable ssh agent with credential auto lookup
 *  - calling execMaven with the appropriate params
 *
 * @param config Configuration options from pipeline
 */
void call(Map config = null) {
    config = config ?: [:]

    // retrieve the configuration and set defaults
    Map defaultConfig = [
            (MAVEN): [
                    (MAVEN_GOALS)    : ["release:prepare", "release:perform"],
                    (MAVEN_ARGUMENTS): ["-B", "-U"]
            ]
    ]
    // merge the configuration
    config = MapUtils.merge(defaultConfig, config)

    // retrieve scm url via utility step
    String scmUrl = getScmUrl(config)
    String scmBranch = env.getProperty(EnvironmentConstants.GIT_BRANCH)

    // check for correct scm configuration
    checkScm(scmUrl, scmBranch)

    // check for correct maven release plugin versionNumber
    checkMavenReleasePluginVersion(config)

    // wrap mvn commands into ssh agent to allow git commit and push actions

    sshAgentWrapper(scmUrl) {
        // execute maven release
        execMaven(config)
    }
}

/**
 * Checks if the maven release plugin has the required minimum version.
 * When the minimum version requirement is not met the step will exit with an error
 *
 * @param config The pipeline library configuration
 */
void checkMavenReleasePluginVersion(config) {
    String effectivePomTmp = "effective-pom.tmp"
    ComparableVersion minimalReleasePluginVersion = new ComparableVersion("2.5.3")

    Map effectivePomConfig = [
            (MAVEN): [
                    (MAVEN_GOALS)  : "help:effective-pom",
                    (MAVEN_DEFINES): [
                            "output": effectivePomTmp
                    ]
            ]
    ]
    config = MapUtils.merge(config, effectivePomConfig)
    // call the effective pom step
    execMaven(config)

    // read the maven pom
    def mavenModel = readMavenPom(file: effectivePomTmp)
    Map<String, Plugin> map = mavenModel.getBuild().getPluginManagement().getPluginsAsMap()

    def mavenReleasePlugin = map.get("org.apache.maven.plugins:maven-release-plugin")
    if (!mavenReleasePlugin) {
        error("No maven deploy plugin found in effective pom!")
    }
    String version = mavenReleasePlugin.getVersion()
    ComparableVersion actualReleasePluginVersion = new ComparableVersion(version)

    if (actualReleasePluginVersion < minimalReleasePluginVersion) {
        error("org.apache.maven.plugins:maven-release-plugin version requirement not met. Expected minimal version: '${minimalReleasePluginVersion}', found: '${actualReleasePluginVersion}' ")
    }

    // set the maven model to null to avoid serialization issues
    mavenModel = null
}

/**
 * Checks if url is a git ssh url and git branch is main, otherwise the step will exit with an error
 *
 * @param scmUrl The current scm url
 * @param scmBranch The current scm branch
 */
void checkScm(String scmUrl, String scmBranch) {
    // check if scm url is available
    if (scmUrl == null) {
        error("Unable to retrieve SCM url. Make sure to either provide the url by configuration or via `SCM_URL` envvar. Refer to getScmUrl and setScmUrl documentation.")
    }

    // check if scm url is a git ssh url, otherwise fail since releasing via http(s) is not supported due to security reasons
    if (!(scmUrl =~ '^git@.+:.+.git$')) {
        error("Invalid SCM url. Make sure to either provide the url by configuration or via `SCM_URL` envvar. Refer to getScmUrl and setScmUrl documentation.")
    }

    // check scm branch
    if (scmBranch == null) {
        error("Unable to retrieve 'GIT_BRANCH' environment variable. Make sure to checkout with 'checkout to local branch' extension enabled and call the setGitBranch step before.")
    }

    // check for correct branch
    if (scmBranch != 'main') {
        error("Not allowed branch detected. You are only able to release from 'main' branch. Detected branch: '$scmBranch'. If you are seeing a commit hash make sure to checkout with 'checkout to local branch'.")
    }
}
