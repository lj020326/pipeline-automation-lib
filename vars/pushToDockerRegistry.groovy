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

/**
 * Publish to Docker Private Registry. Currently this registry is hosted on Dev Cloud (https://artifactory.dev.dettonville.int/artifactory/),
 * but there are plans to have a formal Docker Private Registry in Production also.
 *
 * ref: https://fusion.dettonville.int/confluence/display/ART/Docker+Images
 */
void call(String imageName, String registryUrl, String username, String password, String path = ".") {

    final String formattedBranchName = "${BRANCH_NAME}".replace('/', '-')

    sh """
    	docker -v
        docker login --username ${username} --password ${password} ${registryUrl}

        docker build -t ${imageName}:${formattedBranchName} -f ${path} .
        docker tag -f ${imageName}:${formattedBranchName} ${registryUrl}/com-dettonville-api/${imageName}:${formattedBranchName}
        docker push ${registryUrl}/com-dettonville-api/${imageName}:${formattedBranchName}
                            
        docker logout ${registryUrl}
    """
}