#!/usr/bin/env groovy


//node ('DEVCLD-LIN7') {
//    echo "NODE_NAME = ${env.NODE_NAME}"
//    echo "hello world!"
//}

def application = env.JOB_NAME.replaceAll('%2F', '/').split('/')[1].toUpperCase()
echo "application = ${application}"

//echo "jenkinsMaster=${env.BUILD_URL.split('/')[2].split(':')[0]}"

echo "jenkinsBaseUrl=${env.BUILD_URL.split('/jenkins/')[0]}"

node {
    echo "NODE_NAME = ${env.NODE_NAME}"

    echo "printenv="
    sh('printenv | sort')
}
