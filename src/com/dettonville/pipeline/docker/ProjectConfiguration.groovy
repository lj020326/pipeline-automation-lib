package com.dettonville.pipeline.docker


import com.dettonville.pipeline.docker.docker.DockerConfiguration;
import com.dettonville.pipeline.docker.steps.Steps;

class ProjectConfiguration {
    def environment;
    def services;
    com.dettonville.pipeline.docker.steps.Steps steps;
    def dockerfile;
    def projectName;
    def buildNumber;
    com.dettonville.pipeline.docker.docker.DockerConfiguration dockerConfiguration;
    def env;
}
