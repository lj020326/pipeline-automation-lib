/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2017 dettonville.org DevOps
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
package vars.wrap.jobs

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

/**
 * Runs the wrap.color step
 */
def execute() {
  Logger.init(this, LogLevel.DEBUG)
  Logger log = new Logger(this)

  Map config = [
      (ANSI_COLOR): ANSI_COLOR_VGA
  ]

  log.info("non colorized output - 1")

  wrap.color(config) {
    log.info("first wrap env.TERM: ${env.TERM}")
    wrap.color(config) {
      log.info("second wrap env.TERM: ${env.TERM}")
    }
  }

  log.info("non colorized output - 2")
}

return this
