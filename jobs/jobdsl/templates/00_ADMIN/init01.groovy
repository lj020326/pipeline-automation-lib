
String projectName = "ADMIN"

String projectFolder = projectName.toUpperCase()

String repoHost = "gitea.admin.dettonville.int"

def pipelineText = """
pipeline {
    agent any
    stages {
        stage('SSH config') {
            steps {
                echo "Resetting host keys"
                sh 'mkdir -p ~/.ssh && chmod 700 ~/.ssh'
                sh 'ssh-keygen -R bitbucket.org && ssh-keyscan -t rsa bitbucket.org >> ~/.ssh/known_hosts'
                sh 'ssh-keygen -R ${repoHost} && ssh-keyscan -t rsa ${repoHost} >> ~/.ssh/known_hosts'
                echo "Finished bootstrapping admin"
            }
        }
    }
}
"""

pipelineJob("${projectFolder}/bootstrap-admin") {
    definition {
        cps {
            script(pipelineText)
            sandbox()
        }
    }
}

