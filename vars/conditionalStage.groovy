
import com.dettonville.pipeline.utils.logging.Logger
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

/**
 * Conditionally executes a stage and marks it as skipped if supported
 *
 * @param stageName The name of the stage
 * @param condition The condition
 * @param throwException Controls if the RejectedAccessException will be thrown
 * @param body The stage body
 */
void call(String stageName, Boolean condition, Boolean throwException = true, Closure body) {
  stage(stageName) {
    def config = [:]
    body.resolveStrategy = Closure.OWNER_FIRST
    body.delegate = config

    if (condition) {
      log.debug("condition evaluated to true, executing stage '$stageName'")
      body()
    } else {
      log.debug("condition evaluated to false, skipping stage ''$stageName''")
      try {
        Utils.markStageSkippedForConditional(stageName)
      } catch (RejectedAccessException ex) {
        log.warn("The stage '$stageName' was skipped, but the the Jenkins sandbox does not allow to mark the stage as skipped. You can approve this signature below ${JENKINS_URL}scriptApproval.")
        if (throwException) {
          throw ex
        }
      }
    }
  }
}
