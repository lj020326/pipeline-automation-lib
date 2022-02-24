/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2018 dettonville.org DevOps
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
import com.dettonville.api.pipeline.environment.EnvironmentConstants
import com.dettonville.api.pipeline.utils.ConfigConstants
import com.dettonville.api.pipeline.utils.logging.Logger

/**
 * Utility step to retrieve scm url when checkout was done via default scm variable (e.g. checkout scm)
 *
 * @param config
 */
void call(Map config = [:]) {
    // set default versions
    Logger log = new Logger(this)
    Map scmConfig = config[ConfigConstants.SCM] ?: [:]
    String scmUrl = scmConfig[ConfigConstants.SCM_URL] ?: null
    if (!scmUrl) {
        // scm config has no url property, assuming multibranch build and try to detect with git from command line
        try {
            scmUrl = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
        } catch (Exception ex) {
            // catch exception when checkout to subfolder
            // TODO: Add support for checking out into subfolder
        }
    }
    if (scmUrl) {
        log.info("Setting environment variable " + EnvironmentConstants.SCM_URL + " to $scmUrl")
        env.setProperty(EnvironmentConstants.SCM_URL, scmUrl)
    }
}
