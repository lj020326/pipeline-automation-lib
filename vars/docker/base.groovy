#!/usr/bin/env groovy

import com.dettonville.api.pipeline.docker.*;

def call(ProjectConfiguration projectConfig, def _, def nextClosure) {
    return { variables ->
        def timeoutTime = projectConfig.env.TIMEOUT ?: 600 // timeout 10 minutes
        timeout(time: timeoutTime, unit: 'SECONDS') {
            withEnv(projectConfig.environment) {
                wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    nextClosure(variables)
                }
            }
        }
    }
}
