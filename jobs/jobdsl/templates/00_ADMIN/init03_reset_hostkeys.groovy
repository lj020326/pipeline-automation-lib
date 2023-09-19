
String baseFolder = "ADMIN"

jobFolder = "${baseFolder}/reset-SSH-hostkeys"

String repoHost = "gitea.admin.dettonville.int"

// def pipelineText = """
// pipeline {
//     agent any
//     stages {
//         stage('SSH config') {
//             steps {
//                 echo "Resetting host keys"
//                 sh 'mkdir -p ~/.ssh && chmod 700 ~/.ssh'
//                 sh 'ssh-keygen -R bitbucket.org && ssh-keyscan -t rsa bitbucket.org >> ~/.ssh/known_hosts'
//                 sh 'ssh-keygen -R ${repoHost} && ssh-keyscan -t rsa ${repoHost} >> ~/.ssh/known_hosts'
//                 echo "Finished resetting bitbucket.org host keys"
//             }
//         }
//     }
// }
// """

pipelineJob(jobFolder) {
    parameters {
        stringParam("SshHosts", "", "SSH hosts\nE.g., 'host01.example.com', 'host01.example.int,host02.example.int'" )
    }
    definition {
        cps {
            script("resetSSHHostKeys()")
//             script(pipelineText)
            sandbox()
        }
    }
}
