@Library('infra-jenkins-pipeline-libs')
import sendEmail

def PYTHON_SDK_IMAGE="artifactory.dev.dettonville.int:6555/com-dettonville-api/sdk-python:develop"

pipeline {

    agent {
        label 'DOCKER && DEVCLD-EUR'
    }

    parameters {
		string(name: 'ARTIFACT_VERSION', defaultValue: '1.0.0')
		booleanParam(name: 'PUBLISH_TEST_PYPI',defaultValue: true, description: 'Should this artifact be published to testPyPI')
		booleanParam(name: 'PUBLISH_PYPI', defaultValue: false, description: 'Should this artifact be deployed to PyPI')
		string(name: 'SWAGGER_DEFINITIONS_BRANCH', defaultValue: 'main', description: 'Which branch of service-swagger-definitions should be checked out')
    }

	options {
		disableConcurrentBuilds()
		overrideIndexTriggers(false)
	}

    stages{

        stage('Generate SDK') {
			agent {
				docker {
					image 'artifactory.dev.dettonville.int:6555/com-dettonville-api/sdk-gradle:develop'
				}
			}

            steps {
				script {
					echo "params.ARTIFACT_VERSION=${params.ARTIFACT_VERSION}"
					echo "params.SWAGGER_DEFINITIONS_BRANCH=${params.SWAGGER_DEFINITIONS_BRANCH}"

					dir('service-swagger-definitions') {
						checkout scm: [
							$class: 'GitSCM',
							branches: [[name: "${SWAGGER_DEFINITIONS_BRANCH}"]],
							userRemoteConfigs: [[credentialsId: 'dcapi_ci_vcs_user', url: 'https://gitrepository.dettonville.int/stash/scm/api/service-swagger-definitions.git']]
						]

						sh "find service-swagger-definitions/* -type f -name ${env.SWAGGER_FILE}"
						// TODO: the fileExists below does not work and it is not clear why - so using the find|grep kludge below - when time permits follow up on this
						// if (!fileExists("service-swagger-definitions/${env.SWAGGER_FILE}")) {
						if (sh(script: "find service-swagger-definitions/* -type f | grep -q ${env.SWAGGER_FILE}", returnStatus: true)) {
	                        echo "Did not find swaggerfile [service-swagger-definitions/${env.SWAGGER_FILE}] in branch [${SWAGGER_DEFINITIONS_BRANCH}]"
						    sh "find service-swagger-definitions/* -type f"
	                        currentBuild.result = 'FAILURE'
	                        return
	                    }

						def swagger = readYaml file: "${env.SWAGGER_FILE}"
						env.SWAGGER_ARTIFACT_ID = swagger.info."x-artifactId"
					}

					figlet "${env.SWAGGER_ARTIFACT_ID}"

					sh "./generate-sdk.sh ${params.ARTIFACT_VERSION} service-swagger-definitions/${env.SWAGGER_FILE} pythonSDK"
					stash name: 'generated-code', includes: 'generated-code/**'
				}
            }
        }

		stage('Test SDK') {

			environment {
				HOME = "."
			}

			steps {
				unstash 'generated-code'

				script {
				    echo "NODE_NAME = ${env.NODE_NAME}"

					dir("generated-code/${env.SWAGGER_ARTIFACT_ID}/python/sdk") {
						echo "#### Starting Tests #####"
						def sdk_dir = pwd()
						echo "###### sdk_dir=${sdk_dir}"

						def command="docker run --name sdk-python --rm --net=host -v ${sdk_dir}:/home/sdk ${PYTHON_SDK_IMAGE}"
						try {
							sh script: "${command}", returnStdout: true
						} finally {
							echo "#### Archive reports"
							junit "*.xml"
						}

					}
					stash name: 'pythonsdk-post-test'
				}
			}
		}


		stage('Publish SDK to Test PyPI') {
			agent {
				docker {
					image "${PYTHON_SDK_IMAGE}"
				}
			}

			environment {
				HOME = "."
			}

			when {
				expression { return params.PUBLISH_TEST_PYPI }
			}
			steps {
				figlet 'Publish - TestPyPI'

				script {
					unstash name: 'pythonsdk-post-test'

					def command="""
						cp ./.pypirc generated-code/${env.SWAGGER_ARTIFACT_ID}/python/sdk
						cd generated-code/${env.SWAGGER_ARTIFACT_ID}/python/sdk

						echo "#### CREATE ARTIFACTS ####"
						python setup.py sdist bdist_wheel &> package_setup.log

						# need to register the project by logging in the pypi and upload the PKG-INFO file.
						# for other project run this plan and then it fails the first time you will find in your hudson data folder the file PKG-INFO
						echo "#### PUBLISH PACKAGE TO TESTPYPI ####"
						curl --connect-timeout 10 -kI https://test.pypi.org
						twine upload --config-file ./.pypirc -r testpypi 'dist/*'
					"""

					sh script: "${command}", returnStdout: true
					sh 'find . -type f -name *.whl | sort'
					archiveArtifacts artifacts: '**/dist/*.whl'

				}

				echo "Released version ${ARTIFACT_VERSION} to TestPyPI"
			}
		}

		stage('Publish SDK to PyPI') {
			agent {
				docker {
					image "${PYTHON_SDK_IMAGE}"
				}
			}

			environment {
				HOME = "."
			}

			when {
				expression { return params.PUBLISH_PYPI }
			}
			steps {
				figlet 'Publish - PyPI'

				script {
					unstash name: 'pythonsdk-post-test'

					def command="""
						cp ./.pypirc generated-code/${env.SWAGGER_ARTIFACT_ID}/python/sdk
						cd generated-code/${env.SWAGGER_ARTIFACT_ID}/python/sdk

						echo "#### CREATE ARTIFACTS ####"
						python setup.py sdist bdist_wheel &> package_setup.log

						# need to register the project by logging in the pypi and upload the PKG-INFO file.
						# for other project run this plan and then it fails the first time you will find in your hudson data folder the file PKG-INFO
						echo "#### PUBLISH ARTIFACT TO PYPI ####"
						curl --connect-timeout 10 -kI https://pypi.org
						twine upload --config-file ./.pypirc -r pypi 'dist/*'
					"""

					sh script: "${command}", returnStdout: true
					sh 'find . -type f -name *.whl | sort'
					archiveArtifacts artifacts: '**/dist/*.whl'

				}

				echo "Released version ${ARTIFACT_VERSION} to PyPI"
			}
		}

    }

    post {
        changed {
			echo "#### Sending email"
            sendEmail(currentBuild, env)
        }
    }
}


