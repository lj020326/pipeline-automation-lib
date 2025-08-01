
import com.dettonville.pipeline.shell.CommandBuilder
import com.dettonville.pipeline.shell.CommandBuilderImpl
import com.dettonville.pipeline.utils.logging.Logger
import com.dettonville.pipeline.utils.resources.LibraryResource
import org.jenkinsci.plugins.workflow.cps.DSL

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

/**
 * Executes a managed shell script from the ConfigFileProvider Plugin
 *
 * @param scriptId The id of the script to execute
 * @param commandBuilder The CommandBuilder used for building the command line
 * @param returnStdout When set to true the stdout will be returned
 * @param returnStatus When set to true the status code will be returned
 * @return stdout, status code or sh step result, depending on the selection
 */
Object execJenkinsShellScript(String scriptId, CommandBuilder commandBuilder = null, returnStdout = false, returnStatus = false) {
  log.debug("scriptId", scriptId)
  String tmpScriptPath = '.jenkinsShellScript_' + scriptId

  Object ret = null

  // get the managed file via the configFileProvider step
  configFileProvider([configFile(fileId: scriptId, targetLocation: tmpScriptPath)]) {
    ret = _execShellScript(tmpScriptPath, commandBuilder, returnStdout, returnStatus)
  }
  return ret
}

/**
 * Executes a managed shell script from the pipeline library
 *
 * @param scriptPath The path to the script
 * @param commandBuilder The CommandBuilder used for building the command line
 * @param returnStdout When set to true the stdout will be returned
 * @param returnStatus When set to true the status code will be returned
 * @return stdout, status code or sh step result, depending on the selection
 */
Object execPipelineShellScript(String scriptPath, CommandBuilder commandBuilder = null, returnStdout = false, returnStatus = false) {
  Logger log = new Logger('execPipelineShellScript')
  String tmpScriptPath = '.libraryShellScript_' + scriptPath.replace('/','___')
  log.info("provide pipelin shell script from '$scriptPath' to '$tmpScriptPath'")
  LibraryResource pipelineScriptResource = new LibraryResource(this.steps, scriptPath)
  String scriptContent = pipelineScriptResource.load()
  writeFile(encoding: 'UTF-8', file: tmpScriptPath, text: scriptContent)
  return _execShellScript(tmpScriptPath, commandBuilder, returnStdout, returnStatus)
}

/**
 * Internal function to execute a managed shell script.
 *
 * @param log The Logger instance
 * @param scriptPath Path to the script that should be executed
 * @param commandBuilder The CommandBuilder used for building the command line
 * @param returnStdout When set to true the stdout will be returned
 * @param returnStatus When set to true the status code will be returned
 * @return stdout, status code or sh step result, depending on the selection
 */
Object _execShellScript(String scriptPath, CommandBuilder commandBuilder, returnStdout = false, returnStatus = false) {
  log.debug("scriptPath: '$scriptPath', returnStdout: $returnStdout, returnStatus: $returnStatus")
  if (returnStatus == true && returnStdout == true) {
    log.warn("returnStatus and returnStdout are set to true, only one parameter is allowed to be true, using returnStdout")
  }
  // mark script as executable
  CommandBuilderImpl chmodBuilder = new CommandBuilderImpl((DSL) steps, "chmod")
  chmodBuilder.addArgument("+x")
  chmodBuilder.addPathArgument(scriptPath)
  String chmodCommand = chmodBuilder.build()
  sh(chmodCommand)

  if (commandBuilder == null) {
    commandBuilder = new CommandBuilderImpl(this.steps)
  }
  // build shell command for executing managed script
  commandBuilder.setExecutable("./$scriptPath")
  String command = commandBuilder.build()

  // execute the command
  log.info("Executing command: $command")

  Object ret = null

  if (returnStdout == true) {
    ret = sh(returnStdout: true, script: command).trim()
  } else if (returnStatus == true) {
    ret = sh(returnStatus: true, script: command)
  } else {
    ret = sh(script: command)
  }
  return ret
}
