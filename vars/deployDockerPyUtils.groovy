import sendEmail
import pushToDockerRegistry

pipeline {
    agent {
        label 'DOCKER'
    }

    stages{
        stage('Publish') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dcapi_docker_registry_user', passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                    pushToDockerRegistry(
                            "docker-pyutils",
                            "${env.DOCKER_REGISTRY}",
                            "${DOCKER_USERNAME}",
                            "${DOCKER_PASSWORD}",
                            "docker/pyutils/Dockerfile"
                    )
                }
            }
        }
    }

    post {
        changed {
            sendEmail(currentBuild, env)
        }
    }
}
