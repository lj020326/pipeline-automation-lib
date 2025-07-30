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
package com.dettonville.pipeline.shell

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import com.dettonville.pipeline.utils.ConfigConstants
import com.dettonville.pipeline.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.DSL

/**
 * Utility for building maven commands executed via the sh pipeline step
 */
class MavenCommandBuilderImpl implements Serializable, CommandBuilder, ConfigAwareCommandBuilder {

  private static final long serialVersionUID = 1L

  static final String EXECUTABLE = "mvn"
  static final String ARG_GLOBAL_SETTINGS = "--global-settings"
  static final String ARG_SETTINGS = "--settings"
  static final String ARG_FILE = "-f"

  public String _globalSettingsId = null
  public String _settingsId = null

  public CommandBuilder commandBuilder

  public DSL dsl

  public Map _params

  public com.dettonville.pipeline.utils.logging.Logger log = new com.dettonville.pipeline.utils.logging.Logger(this)

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param executable The executable, default: 'maven'
   *
   * @deprecated
   */
  MavenCommandBuilderImpl(DSL dsl, String executable = null) {
    this(dsl, [:], executable)
    log.warn("Calling MavenCommandBuilderImpl Constructor without params is deprecated and is subject to remove in the upcoming versions")
  }

  /**
   * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
   * @param executable The executable, default: 'maven'
   *
   * @deprecated
   */
  MavenCommandBuilderImpl(DSL dsl, Map params, String executable = null) {
    commandBuilder = new CommandBuilderImpl(dsl, executable ?: EXECUTABLE)
    this.dsl = dsl
    this._params = params
    this.reset()
  }

  /**
   * Adds the global settings argument with the given path to the command line
   * @param path Path to the global maven settings
   *
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl setGlobalSettings(String path) {
    this.addPathArgument(ARG_GLOBAL_SETTINGS, path)
    return this
  }

  /**
   * Sets maven profiles based upon the provided string
   *
   * @param profiles The maven profiles as string, comma separated
   *
   * @return MavenCommandBuilderImpl
   */
  @NonCPS
  MavenCommandBuilderImpl addProfiles(String profiles) {
    if (profiles && profiles != "") {
      this.addArgument("-P${profiles}")
    }
    return this
  }

  /**
   * Sets the maven profiles based upon the list
   * @param profiles The maven profiles as list
   *
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl addProfiles(List<String> profiles) {
    if (profiles.size() > 0) {
      this.addProfiles(profiles.join(","))
    }
    return this

  }

  /**
   * Sets the goals to be executed
   *
   * @param goals The Maven goals as string
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl setGoals(String goals) {
    this.addArgument(goals)
    return this
  }

  /**
   * Sets the goals to be executed.
   * Adapter function for goals that are provided as list.
   *
   * @param goals The maven goals as list
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl setGoals(List goals) {
    if (goals.size() > 0) {
      this.setGoals(goals.join(" "))
    }
    return this
  }

  /**
   * Adds the settings argument with the given path to the command line
   *
   * @param path Path to the maven settings
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl setSettings(String path) {
    this.addPathArgument(ARG_SETTINGS, path)
    return this
  }

  /**
   * Sets the path to the pom
   *
   * @param path The path to the pom file for maven
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl setPom(String path) {
    this.addPathArgument(ARG_FILE, path)
    return this
  }

  /**
   * Adds a flag define to the maven command line
   *
   * @param defineName The define to be added (-D is automatically added)
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl addDefine(String defineName) {
    if (defineName == null) return this
    this.addArgument("-D".concat(defineName))
    return this
  }

  /**
   * Adds defined based on map input
   * @param defines the defines as map
   * @return The current command builder instance
   */
  @NonCPS
  @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
  MavenCommandBuilderImpl addDefines(Map defines) {
    defines.each {
      String k, v ->
        if (v != null) {
          this.addArgument("-D$k=$v".toString())
        } else {
          this.addArgument("-D$k".toString())
        }
    }
    return this
  }

  /**
   * Adds defines as string
   *
   * @param defines The defines to be added (each define must be prefixed with -D)
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl addDefines(String defines) {
    this.addArguments(defines)
    return this
  }

  /**
   * Adds a value define to the maven command line
   *
   * @param defineName The name of the define (will be automatically prefixed with -D)
   * @param defineValue The value of the define
   * @return The current command builder instance
   */
  @NonCPS
  MavenCommandBuilderImpl addDefine(String defineName, Object defineValue) {
    if (defineName == null) return this
    if (defineValue == null) {
      this.addDefine(defineName)
    } else {
      this.addArgument("-D".concat(defineName).concat("=").concat(defineValue.toString()))
    }
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder reset() {
    this._globalSettingsId = null
    this._settingsId = null
    commandBuilder.reset()
    return this
  }

  /**
   *
   * @return id of maven global settings managed file
   */
  @NonCPS
  String getGlobalSettingsId() {
    return _globalSettingsId
  }

  /**
   * @return id of the maven settings managed file
   */
  @NonCPS
  String getSettingsId() {
    return _settingsId
  }

  /**
   * @param globalSettingsId The id of the global maven settings managed file
   */
  @NonCPS
  void setGlobalSettingsId(String globalSettingsId) {
    this._globalSettingsId = globalSettingsId
  }

  /**
   * @param settingsId The id of the maven settings managed file
   */
  @NonCPS
  void setSettingsId(String settingsId) {
    this._settingsId = settingsId
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  ConfigAwareCommandBuilder applyConfig(Map config) {
    // parse config
    Map mavenConfig = (Map) config[com.dettonville.pipeline.utils.ConfigConstants.MAVEN] ?: [:]
    String mavenExecutable = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_EXECUTABLE] ?: null
    String pom = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_POM] ?: null
    Object goals = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_GOALS] ?: []
    Object arguments = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_ARGUMENTS] ?: []
    globalSettingsId = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_GLOBAL_SETTINGS] ?: null
    settingsId = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_SETTINGS] ?: null
    Object defines = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_DEFINES] ?: [:]
    Boolean injectParameters = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_INJECT_PARAMS] ?: false
    Object profiles = mavenConfig[com.dettonville.pipeline.utils.ConfigConstants.MAVEN_PROFILES] ?: []

    if (mavenExecutable != null) {
      commandBuilder.setExecutable(mavenExecutable)
    }

    // set pom
    this.setPom(pom)
    // set goals
    this.setGoals(goals)
    // set arguments
    this.addArguments(arguments)
    // add defines to command builder
    this.addDefines(defines)
    if (injectParameters) {
      this.addDefines(this._params)
    }
    // add profiles
    this.addProfiles(profiles)
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder setExecutable(String executable) {
    commandBuilder.setExecutable(executable)
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder addArgument(String argument) {
    commandBuilder.addArgument(argument)
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder addPathArgument(String argument) {
    commandBuilder.addPathArgument(argument)
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder addPathArgument(String argumentName, String value) {
    commandBuilder.addPathArgument(argumentName, value)
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder addArgument(String argumentName, String argumentValue) {
    commandBuilder.addArgument(argumentName, argumentValue)
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  String build() {
    return commandBuilder.build()
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder addArguments(String arguments) {
    commandBuilder.addArguments(arguments)
    return this
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NonCPS
  CommandBuilder addArguments(List<String> arguments) {
    commandBuilder.addArguments(arguments)
    return this
  }
}
