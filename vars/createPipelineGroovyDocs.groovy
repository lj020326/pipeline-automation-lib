def call(def packagePath) {
    node {
        stage('Prepare') {
            parallel(
                    Clean: {
                        deleteDir()
                    },
                    BuildDesc: {
                        currentBuild.description = "Build @${env.NODE_NAME}[${env.EXECUTOR_NUMBER}]"
                    }
            )
        }
        stage('Checkout') {
            checkout scm
        }
        stage('Generate Docs') {
            def linuxPrefix = ''
            if (isUnix()) {
                linuxPrefix = 'Linux '
            }

            def groovyHome = tool name: "${linuxPrefix}Groovy 2.4.5", type: 'hudson.plugins.groovy.GroovyInstallation'
            def javaHome = tool name: "${linuxPrefix}SUN JDK 1.8", type: 'jdk'

            def command = "${groovyHome}/bin/groovydoc -sourcepath src -verbose -d output ${packagePath}"

            steps.withEnv(["GROOVY_HOME=${groovyHome}", "JAVA_HOME=${javaHome}"]) {
                if (isUnix()) {
                    println "On Linux"
                    sh "ls ${groovyHome}/"
                    sh command
                } else {
                    println "On Windows"
                    bat "dir ${groovyHome}\\"
                    bat command
                }
            }

            publishHTML target: [
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : false,
                    reportDir            : 'output',
                    reportFiles          : 'index.html',
                    reportName           : 'GroovyDocs'
            ]
        }
    }
}