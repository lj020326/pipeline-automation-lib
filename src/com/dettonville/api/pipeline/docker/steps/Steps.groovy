package com.dettonville.api.pipeline.docker.steps;

class Steps {
    List<Step> steps;

    def getVar(def dockerImage) {
        return "buildSteps"
    }
}
