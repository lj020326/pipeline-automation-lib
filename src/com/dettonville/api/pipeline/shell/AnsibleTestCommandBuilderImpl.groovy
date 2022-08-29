/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2018 Dettonville DevOps
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
package com.dettonville.api.pipeline.shell

import com.cloudbees.groovy.cps.NonCPS
import com.dettonville.api.pipeline.credentials.Credential
import com.dettonville.api.pipeline.credentials.CredentialAware
import com.dettonville.api.pipeline.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.DSL

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

/**
 * Utility for building ansible-test commands
 */
class AnsibleTestCommandBuilderImpl implements CommandBuilder,
        com.dettonville.api.pipeline.credentials.CredentialAware,
        ConfigAwareCommandBuilder, Serializable
{

  private static final long serialVersionUID = 1L

  /**
   * Default executable
   */
  public static final String EXECUTABLE = "ansible-test"

  /**
   * The ansible-test destination path
   */
  String destination = null

  /**
   * Logger instance
   */
  com.dettonville.api.pipeline.utils.logging.Logger log = new com.dettonville.api.pipeline.utils.logging.Logger(this)

  /**
   * Wrapped command builder since inheritance causes problems in Groovy Sandbox
   */
  CommandBuilderImpl commandBuilder

  /**
   * Credentials for SSH
   */
  com.dettonville.api.pipeline.credentials.Credential credentials

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param executable The executable, default: 'ansible-test'
   */
  AnsibleTestCommandBuilderImpl(DSL dsl, String executable = null) {
    commandBuilder = new CommandBuilderImpl(dsl, executable ?: EXECUTABLE)
    this.reset()
  }

  /**
   * Applies a given map configuration to the command builder
   *
   * @param config Map with configration values
   */
  @NonCPS
  ConfigAwareCommandBuilder applyConfig(Map config) {
    commandBuilder.executable = config[ANSIBLE_TEST_EXECUTABLE] ?: "ansible-test"

    this.destination = config[ANSIBLE_TEST_DESTINATION] ?: null

    Boolean color = config[ANSIBLE_TEST_COLOR] ?: false
    List arguments = (List) config[ANSIBLE_TEST_ARGUMENTS] ?: []

    // add arguments
    this.addArguments(arguments)
    // add recursive if configured
    if (color) {
      this.addArgument("--color")
    }
    return this
  }

  /**
   * Used to set the username based on a Credential found by auto lookup
   *
   * @param credential The credential object to use the username from (if set)
   */
  @NonCPS
  void setCredential(com.dettonville.api.pipeline.credentials.Credential credential) {
    this.credentials = credential
    if (this.user == null && this.credentials != null && credential.getUserName() != null) {
      this.user = credential.getUserName()
    }
  }

  @Override
  com.dettonville.api.pipeline.credentials.Credential getCredential() {
    return this.credentials
  }
/**
 * Builds the commandline by first calling the build function of superclass and then adding
 * - shell escaped source path
 * - user and host
 * - shell escaped destination path
 *
 * @return The ansible-test command line
 */
  @NonCPS
  CommandBuilder addArgument(String argument) {
    commandBuilder.addArgument(argument)
    return this
  }

  /**
   * @see CommandBuilder#addPathArgument(java.lang.String)
   */
  @NonCPS
  CommandBuilder addPathArgument(String argument) {
    commandBuilder.addPathArgument(argument)
    return this
  }

  /**
   * @see CommandBuilder#addPathArgument(java.lang.String, java.lang.String)
   */
  @NonCPS
  CommandBuilder addPathArgument(String argumentName, String value) {
    commandBuilder.addPathArgument(argumentName, value)
    return this
  }

  /**
   * @see CommandBuilder#addArgument(java.lang.String, java.lang.String)
   */
  @NonCPS
  CommandBuilder addArgument(String argumentName, String argumentValue) {
    commandBuilder.addArgument(argumentName, argumentValue)
    return this
  }

  /**
   * Builds the command line for ansible-test by using the wrapped command builder and
   * adding the specific ansible-test arguments.
   *
   * @see CommandBuilder#build()
   */
  @NonCPS
  String build() {
    String baseCommand = commandBuilder.build()
    if (host == null || destination == null || sourcePath == null) {
      log.fatal("One of the mandatory properties is not set! (host: $host, destination: $destination, sourcePath: $sourcePath)")
      // exits and throws HudsonAbortException
      commandBuilder.dsl.error("One of the mandatory properties is not set! (host: $host, destination: $destination, sourcePath: $sourcePath)")
    }
    String escapeddestination = ShellUtils.escapePath(destination)
    String escapedSourcePath = ShellUtils.escapePath(sourcePath)

    // calculate destination
    // add user when defined
    String destination = user ? "$user@" : ""
    // add the host
    destination = "$destination$host:"
    // add the destination path surrounded by double quotes since it should be evaluated on target server
    destination = destination + "\"$escapeddestination\""

    // append to the existing command and return
    return "$baseCommand $escapedSourcePath $destination"
  }

  @NonCPS
  CommandBuilder addArguments(String arguments) {
    commandBuilder.addArguments(arguments)
    return this
  }

  @NonCPS
  CommandBuilder addArguments(List<String> arguments) {
    commandBuilder.addArguments(arguments)
    return this
  }

  @Override
  @NonCPS
  CommandBuilder reset() {
    host = null
    user = null
    destination = null
    sourcePath = null
    credentials = null
    commandBuilder.reset()
    return this
  }

  @Override
  @NonCPS
  CommandBuilder setExecutable(String executable) {
    commandBuilder.setExecutable(executable)
    return this
  }
}
