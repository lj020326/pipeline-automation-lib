
import com.dettonville.api.pipeline.model.Tool
import com.dettonville.api.pipeline.utils.ConfigConstants
import com.dettonville.api.pipeline.utils.logging.Logger

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Main function to setup tools. Takes a list of
 *
 *  [ name: 'STRING', type: 'Tool', envVar: 'STRING' ]
 *
 * This step automatically adds the tool path to the PATH environment so it will be available for later usage
 *
 * @param config The config containing the tools to be setup inside tools node
 */
void call(Map config) {
    List<Map> toolsConfig = (List<Map>) config[ConfigConstants.TOOLS] ? config[ConfigConstants.TOOLS] : []

    for (Map toolConfig in toolsConfig) {
        doSetupTool(toolConfig, log)
    }
}

/**
 * Setups a tool based on the provided configuration. The path of the tool is automatically added to the PATH
 * environment for later usage.
 *
 * @param toolConfig A map with this structure: [ name: 'STRING', type: 'STRING', envVar: 'STRING' ]
 * @param log The logger instance from the main function
 */
void doSetupTool(Map toolConfig, Logger log) {
    // retrieve the configuration variables
    String toolName = toolConfig[ConfigConstants.TOOL_NAME]
    Tool toolType = (Tool) toolConfig[ConfigConstants.TOOL_TYPE]
    String toolEnvVar = toolConfig[ConfigConstants.TOOL_ENVVAR]

    // when no environment variable was provided do auto detection by using Tool enum
    if (toolEnvVar == null && toolType != null) {
        toolEnvVar = toolType.getEnvVar()
    }

    log.debug("Setting up '$toolName' with type '$toolType' to environment variable '$toolEnvVar'")

    // call the Jenkins pipeline method to install the tool and get back the path
    String retrievedTool = "${tool toolName}"

    // check of tool was retrieved correctly, otherwise abort
    if (retrievedTool == null || retrievedTool == "") {
        error("Tool '$toolName' not found, aborting")
    }

    // if environment variable is present, set the environment variable to the path of the tool
    if (toolEnvVar != null) {
        env.setProperty(toolEnvVar, retrievedTool)
        log.info "set environment var '$toolEnvVar' to: '${env.getProperty(toolEnvVar)}'"
    }

    // add the tool path to the PATH variable for later usage
    env.setProperty("PATH", "${retrievedTool}/bin:${env.PATH}")
    log.info "set environment var PATH to: ${env.PATH}"
}
