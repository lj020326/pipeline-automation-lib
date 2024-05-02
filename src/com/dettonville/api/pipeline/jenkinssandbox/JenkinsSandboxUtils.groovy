
// ***************************
// ref: https://github.com/tomasbjerre/jenkins-configuration-as-code-sandbox/blob/master/src/se/bjurr/jenkinssandbox/JenkinsSandboxUtils.groovy

package com.dettonville.api.pipeline.jenkinssandbox

public class JenkinsSandboxUtils {
  /**
  * Get ip of host machine.
  */
  public static String getHostIp(steps) {
    steps.sh(
      returnStdout: true,
      script: '''ip route|awk '/default/ { print $3 }' '''
    )
    .trim()
  }
}
