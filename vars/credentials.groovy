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

import com.dettonville.api.pipeline.credentials.Credential
import com.dettonville.api.pipeline.credentials.CredentialConstants
import com.dettonville.api.pipeline.credentials.CredentialParser
import com.dettonville.api.pipeline.utils.PatternMatcher
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.resources.JsonLibraryResource
import net.sf.json.JSON
import org.jenkinsci.plugins.workflow.cps.DSL

/**
 * Tries to retrieve credentials for the given host by using configurations provided in
 * resources/credentials/http/credentials.json
 *
 * @param uri The uri to retrieve the credentials for
 * @return The found Credential object or null when no credential object was found during auto lookup
 * @see com.dettonville.api.pipeline.credentials.Credential
 * @see com.dettonville.api.pipeline.credentials.CredentialParser
 * @see JsonLibraryResource
 * @see com.dettonville.api.pipeline.credentials.CredentialConstants
 */
Credential lookupHttpCredential(String uri) {
  Logger log = new Logger("lookupHttpCredential")
  // load the json
  JsonLibraryResource jsonRes = new JsonLibraryResource((DSL) this.steps, CredentialConstants.HTTP_CREDENTIALS_PATH)
  try {
    JSON credentialJson = jsonRes.load()
    // parse the credentials
    CredentialParser parser = new CredentialParser()
    List<Credential> credentials = parser.parse(credentialJson)
    // try to find matching credential and return the credential
    PatternMatcher matcher = new PatternMatcher()
    return (Credential) matcher.getBestMatch(uri, credentials)
  } catch (Exception ex) {
    log.warn("Unable to lookup HTTP(S) credentials for $uri", ex.getMessage())
  }
  return null
}

/**
 * Tries to retrieve credentials for the given scmUrl by using configurations provided in
 * resources/credentials/scm/credentials.json
 *
 * @param uri The uri to lookup the credentials for
 * @return The found Credential object or null when no credential object was found during auto lookup
 * @see com.dettonville.api.pipeline.credentials.Credential
 * @see com.dettonville.api.pipeline.credentials.CredentialParser
 * @see com.dettonville.api.pipeline.utils.resources.JsonLibraryResource
 * @see com.dettonville.api.pipeline.credentials.CredentialConstants
 */
Credential lookupScmCredential(String uri) {
  Logger log = new Logger("lookupScmCredential")
  // load the json
  JsonLibraryResource jsonRes = new JsonLibraryResource((DSL) this.steps, CredentialConstants.SCM_CREDENTIALS_PATH)
  try {
    JSON credentialJson = jsonRes.load()
    // parse the credentials
    CredentialParser parser = new CredentialParser()
    List<Credential> credentials = parser.parse(credentialJson)
    // try to find matching credential and return the credential
    PatternMatcher matcher = new PatternMatcher()
    return (Credential) matcher.getBestMatch(uri, credentials)
  } catch (Exception ex) {
    log.warn("Unable to lookup SCM credentials for $uri", ex.getMessage())
  }
  return null
}

/**
 * Tries to retrieve credentials for the given host by using configurations provided in
 * resources/credentials/ssh/credentials.json
 *
 * @param uri The uri to lookup the credentials for
 * @return The found Credential object or null when no credential object was found during auto lookup
 * @see com.dettonville.api.pipeline.credentials.Credential
 * @see com.dettonville.api.pipeline.credentials.CredentialParser
 * @see JsonLibraryResource
 * @see com.dettonville.api.pipeline.credentials.CredentialConstants
 */
Credential lookupSshCredential(String uri) {
  Logger log = new Logger("lookupSshCredential")
  // load the json
  JsonLibraryResource jsonRes = new JsonLibraryResource((DSL) this.steps, CredentialConstants.SSH_CREDENTIALS_PATH)
  try {
    JSON credentialJson = jsonRes.load()
    // parse the credentials
    CredentialParser parser = new CredentialParser()
    List<Credential> credentials = parser.parse(credentialJson)
    // try to find matching credential and return the credential
    PatternMatcher matcher = new PatternMatcher()
    return (Credential) matcher.getBestMatch(uri, credentials)
  } catch (Exception ex) {
    log.warn("Unable to lookup SSH credentials for $uri", ex.getMessage())
  }
  return null
}

