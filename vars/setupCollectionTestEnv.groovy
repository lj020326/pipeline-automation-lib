#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities
import com.dettonville.pipeline.utils.MapMerge
import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import static com.dettonville.pipeline.utils.ConfigConstants.*

// import jenkins.model.CauseOfInterruption.*
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

void call(Map config=[:]) {

//     log.enableDebug()
    log.info("config.collectionsBaseDir=${config.collectionsBaseDir}")

    sh """
        echo "Current working directory: \$(pwd)
        echo "Creating collection directory: ${config.targetCollectionDir}
        mkdir -p ${config.targetCollectionDir}

        # To avoid 'file changed as we read it' and ensure proper exclusion,
        # we'll list the contents of the current directory (excluding the target itself)
        # and pipe them to tar.
        # This ensures that tar only processes the actual source files and directories,
        # and the exclude patterns are matched against their names directly.

        # First, find all files and directories to include, excluding the target path
        # and the .git, docs, releases, ansible_collections directories.
        # Then, pipe this list to 'tar -T -' to archive them.
        # The -C . ensures tar's paths are relative to the current directory.

        find . -maxdepth 1 -mindepth 1 \\
            ! -path './.git' \\
            ! -path './docs' \\
            ! -path './releases' \\
            ! -path './ansible_collections' \\
            ! -path './collections' \\
            -print0 | tar -cf - --null -T - | (tar -xf - -C "${config.targetCollectionDir}")

        echo "Contents of target collection directory:"
        find "${config.targetCollectionDir}/" -type d
    """

    return config
}
