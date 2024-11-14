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

// ref: https://github.com/ska-sa/katcp-python/blob/master/Jenkinsfile
node('docker') {

    docker.image('cambuilder:latest').inside('-u root') {
        stage 'Cleanup workspace'
        sh 'chmod 777 -R .'
        sh 'rm -rf *'

        stage 'Checkout SCM'
        checkout([
                $class: 'GitSCM',
                branches: [[name: "refs/heads/${env.BRANCH_NAME}"]],
                extensions: [[$class: 'LocalBranch']],
                userRemoteConfigs: scm.userRemoteConfigs,
                doGenerateSubmoduleConfigurations: false,
                submoduleCfg: []
        ])

        stage 'Install & Unit Tests'
        timestamps {
            timeout(time: 30, unit: 'MINUTES') {
                try {
                    sh 'pip install . -U --pre'
                    sh 'python setup.py nosetests --with-xunit'
                } finally {
                    step([$class: 'JUnitResultArchiver', testResults: 'nosetests.xml'])
                }
            }
        }

        stage 'Build .whl & .deb'
        sh 'fpm -s python -t deb .'
        sh 'python setup.py bdist_wheel'
        sh 'mv *.deb dist/'

        stage 'Archive build artifact: .whl & .deb'
        archiveArtifacts artifacts: 'dist/*'

        stage 'Trigger downstream publish'
        build job: 'publish-local', parameters: [
                string(name: 'artifact_source', value: "${currentBuild.absoluteUrl}/artifact/dist/*zip*/dist.zip"),
                string(name: 'source_branch', value: "${env.BRANCH_NAME}")]
    }
}
