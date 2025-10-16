/*-
 * #%L
 * apps.dettonville.org
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
package com.dettonville.pipeline.managedfiles

/**
 * Constants for managed files used by the pipeline library
 */
class ManagedFileConstants implements Serializable {

  private static final long serialVersionUID = 1L

  static final String GLOBAL_MAVEN_SETTINGS_PATH = "managedfiles/maven/global-settings.json"
  static final String GLOBAL_MAVEN__SETTINGS_ENV = "MVN_GLOBAL_SETTINGS"

  static final String MAVEN_SETTINS_PATH = "managedfiles/maven/settings.json"
  static final String MAVEN_SETTING_ENV = "MVN_SETTINGS"

  static final String NPM_CONFIG_USERCONFIG_PATH = "managedfiles/npm/npm-config-userconfig.json"
  // wrong named in existing jenkins/maven projects
  // TODO: remove
  static final String NPM_CONFIG_USERCONFIG_ENV = "NPM_CONFIG_USERCONFIG"
  // correct name based on NPM config
  static final String NPM_CONF_USERCONFIG_ENV = "NPM_CONF_USERCONFIG"

  static final String NPMRC_PATH = "managedfiles/npm/npmrc.json"
  // wrong name in existing jenkins/maven projects
  // TODO: remove
  static final String NPMRC_ENV = "NPMRC"
  // correct name based on NPM config
  static final String NPM_CONF_GLOBALCONFIG_ENV = "NPM_CONF_GLOBALCONFIG"


  static final String BUNDLE_CONFIG_ENV = "BUNDLE_CONFIG"
  static final String BUNDLE_CONFIG_PATH = "managedfiles/ruby/bundle-config.json"

}
