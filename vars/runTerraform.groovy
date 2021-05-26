#!/usr/bin/env groovy
import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.MapMerge
import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger
import com.dettonville.api.pipeline.utils.DockerUtil


// ref: https://blog.nimbleci.com/2016/08/31/how-to-build-docker-images-automatically-with-jenkins-pipeline/
// ref: https://mike42.me/blog/2019-05-how-to-integrate-gitea-and-jenkins
// ref: https://github.com/jenkinsci/pipeline-examples/pull/83/files
// ref: https://www.jenkins.io/doc/book/pipeline/docker/

def call(Map params=[:]) {

//    Logger.init(this, LogLevel.INFO)
    Logger.init(this)
    Logger log = new Logger(this)

    log.info("Loading Default Configs")

//    TF_VARS_vsphere_password
//    TF_VARS_vm_ssh_password
//List secretVars = [
//      string(credentialsId: 'vmware-vcenter-password', variable: 'TF_VARS_vsphere_password'),
//      string(credentialsId: 'vmware-vcenter-password', variable: 'TF_VARS_vm_ssh_password'),
//]
    List secretVars = [
            string(credentialsId: 'vmware-vcenter-password', variable: 'VSPHERE_PASSWORD'),
            string(credentialsId: 'vmware-vcenter-password', variable: 'VM_SSH_PASSWORD'),
    ]

    pipeline {

        agent {
            node {
                label "master"
            }
        }
        parameters {
            booleanParam(name: 'ACTION_PLAN', defaultValue: true, description: 'Run terraform plan action')
            booleanParam(name: 'ACTION_APPLY', defaultValue: true, description: 'Run terraform apply action')
            booleanParam(name: 'ACTION_SHOW', defaultValue: true, description: 'Run terraform show action')
            booleanParam(name: 'ACTION_PREVIEW_DESTROY', defaultValue: false, description: 'Run terraform preview destroy action')
            booleanParam(name: 'ACTION_DESTROY', defaultValue: false, description: 'Run terraform destroy action')
            choice(
                    choices: ['dev', 'test', 'prod'],
                    description: 'deployment environment',
                    name: 'ENVIRONMENT')
        }

        stages {
            //      stage('fetch_latest_code') {
            //        steps {
            ////          git credentialsId: '17371c59-6b11-42c7-bb25-a37a9febb4db', url: 'https://github.com/PrashantBhatasana/terraform-jenkins-ec2'
            //          git credentialsId: 'dcapi-jenkins-git-user', url: 'https://gitea.admin.dettonville.int:8443/infra/terraform-jenkins.git'
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
                            //                        sh 'terraform init -backend-config="bucket=${ASI}-${ENVIRONMENT}-tfstate" -backend-config="key=${ASI}-${ENVIRONMENT}/terraform.tfstate" -backend-config="region=${AWS_REGION}"'
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
                    expression { params.ACTION_PLAN == true || params.ACTION_APPLY == true }
                }
                steps {
                    withCredentials(secretVars) {
                        ansiColor('xterm') {
                            //                        sh 'terraform plan -input=false -out=tfplan -var vsphere_password=${VSPHERE_PASSWORD} -var vm_ssh_password=${VM_SSH_PASSWORD}                 sh \'terraform destroy -force -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}" --var-file=environments/${ENVIRONMENT}.vars'
                            sh 'terraform plan -input=false -out=tfplan -var vsphere_password=${VSPHERE_PASSWORD} -var vm_ssh_password=${VM_SSH_PASSWORD}                 sh \'terraform destroy -force -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}"'
                        }
                    }
                }
            }

            stage('Approval') {
                when {
                    expression { params.ACTION_APPLY == true }
                }
                steps {
                    sh 'terraform show -no-color tfplan > tfplan.txt'
                    script {
                        //                    def userInput = input(id: 'confirm', message: 'Apply Terraform?', parameters: [[$class: 'BooleanParameterDefinition', defaultValue: false, description: 'Apply terraform', name: 'confirm']])
                        def plan = readFile 'tfplan.txt'
                        input message: "Apply the plan?",
                                parameters: [text(name: 'Plan', description: 'Please review the plan', defaultValue: plan)]
                    }
                }
            }

            stage('Terraform Apply') {
                when {
                    expression { params.ACTION_APPLY == true }
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
                    expression { params.ACTION_SHOW == true }
                }
                steps {
                    sh 'terraform show'
                }
            }

            stage('preview-destroy') {
                when {
                    expression { params.ACTION_PREVIEW_DESTROY == true || params.ACTION_DESTROY == true }
                }
                steps {
                    //                sh 'terraform plan -destroy -out=tfplan -var "aws_region=${AWS_REGION}" --var-file=environments/${ENVIRONMENT}.vars'
                    sh 'terraform plan -destroy -out=tfplan -var "aws_region=${AWS_REGION}"'
                    sh 'terraform show -no-color tfplan > tfplan.txt'
                }
            }
            stage('destroy') {
                when {
                    expression { params.ACTION_DESTROY == true }
                }
                steps {
                    script {
                        def plan = readFile 'tfplan.txt'
                        input message: "Delete the stack?",
                                parameters: [text(name: 'Plan', description: 'Please review the plan', defaultValue: plan)]
                    }
                    //                sh 'terraform destroy -force -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}" --var-file=environments/${ENVIRONMENT}.vars'
                    sh 'terraform destroy -force -var "vsphere_password=${VSPHERE_PASSWORD}" -var "vm_ssh_password=${VM_SSH_PASSWORD}"'
                }
            }


        }
    }
}
