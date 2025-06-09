/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Lee Johnson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * #L%
 */
package com.dettonville.api.pipeline.shell

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.jenkinsci.plugins.workflow.cps.DSL

/**
 * Utility for building commands executed via the sh pipeline step
 */
class CommandBuilderImpl implements CommandBuilder, Serializable {

  private static final long serialVersionUID = 1L

  /**
   * The path of the executable
   */
  String _executable = null

  /**
   * Reference to the DSL object of the current pipeline script
   */
  DSL dsl

  /**
   * Used for storing the added arguments
   */
  List<String> arguments

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   */
  CommandBuilderImpl(DSL dsl) {
    this.dsl = dsl
    this.reset()
  }

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param executable The executable to use
   */
  CommandBuilderImpl(DSL dsl, String executable) {
    this(dsl)
    if (executable == null) {
      dsl.error("provided executable is null, please make sure to provide a String")
    }
    this._executable = executable
  }

  /**
   * Adds an argument to the list
   *
   * @param argument The argument to add
   * @return The current instance
   */
  @NonCPS
  CommandBuilder addArgument(String argument) {
    if (argument == null || argument == "") return this
    this.arguments.push(argument)
    return this
  }

  /**
   * Adds a path argument.
   * The provided argument will be escaped for shell usage before adding to arguments list.
   *
   * @param argument The path argument to add
   * @return The current instance
   */
  @NonCPS
  CommandBuilder addPathArgument(String argument) {
    if (argument == null) return
    argument = ShellUtils.escapePath(argument)
    this.arguments.push(argument)
    return this
  }

  /**
   * Adds a path argument with argument name and value e.g. --path /some/path
   * The provided argument will be escaped for shell usage before adding to arguments list.
   *
   * @param argumentName The name of the argument
   * @param value The value of the argument
   * @return The current instance
   */
  @NonCPS
  CommandBuilder addPathArgument(String argumentName, String value) {
    if (value == null || argumentName == null) return this
    value = ShellUtils.escapePath(value)
    this.addArgument(argumentName, value)
    return this
  }

  /**
   * Adds a path argument with argument name and value e.g. --prop value
   *
   * @param argumentName The name of the argument
   * @param argumentValue The value of the argument
   * @return The current instance
   */
  @NonCPS
  CommandBuilder addArgument(String argumentName, String argumentValue) {
    if (argumentName == null || argumentValue == null) return this
    this.arguments.push("$argumentName $argumentValue")
    return this
  }

  /**
   * Builds the command line by joining all provided arguments using space
   *
   * @return The command line that can be called by the 'sh' step
   */
  @NonCPS
  String build() {
    // add the executable, add etc. is blocked by sandbox
    List tmpArgs = []
    if (_executable != null) {
      tmpArgs.push(_executable)
    }
    for (String argument : arguments) {
      tmpArgs.push(argument)
    }

    return tmpArgs.join(" ")
  }

  /**
   * Adapter function for arguments provided as string
   *
   * @param arguments The argument String to be added
   * @return The current instance
   */
  @NonCPS
  CommandBuilder addArguments(String arguments) {
    this.addArgument(arguments)
    return this
  }

  /**
   * Adds a list of arguments
   *
   * @param arguments A List of String containing 0-n arguments to be added
   * @return The current instance
   */
  @NonCPS
  @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
  CommandBuilder addArguments(List<String> arguments) {
    arguments?.each { String argument ->
      this.addArgument(argument)
    }
    return this
  }

  /**
   * Resets the command builder
   *
   * @return The current instance
   */
  @Override
  @NonCPS
  CommandBuilder reset() {
    arguments = []
    return this
  }

  /**
   * Sets the executable
   * @param executable The executable to be used
   * @return The current instance
   */
  @Override
  @NonCPS
  CommandBuilder setExecutable(String executable) {
    this._executable = executable
    return this
  }
}
