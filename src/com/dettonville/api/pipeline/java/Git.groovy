/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2018 Dettonville DevOps
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

package com.dettonville.api.pipeline.java

/**
 * Class for Git steps for the Java Pipeline.
 */
class Git implements Serializable {

    def steps
    def git

    /**
     * Create a new Git instance.
     * @param steps the groovy dsl context
     */
    Git(steps) {
        this.steps = steps
    }

    /**
     * Execute a shell command, while taking care of the current platform.
     * @param command the command to execute
     */
    private void shell(String command) {
        if(steps.isUnix()) {
            steps.sh command
        } else {
            steps.bat command
        }
    }

    /**
     * Create a tag with the provided tagName.
     * @param tagName the name for the tag
     */
    void createTag(String tagName) {
        assert tagName: 'I need tagName to be valid'
        def createTagCommand = "\"${git}\" tag -a ${tagName} -m \"Jenkins created version ${tagName}\""
        shell(createTagCommand)
    }

    /**
     * Push the given tag to the current remote used. <br>
     * <ul>
     *     <li>Checkout: https://p-bitbucket.nl.eu.dettonville.org:7999/scm/~c29874/pipeline-from-scm-tests.git</li>
     *     <li>Get credentials from Jenkins</li>
     *     <li>Push to https://{user}:{pass}@p-bitbucket.nl.eu.dettonville.org:7999/scm/~c29874/pipeline-from-scm-tests.git </li>
     *  </ul>
     *
     * @param tagName
     * @param credentialsId
     */
    void pushTagToRepo(String tagName, String credentialsId) {
        assert tagName: 'I need tagName to be valid'
        assert credentialsId: 'I need credentialsId to be valid'

        def gitCommand = "\"${git}\" config --get remote.origin.url > url.txt"
        shell(gitCommand)

        /*
         * example:
         * from: https://p-bitbucket.nl.eu.dettonville.org:7999/scm/~c29874/pipeline-from-scm-tests.git
         * to: https://{user}:{pass}@p-bitbucket.nl.eu.dettonville.org:7999/scm/~c29874/pipeline-from-scm-tests.git
         *
        */
        def urlFile = steps.readFile 'url.txt'
        def url = urlFile.trim()
        def repo = url.replace('https://', '')
        steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, passwordVariable: 'pss', usernameVariable: 'usr']]) {
            def gitAddRemoteCommand = "\"${git}\" remote add bb https://${steps.env.usr}:${steps.env.pss}@${repo}"
            def gitPushCommand = "\"${git}\" push -u bb ${tagName}"

            shell(gitAddRemoteCommand)
            shell(gitPushCommand)
        }
    }

    /**
     * Making sure we have a clean checkout in our workspace which is absolutely identical to the branch.
     * @param branchName the branchName
     */
    void cleanAndResetToBranch(String branchName) {
        assert branchName: 'I need branchName to be valid'

        // Clean any locally modified files and ensure we are actually on origin/$env.BRANCH_NAME
        // as a failed release could leave the local workspace ahead of origin/main
        def gitCommand = "\"${git}\" clean -f && \"${git}\" reset --hard origin/${branchName}"
        shell(gitCommand)
    }
}
