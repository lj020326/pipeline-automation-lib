#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.DockerUtil

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
//@Field Logger log = new Logger(this, LogLevel.INFO)
@Field Logger log = new Logger(this)

// ref: https://gist.github.com/fortunecookiezen/b3bc3214a07a14529608857d078b32dd

def call(Map params=[:]) {

    log.info("Loading Default Configs")

    Map config=loadPipelineConfig(params)

    List secretVars = [
        string(credentialsId: 'vmware-vcenter-password', variable: 'VSPHERE_PASSWORD'),
        string(credentialsId: 'vmware-vcenter-password', variable: 'VM_SSH_PASSWORD'),
    ]

    pipeline {

        agent {
            node {
                // https://stackoverflow.com/questions/68830925/jenkins-missing-node-label-master-after-v-2-307-upgrade
                label "built-in"
            }
        }

        stages {
            //      stage('fetch_latest_code') {
            //        steps {
            ////          git credentialsId: '17371c59-6b11-42c7-bb25-a37a9febb4db', url: 'https://github.com/PrashantBhatasana/terraform-jenkins-ec2'
            //          git credentialsId: 'infra-jenkins-git-user', url: 'https://gitea.admin.dettonville.int:8443/infra/terraform-jenkins.git'
            //        }
            //      }

            stage('Terraform Lint') {
                steps {
                    // ref: https://github.com/pwelch/test-workspace/blob/master/.github/workflows/terraform.yml
                    ansiColor('xterm') {
                        sh 'terraform fmt'
                    }
                }
            }

            stage('Terraform Init') {
                steps {
                    // ref: https://github.com/alexandarp/gitops-terraform-jenkins/blob/master/Jenkinsfile
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
                            sh 'terraform init'
                            // sh 'terraform init -backend-config="bucket=${ASI}-${ENVIRONMENT}-tfstate" -backend-config="key=${ASI}-${ENVIRONMENT}/terraform.tfstate" -backend-config="region=${AWS_REGION}"'
                        }
                    }
                }
            }

            stage('Terraform Validate') {
                steps {
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
                            sh 'terraform validate'
                        }
                    }
                }
            }

            // ref: https://medium.com/@suhasulun/deploying-to-aws-with-ansible-and-terraform-4c3be121ba51
            stage('Terraform Plan') {
                when {
                    expression { config.ACTION_PLAN || config.ACTION_APPLY }
                }
                steps {
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
                            // sh 'terraform plan -input=false -out=tfplan -var vsphere_password=${VSPHERE_PASSWORD} -var vm_ssh_password=${VM_SSH_PASSWORD}'
                            sh 'terraform plan -input=false -out=tfplan -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}"'
                        }
                    }
                }
            }

            stage('Approval') {
                when {
                    expression { config.ACTION_APPLY }
                }
                steps {
                    sh 'terraform show -no-color tfplan > tfplan.txt'
                    script {
                        // def userInput = input(id: 'confirm', message: 'Apply Terraform?', parameters: [[$class: 'BooleanParameterDefinition', defaultValue: false, description: 'Apply terraform', name: 'confirm']])
                        def plan = readFile 'tfplan.txt'
                        input message: "Apply the plan?",
                                parameters: [text(name: 'Plan', description: 'Please review the plan', defaultValue: plan)]
                    }
                }
            }

            stage('Terraform Apply') {
                when {
                    expression { config.ACTION_APPLY }
                }
                steps {
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
                            //                        sh 'terraform apply -input=false'
                            sh 'terraform apply -input=false tfplan'
                        }
                    }
                }
            }
            stage('show') {
                when {
                    expression { config.ACTION_SHOW }
                }
                steps {
                    sh 'terraform show'
                }
            }

            stage('preview-destroy') {
                when {
                    expression { config.ACTION_PREVIEW_DESTROY || config.ACTION_DESTROY }
                }
                steps {
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
                            sh 'terraform plan -destroy -out=tfplan -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}"'
                            sh 'terraform show -no-color tfplan > tfplan.txt'
                        }
                    }
                }
            }
            stage('destroy') {
                when {
                    expression { config.ACTION_DESTROY }
                }
                steps {
                    script {
                        def plan = readFile 'tfplan.txt'
                        input message: "Delete the stack?",
                                parameters: [text(name: 'Plan', description: 'Please review the plan', defaultValue: plan)]
                    }
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
//                            sh 'terraform destroy -force -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}" --var-file=environments/${ENVIRONMENT}.vars'
                            sh 'terraform destroy -force -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}"'
                        }
                    }
                }
            }


        }
    }
}

//@NonCPS
Map loadPipelineConfig(Map params) {
    Map config = [:]

    // copy immutable params maps to mutable config map
    params.each { key, value ->
        log.debug("params[${key}]=${value}")
//        key= Utilities.decapitalize(key)
        if (value!="") {
            config[key]=value
        }
    }

    config.get('logLevel', "INFO")

    log.setLevel(config.logLevel)

    log.info("log.level=${log.level}")

    log.info("params=${JsonUtils.printToJsonString(params)}")
    log.info("config=${JsonUtils.printToJsonString(config)}")

    return config
}
