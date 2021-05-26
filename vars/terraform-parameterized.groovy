
// ref: https://gist.github.com/fortunecookiezen/b3bc3214a07a14529608857d078b32dd

pipeline {
    agent {
        docker {
            image 'hashicorp/terraform:light'
            args '--entrypoint="" -u root'
        } 
    }
    environment {
        AWS_ACCESS_KEY_ID = credentials('DEV_AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = credentials('DEV_AWS_SECRET_ACCESS_KEY')
    }
    parameters {
        choice(
            choices: ['plan', 'apply', 'show', 'preview-destroy', 'destroy'],
            description: 'Terraform action to apply',
            name: 'action')
        choice(
            choices: ['dev', 'test', 'prod'],
            description: 'deployment environment',
            name: 'ENVIRONMENT')
        string(defaultValue: "us-east-1", description: 'aws region', name: 'AWS_REGION')
        string(defaultValue: "fcz", description: 'application system identifier', name: 'ASI')
    }
    stages {
        stage('init') {
            steps {
                withCredentials([string(credentialsId: 'DEV_AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'DEV_AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY')])
                {
                    sh 'terraform init -no-color -backend-config="bucket=${ASI}-${ENVIRONMENT}-tfstate" -backend-config="key=${ASI}-${ENVIRONMENT}/terraform.tfstate" -backend-config="region=${AWS_REGION}"'
                }
            }
        }
        stage('validate') {
            steps {
                sh 'terraform validate -no-color'
            }
        }
        stage('plan') {
            when {
                expression { params.action == 'plan' || params.action == 'apply' }
            }
            steps {
                sh 'terraform plan -no-color -input=false -out=tfplan -var "aws_region=${AWS_REGION}" --var-file=environments/${ENVIRONMENT}.vars'
            }
        }
        stage('approval') {
            when {
                expression { params.action == 'apply'}
            }
            steps {
                sh 'terraform show -no-color tfplan > tfplan.txt'
                script {
                    def plan = readFile 'tfplan.txt'
                    input message: "Apply the plan?",
                    parameters: [text(name: 'Plan', description: 'Please review the plan', defaultValue: plan)]
                }
            }
        }
        stage('apply') {
            when {
                expression { params.action == 'apply' }
            }
            steps {
                sh 'terraform apply -no-color -input=false tfplan'
            }
        }
        stage('show') {
            when {
                expression { params.action == 'show' }
            }
            steps {
                sh 'terraform show -no-color'
            }
        }
        stage('preview-destroy') {
            when {
                expression { params.action == 'preview-destroy' || params.action == 'destroy'}
            }
            steps {
                sh 'terraform plan -no-color -destroy -out=tfplan -var "aws_region=${AWS_REGION}" --var-file=environments/${ENVIRONMENT}.vars'
                sh 'terraform show -no-color tfplan > tfplan.txt'
            }
        }
        stage('destroy') {
            when {
                expression { params.action == 'destroy' }
            }
            steps {
                script {
                    def plan = readFile 'tfplan.txt'
                    input message: "Delete the stack?",
                    parameters: [text(name: 'Plan', description: 'Please review the plan', defaultValue: plan)]
                }
                sh 'terraform destroy -no-color -force -var "aws_region=${AWS_REGION}" --var-file=environments/${ENVIRONMENT}.vars'
            }
        }
    }
}