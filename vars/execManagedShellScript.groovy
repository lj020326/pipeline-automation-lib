
import com.dettonville.api.pipeline.shell.CommandBuilderImpl
import com.dettonville.api.pipeline.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.DSL

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Adapter when called with list of arguments
 *
 * @param fileId The id of the managed files
 * @param args List of string arguments for the manages script
 * @return the output of the executed shell script
 */
String call(String fileId, List<String> args) {
    return this.call(fileId, args.join(" "))
}

/**
 * Executes a managed script identified by fileId with the given argLine.
 * Since managed shell scripts are not executable by default when provided by the configFileProvider
 * this step takes also care about the specific chmod command.
 *
 * @param fileId The id of the managed script
 * @param argLine The argument line for the managed script to be executed
 * @return the output of the executed shell script
 * @deprecated
 */
String call(String fileId, String argLine) {
    log.info("Executing managed script with id: '$fileId' and argLine: '$argLine'")
    log.deprecated('execManagedShellScript', 'managedScripts.execJenkinsShellScript')

    // creating an environment variable
    String envVar = "SCRIPT_$fileId"

    // get the managed file via the configFileProvider step
    configFileProvider([configFile(fileId: fileId, variable: envVar)]) {
        // retrieve the path to the provided managed script
        String managedScriptPath = env.getProperty(envVar)

        // make script executable
        CommandBuilderImpl chmodBuilder = new CommandBuilderImpl((DSL) steps, "chmod")
        chmodBuilder.addArgument("+x")
        chmodBuilder.addPathArgument(managedScriptPath)
        String chmodCommand = chmodBuilder.build()
        sh(chmodCommand)

        // build shell command for executing managed script
        CommandBuilderImpl commandBuilder = new CommandBuilderImpl((DSL) steps)
        commandBuilder.addPathArgument(managedScriptPath)
        commandBuilder.addArgument(argLine)
        String command = commandBuilder.build()

        // execute the command
        log.info("Executing command: $command")
        return sh(returnStdout: true, script: command).trim()
    }
}
