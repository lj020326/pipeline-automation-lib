#!/usr/bin/env groovy

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

//
// ref: https://github.com/pycontribs/powertape/blob/master/vars/md5.groovy
// ref: https://gist.github.com/ikarius/299062
// ref: https://github.com/paulbunyannet/groovy-scripts/blob/master/hash.groovy
//

import static java.security.MessageDigest

@NonCPS
String generateMD5(String s, int limit = 0) {
    def x = MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
    if (limit) {
        return x.substring(0, limit)
    } else
        return x
}

@NonCPS
def generateMD5_B(String s) {
    MessageDigest digest = MessageDigest.getInstance("MD5")
    digest.update(s.bytes);
    new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
}

def call(String s, int limit = 0) {
//    return generateMD5(s, limit)
    return generateMD5_B(s)
}
