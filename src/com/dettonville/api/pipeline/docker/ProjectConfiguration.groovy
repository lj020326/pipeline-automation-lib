package com.dettonville.api.pipeline.docker


import com.dettonville.api.pipeline.docker.docker.DockerConfiguration;
import com.dettonville.api.pipeline.docker.steps.Steps;

class ProjectConfiguration {
    def environment;
    def services;
    com.dettonville.api.pipeline.docker.steps.Steps steps;
    def dockerfile;
    def projectName;
    def buildNumber;
    com.dettonville.api.pipeline.docker.docker.DockerConfiguration dockerConfiguration;
    def env;
}
