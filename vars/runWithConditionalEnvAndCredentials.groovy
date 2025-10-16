#!/usr/bin/env groovy

/////////////////
// NOTE:  "runWithConditionalEnvAndCredentials" handles the generalized use case of avoiding
// massive duplication with "if...then" logic required for conditional envvars and secrets
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
