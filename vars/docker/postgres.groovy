#!/usr/bin/env groovy

/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2024 dettonville.org DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
