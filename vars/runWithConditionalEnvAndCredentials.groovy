#!/usr/bin/env groovy

// import com.dettonville.pipeline.utils.logging.LogLevel
// import com.dettonville.pipeline.utils.logging.Logger
//
// // ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
// import groovy.transform.Field
// @Field Logger log = new Logger(this)

// Wrapper function to conditionally apply environment variables and credentials
def call(envVars, secretVars, body) {
    // If both lists are empty, just execute the body
    if (envVars.isEmpty() && secretVars.isEmpty()) {
        body()
        return
    }

    // Conditionally apply withEnv
    def withEnvClosure = {
        if (envVars.isEmpty()) {
            body()
        } else {
            withEnv(envVars) {
                body()
            }
        }
    }

    // Conditionally apply withCredentials
    if (secretVars.isEmpty()) {
        withEnvClosure()
    } else {
        withCredentials(secretVars) {
            withEnvClosure()
        }
    }
}
