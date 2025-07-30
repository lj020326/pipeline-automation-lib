package com.dettonville.pipeline.utils
/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
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
@GrabResolver(name='artifactory', root='http://gitrepository.dettonville.int/artifactory/simple/atlassian-public-cache/',  m2Compatible=true)

@Grab('org.yaml:snakeyaml:1.17')

import org.yaml.snakeyaml.*

def yamlFile(data) {
    Yaml parser = new Yaml()
    def obj = [:]
    obj = parser.load(data)
    return obj
}