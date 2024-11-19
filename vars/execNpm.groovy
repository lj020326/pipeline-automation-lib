/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2024 dettonville.org DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import hudson.AbortException
import com.dettonville.api.pipeline.managedfiles.ManagedFileConstants
import com.dettonville.api.pipeline.managedfiles.ManagedFileParser
import com.dettonville.api.pipeline.model.PatternMatchable
import com.dettonville.api.pipeline.shell.CommandBuilderImpl
import com.dettonville.api.pipeline.utils.PatternMatcher
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.resources.JsonLibraryResource
import net.sf.json.JSON
import org.jenkinsci.plugins.workflow.cps.DSL

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this, LogLevel.INFO)

/**
 * Executes npm
 * This step implements
 *  - auto lookup for NPMRC and NPM_CONFIG_USERCONFIG
 *
 * @param config Configuration options for the step
 */
void call(Map config = null) {
    config = config ?: [:]

    // retrieve the configuration and set defaults
    Map npmConfig = (Map) config[NPM] ?: [:]
    String npmExecutable = npmConfig[NPM_EXECUTABLE] ?: "npm"
    List arguments = npmConfig[NPM_ARGUMENTS] ?: []

    log.trace("NPM config: ", npmConfig)

    // retrieve scm url via utility step
    String scmUrl = getScmUrl(config)

    // initialize the command builder
    CommandBuilderImpl commandBuilder = new CommandBuilderImpl((DSL) steps, npmExecutable)
    commandBuilder.addArguments(arguments)

    // initialize the configuration files
    List configFiles = []

    // add config file for NPM_CONF_USERCONFIG if defined
    addManagedFile(scmUrl, ManagedFileConstants.NPM_CONFIG_USERCONFIG_PATH, ManagedFileConstants.NPM_CONF_USERCONFIG_ENV, configFiles)

    // add config file for NPM_CONF_GLOBALCONFIG if defined
    addManagedFile(scmUrl, ManagedFileConstants.NPMRC_PATH, ManagedFileConstants.NPM_CONF_GLOBALCONFIG_ENV, configFiles)

    log.debug("configFiles", configFiles)

    // run in config file provider wrapper
    configFileProvider(configFiles) {
        // check if npm user config was provided
        if (env.getProperty(ManagedFileConstants.NPM_CONF_USERCONFIG_ENV) != null) {
            log.debug("found environment variable ${ManagedFileConstants.NPM_CONF_USERCONFIG_ENV}, value: ${env.getProperty(ManagedFileConstants.NPM_CONF_USERCONFIG_ENV)}")
            commandBuilder.addPathArgument("--userconfig", (String) env.getProperty(ManagedFileConstants.NPM_CONF_USERCONFIG_ENV))
        }
        // check if npm global config was provided
        if (env.getProperty(ManagedFileConstants.NPM_CONF_GLOBALCONFIG_ENV) != null) {
            log.debug("found environment variable ${ManagedFileConstants.NPM_CONF_GLOBALCONFIG_ENV}, value: ${env.getProperty(ManagedFileConstants.NPM_CONF_GLOBALCONFIG_ENV)}")
            commandBuilder.addPathArgument("--globalconfig", (String) env.getProperty(ManagedFileConstants.NPM_CONF_GLOBALCONFIG_ENV))
        }

        // build the command line
        command = commandBuilder.build()
        log.info("executing npm with: $command")

        // execute the maven command
        sh(command)
    }
}

/**
 * Searches for a managed file in the json from jsonPath by using the scmUrl for matching and adds the file
 * to the provided configFiles object when a result was found.
 *
 * @param log Instance of the execNpm logger
 * @param scmUrl The scm url of the current job
 * @param jsonPath Path to the json containing configurations for managed files
 * @param envVar The environment variable where the configFileProvider should store the path in
 * @param configFiles List of config files where the found file has to be added
 */
void addManagedFile(String scmUrl, String jsonPath, String envVar, List configFiles) {
    try {
        // load and parse the json
        JsonLibraryResource jsonLibraryResource = new JsonLibraryResource(steps, jsonPath)
        JSON managedFilesJson = jsonLibraryResource.load()
        ManagedFileParser parser = new ManagedFileParser()
        List<PatternMatchable> managedFiles = parser.parse(managedFilesJson)
        PatternMatcher matcher = new PatternMatcher()
        // match the scmUrl against the parsed mangedFiles and get the best match
        PatternMatchable managedFile = matcher.getBestMatch(scmUrl, managedFiles)
        // when a file was found add it to the configFiles
        if (managedFile) {
            log.info("Found managed file for env var '$envVar' with id: '${managedFile.id}', adding to provided config files")
            configFiles.push(configFile(fileId: managedFile.getId(), targetLocation: "", variable: envVar))
        }
    } catch (AbortException ex) {
        log.debug("Unable to load resource from $jsonPath")
    }

}
