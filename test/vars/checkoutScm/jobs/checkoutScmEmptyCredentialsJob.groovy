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
package vars.checkoutScm.jobs

import static com.dettonville.pipeline.utils.ConfigConstants.SCM
import static com.dettonville.pipeline.utils.ConfigConstants.SCM_URL

/**
 * Executes default checkout with no credential found during auto lookup for the given scm url
 *
 * @return The script
 * @see vars.checkoutScm.CheckoutScmIT
 */
def execute() {
  checkoutScm((SCM): [
      (SCM_URL): "git@unknowndomain.tld/group/project1.git"
  ])
}

return this
