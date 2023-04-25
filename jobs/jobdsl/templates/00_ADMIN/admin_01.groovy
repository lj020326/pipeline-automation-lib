
String projectName = "ADMIN"

String projectFolder = projectName.toUpperCase()

def pipelineText = '''
pipeline {
    agent any
    stages {
        stage('SSH config') {
            steps {
                echo "Resetting host keys"
                sh 'mkdir -p ~/.ssh && chmod 700 ~/.ssh'
                sh 'ssh-keygen -R bitbucket.org && ssh-keyscan -t rsa bitbucket.org >> ~/.ssh/known_hosts'
                sh 'ssh-keygen -R gitea.admin.dettonville.int && ssh-keyscan -t rsa gitea.admin.dettonville.int >> ~/.ssh/known_hosts'
                echo "Finished bootstrapping admin"
            }
        }
    }
}
'''

pipelineJob("${projectFolder}/bootstrap-admin") {
    definition {
        cps {
            script(pipelineText)
            sandbox()
        }
    }
}

