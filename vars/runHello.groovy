#!/usr/bin/env groovy

def call(Map params=[:]) {

    def timeoutValue = 60

    pipeline() {

        agent any

        options {
            timestamps()
            timeout(time: 5, unit: 'MINUTES') //Should not take longer than 5 minutes to run
        }

        stages {

            stage('Say Hello') {
                steps {
                    figlet 'Hello World'
                    echo 'Hello World'
                }
            }

            stage('Await Response') {
                steps {
                    script {
                        try {
                            timeout(time: timeoutValue as Integer, unit: 'SECONDS') {
                                env.CONFIRMED = input parameters: [
                                        [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: "Tick here to confirm world is here!\n if no response in ${timeoutValue} seconds - we will end the hello"]]
                            }
                        } catch(err) {
                            env.CONFIRMED = false
                            error "Timeout reached on ${env.BRANCH_NAME}. Pipeline failed: ${err}"
                        }

                        if (!Boolean.valueOf(env.CONFIRMED as String)) {
                            error "User chose not to confirm hello on ${env.BRANCH_NAME}. Pipeline failed."
                        }

                    }
                }
            }

        }

    }
}
