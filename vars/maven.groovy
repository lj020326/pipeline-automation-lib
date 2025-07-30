
import com.dettonville.pipeline.shell.CommandBuilder
import com.dettonville.pipeline.shell.CommandBuilderImpl
import com.dettonville.pipeline.utils.logging.LogLevel

import static com.dettonville.pipeline.utils.ConfigConstants.*

/**
 * Adapter function to allow execution by providing a config map.
 *
 * @param config
 */
void purgeSnapshots(Map config) {
  Map purgeConfig = config[MAVEN_PURGE_SNAPSHOTS] ?: [:]
  String repoPath= purgeConfig[MAVEN_PURGE_SNAPSHOTS_REPO_PATH] ?: null
  Boolean dryRun = purgeConfig[MAVEN_PURGE_SNAPSHOTS_DRY_RUN] != null ? purgeConfig[MAVEN_PURGE_SNAPSHOTS_DRY_RUN] : false
  LogLevel logLevel = (LogLevel) purgeConfig[MAVEN_PURGE_SNAPSHOTS_LOG_LEVEL] ?: null
  purgeSnapshots(repoPath, dryRun, logLevel)
}

/**
 * Purges SNAPSHOT artifacts from the given repository
 *
 * @param repositoryPath The repository path (e.g. $HOME/.m2/repository), set to null to use default
 * @param dryRun Set to true if you only want to see what the step will do
 * @param logLevel The log level of the managed pipeline shell script
 */
void purgeSnapshots(String repositoryPath = null, Boolean dryRun = false, LogLevel logLevel = null) {
  CommandBuilder commandBuilder = new CommandBuilderImpl(this.steps)
  if (repositoryPath != null) {
    commandBuilder.addArgument("--repo='$repositoryPath'")
  }
  if (dryRun) {
    commandBuilder.addArgument("--dryrun")
  }
  if (logLevel) {
    commandBuilder.addArgument("--loglvl=${logLevel.level}")
  }
  managedScripts.execPipelineShellScript("jenkinsPipelineLibrary/managedScripts/shell/maven/purge-snapshots.sh", commandBuilder)
}
