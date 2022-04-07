#!/usr/bin/env groovy

/*-
 * #%L
 * dettonville.org
 * %%
 * Copyright (C) 2018 dettonville.org DevOps
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
        def dbPassword = 'someReallyStrongPwd123'
        /* Build mssql image */
        docker.image("microsoft/mssql-server-linux:${version}").withRun("-e \"ACCEPT_EULA=Y\" -e \"SA_PASSWORD=${dbPassword}\"") { db ->
            withEnv(['DB_USERNAME=sa', "DB_PASSWORD=${dbPassword}", "DB_HOST=db", "DB_PORT=1433"]) {
                variables.db = db;
                nextClosure(variables)
            }
        }
    }
}
