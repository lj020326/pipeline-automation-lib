# Examples

## Example 1: Building a maven project with notifications

```groovy
import com.dettonville.api.pipeline.model.Tool
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*

Map config = [
    (SCM): [
        (SCM_URL): 'git@git.yourcompany.tld:group/project.git'
    ],
    (TOOLS): [
        [ (TOOL_NAME): 'apache-maven3', (TOOL_TYPE): Tool.MAVEN ],
        [ (TOOL_NAME): 'jdk8', (TOOL_TYPE): Tool.JDK ]
    ],
    (MAVEN): [
        (MAVEN_GOALS): [ "clean", "install" ]
    ],
    (LOGLEVEL): LogLevel.INFO
]

// surround by try and catch
try {
    // initialize the logger
    Logger.init(steps, config)
    node() {
        // setup the tools
        setupTools(config)
        // to the checkout
        checkoutScm(config)
        // execute maven
        execMaven(config)
    }
    currentBuild.result = "SUCCESS"
} catch (Exception ex) {
    currentBuild.result = "FAILED"
    throw ex
} finally {
    notifyMail(config)
}
```
