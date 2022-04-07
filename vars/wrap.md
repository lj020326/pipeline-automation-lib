# Wrap

This part of the library contains utilites for wrappers such as
* ansiColor

# Table of contents

* [`color(Map config, Closure body)`](#colormap-config-closure-body)
    * [`color` Example](#color-example)

## `color(Map config, Closure body)`

This step is just a small adapter for the `ansiColor` step.
It uses the pipeline configuration to set the color mode

### `color` Example

```groovy
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import static com.dettonville.api.pipeline.utils.ConfigConstants.*
Map config = [
    (ANSI_COLOR) : ANSI_COLOR_XTERM
]

Logger.init(this, LogLevel.INFO)
Logger log = new Logger(this)
wrap.color(config) {
    log.info("i have a colorized loglevel!")
}

```

### Configuration options

The step has only one configuration option: `ConfigConstants.ANSI_COLOR`.
The value used in this configuration option is the color mode provided to the
`ansiColor` Step

```groovy
import static com.dettonville.api.pipeline.utils.ConfigConstants.*

wrap.color(
        (ANSI_COLOR): ANSI_COLOR_XTERM
    )
```

Available values:
* `ConfigConstants.ANSI_COLOR_XTERM`
* `ConfigConstants.ANSI_COLOR_GNOME_TERMINAL`
* `ConfigConstants.ANSI_COLOR_VGA`
* `ConfigConstants.ANSI_COLOR_CSS`

## Related classes:
* [`Logger`](../src/com/dettonville/dcapi/pipeline/utils/logging/Logger.groovy)
* [`LogLevel`](../src/com/dettonville/dcapi/pipeline/utils/logging/LogLevel.groovy)
