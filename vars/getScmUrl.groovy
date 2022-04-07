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
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

/**
 * Tries to retrieve the current scm url by using some fallback steps
 *
 * @param config Configuration options for pipeline library
 */
String call(Map config = [:]) {
    Logger log = new Logger(this)
    Map scmConfig = (Map) config[SCM] ?: [:]
    // try to retrieve scm url from config constants, otherwise do fallback to SCM_URL environment variable
    String detectedScmUrl = scmConfig[SCM_URL] ?: null
    if (detectedScmUrl == null) {
        detectedScmUrl = env.getProperty(EnvironmentConstants.SCM_URL) ?: null
    }
    // log a warning when scm url is still null
    if (detectedScmUrl == null) {
        log.warn("Unable to detect scm url from config or environment variable!")
    }
    return detectedScmUrl
}
