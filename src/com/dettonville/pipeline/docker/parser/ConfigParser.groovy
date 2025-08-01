package com.dettonville.pipeline.docker.parser

import com.dettonville.pipeline.docker.services.Base
import com.dettonville.pipeline.docker.services.Mssql
import com.dettonville.pipeline.docker.services.Postgres
import com.dettonville.pipeline.docker.services.Redis;
import com.dettonville.pipeline.docker.ProjectConfiguration;
import com.dettonville.pipeline.docker.docker.DockerConfiguration;
import com.dettonville.pipeline.docker.services.*;
import com.dettonville.pipeline.docker.steps.*;

class ConfigParser {

    private static String LATEST = 'latest';

    static ProjectConfiguration parse(def yaml, def env) {
        ProjectConfiguration projectConfiguration = new ProjectConfiguration();

        projectConfiguration.buildNumber = env.BUILD_ID;

        // parse the environment variables
        projectConfiguration.environment    = parseEnvironment(yaml.environment);

        // parse the execution steps
        projectConfiguration.steps          = parseSteps(yaml.steps);

        // parse the necessary services
        projectConfiguration.services   = parseServices(yaml.services);

        // load the dockefile
        projectConfiguration.dockerfile = parseDockerfile(yaml.config);

        // load the project name
        projectConfiguration.projectName = parseProjectName(yaml.config);

        projectConfiguration.env = env;

        projectConfiguration.dockerConfiguration = new DockerConfiguration(projectConfiguration: projectConfiguration);

        return projectConfiguration;
    }

    static def parseEnvironment(def environment) {
        if (!environment) {
            return "";
        }

        return environment.collect { k, v -> "${k}=${v}"};
    }

    static def parseSteps(def yamlSteps) {
        List<Step> steps = yamlSteps.collect { k, v ->
            Step step = new Step(name: k)

            // a step can have one or more commands to execute
            v.each {
                step.commands.add(it);
            }
            return step
        }
        return new Steps(steps: steps);
    }

    static def parseServices(def steps) {
        def services = [];

        steps.each {
            def service = it.tokenize(':')
            def version = service.size() == 2 ? service.get(1) : LATEST
            def instance = getServiceClass(service.get(0).capitalize())?.newInstance()

            services.add([service: instance, version: version])
        };

        services.add([service: new Base(), version: LATEST]);

        return services
    }

    static def getServiceClass(def name) {
        switch(name) {
            case "Postgres":
                return Postgres
                break
            case "Redis":
                return Redis
                break
            case "Mssql":
                return Mssql
                break
        }
    }

    static def parseDockerfile(def config) {
        if (!config || !config["dockerfile"]) {
            return "Dockerfile";
        }

        return config["dockerfile"];
    }

    static def parseProjectName(def config) {
        if (!config || !config["project_name"]) {
            return "woloxci-project";
        }

        return config["project_name"];
    }
}
