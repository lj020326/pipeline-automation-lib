#!/usr/bin/env groovy

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
