#!/usr/bin/env groovy

import com.dettonville.pipeline.docker.*;
import com.dettonville.pipeline.docker.steps.Step;

def call(ProjectConfiguration projectConfig, def dockerImage) {
    return { variables ->
        List<Step> stepsA = projectConfig.steps.steps
        def links = variables.collect { k, v -> "--link ${v.id}:${k}" }.join(" ")
        dockerImage.inside(links) {
            stepsA.each { step ->
                stage(step.name) {
                    step.commands.each { command ->
                        sh command
                    }
                }
            }
        }
    }
}
