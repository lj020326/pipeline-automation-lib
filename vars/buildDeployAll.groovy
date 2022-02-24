#!/usr/bin/env groovy

//def call(body) {
//
//    def config = [:]
//
//    body.resolveStrategy = Closure.DELEGATE_FIRST
//    body.delegate = config
//    body()

def call(Map config=[:]) {

    echo "config.Branch=${config.Branch}"

    node('DEVCLD-LIN7') {
        Boolean successBuild = true
        echo "env.BRANCH_NAME=${env.BRANCH_NAME}"

        buildCode("DEVPORTAL")
        buildCode("DEVPORTAL-FRONTEND")
        buildCode("ENCRYPTION-KEY-SERVICES")
        buildCode("MONITORING-SERVICE")

        deployArtifact("PORTAL-DRIVER")

    }

} // body


/**
 * Build the Automation code for a Given Application
 **/
void buildCode(String appName) {
    stage appName
    figlet appName

    Repository repository = getRepositoryFromAppName(appName)
    checkoutAutomationCode(repository)
    compileCode(repository)
}

/**
 * Get Repository details based on plan name
 * @return: Application Object
 **/
Application getRepositoryFromAppName(String appName) {
    switch (appName) {
        case "DEVPORTAL":
            return new Repository(env.DEVPORTAL_GIT_PATH, "master", env.DEVPORTAL_GIT_FOLDER, env.DEVPORTAL_GIT_EXEC_FOLDER)
        case "DEVPORTAL-FRONTEND":
            return new Repository(env.DEVPORTAL-FRONTEND_GIT_PATH, "master", env.DEVPORTAL-FRONTEND_GIT_FOLDER, env.DEVPORTAL-FRONTEND_GIT_EXEC_FOLDER)
        case "ENCRYPTION-KEY-SERVICES":
            return new Repository(env.ENCRYPTION-KEY-SERVICES_GIT_PATH, "master", env.ENCRYPTION-KEY-SERVICES_GIT_FOLDER, env.ENCRYPTION-KEY-SERVICES_GIT_EXEC_FOLDER)
        case "MONITORING-SERVICE":
            return new Repository(env.MONITORING-SERVICE_GIT_PATH, "master", env.MONITORING-SERVICE_GIT_FOLDER, env.MONITORING-SERVICE_GIT_EXEC_FOLDER)
        case "QA-UI-AUTOMATION-HARNESS":
            return new Repository(env.QA-UI-AUTOMATION-HARNESS_GIT_PATH, "master", env.QA-UI-AUTOMATION-HARNESS_GIT_FOLDER, env.QA-UI-AUTOMATION-HARNESS_GIT_EXEC_FOLDER)
        default:
            error "Application '" + appName +"' not found!"
    }
}

/**
 * Checkout Automation Code
 **/
void checkoutAutomationCode (Repository repository) {
    sh 'rm -rf'
    checkout poll: false, scm: [
            $class: 'GitSCM', branches: [[name: repository.branch]],
            extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: repository.checkoutDir]],
            userRemoteConfigs: [[url: repository.url]]
    ]
}

/**
 * Build the automation project
 */
void compileCode(Repository repository) {
    dir (repository.checkoutDir) {
//        sh  "${env.M3}/bin/mvn clean install -Djbehave.skip.tests=true"
        sh  "${env.M3}/bin/mvn clean install"
    }
}

/**
 * Build the automation project
 */
void deployArtifact(String appName) {
    stage "Deploy " + appName
    figlet "Deploy"

    Repository repository = getRepositoryFromAppName(appName)

    dir (repository.checkoutDir) {
        sh  "${env.M3}/bin/mvn clean deploy"
    }
}


/**
 * GIT Repository information
 **/
class Repository implements Serializable {
    String url
    String branch
    String checkoutDir
    String executionDir

    Repository(String url, String branch, String checkoutDir, String executionDir) {
        this.url = url
        this.branch = branch
        this.checkoutDir = checkoutDir
        this.executionDir = executionDir
    }
}

