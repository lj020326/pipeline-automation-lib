#!/usr/bin/env groovy

import com.dettonville.api.pipeline.docker.*;

def call(ProjectConfiguration projectConfig, def version, def nextClosure) {
    return { variables ->
        /* Build postgres image */
        docker.image("postgres:${version}").withRun() { db ->
            withEnv(['DB_USERNAME=postgres', 'DB_PASSWORD=', "DB_HOST=db", "DB_PORT=5432"]) {
                variables.db = db;
                nextClosure(variables)
            }
        }
    }
}
