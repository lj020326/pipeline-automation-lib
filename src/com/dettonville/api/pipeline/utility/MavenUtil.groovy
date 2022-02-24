/**
 * com.dettonville.api.pipeline.utility is a collection of utilities to perform common pipeline tasks.
 */
package com.dettonville.api.pipeline.utility

/**
 * Utility that performs various maven tasks as well as other tasks tied to it such as git.
 * peforms tasks such as build, interact with sonar, run integration tests, git pull, etc.
 *
 * @Author grant.gortsema@dettonville.org
 */
class MavenUtil implements Serializable {

  /**
  * a reference to the pipeline that allows you to run pipeline steps in your shared libary
  */
  def steps


  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public MavenUtil(steps) {this.steps = steps}


  /**
  * Uses maven to deploy your code to artifactory. It assumes you have the correct parent pom set up in your project and
  * that you are in the directory with the pom. You also need to have deployer credentials set up in jenkins as they are required to deploy to artifactory.
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param deployerCredentails string that represents the id of the jenkins credentials used for your artifactory deployer
  * @param pomLoc optional string with location of your pom file
  */
  public void mvnDeployToArtifactory(script, String deployerCredentails, String pomLoc = null) {

    def pomFlag = ''
    if(pomLoc) pomFlag = " -f ${pomLoc}"

    steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${deployerCredentails}", usernameVariable: 'UNAME', passwordVariable: 'PASS']]) {
      steps.sh """
      export ARTIFACTORY_USR=${script.UNAME}
      export ARTIFACTORY_PSW=${script.PASS}
      ${steps.tool 'M3'}/bin/mvn -X clean deploy -DskipTests=true${pomFlag}
      """
    }
  }

  /**
  * Pulls shared ansible files from git repo and stashes them under ansible-workspace
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param branch the branch for the git repo. if this param is ommited it will use default branch
  *
  */
  public void gitPullSharedAnsibleFiles(script, String branch = null) {

    if(branch) steps.git branch: "${branch}", url: "https://gitrepository.dettonville.int/stash/scm/ca/shared-pct-ansible-libraries.git"
    else steps.git url: "https://gitrepository.dettonville.int/stash/scm/ca/shared-pct-ansible-libraries.git"

    steps.stash includes: '**', name: 'ansible-workspace'
  }

  /**
  * Pulls code from a git repo
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param repo is the URL to pull the git code from
  * @param branch the branch for the git repo. if this param is ommited it will use default branch
  *
  */
  public void gitPull(script, String repo, String branch = null) {
    steps.git branch: "${branch}", url: "${repo}"
  }

  /**
  * Runs static analysis on a project
  *
  * @todo arrive a code parity in parent poms and how sonar is run across projects. The pipeline code should be able to highlight the differences.
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param branch the git branch we are running sonar tests on. If this is null it will override the main sonar page for this project.
  * @param exclusions A String of exclusions to be passed to sonar.
  * @param pomLoc The location of the pom file relative to the workspace. Optional parameter. Will use pom current directory if it is left off.
  */
  public void runSonarWithExclusions(script, String branch,String exclusions, String pomLoc = null) {

    def pomFlag = ' '
    if(pomLoc) pomFlag = " -f ${pomLoc} "
    else pomLoc = 'pom.xml'

    def pom = steps.readMavenPom file: pomLoc
    def parentPart = pom.groupId ?: pom.parent.groupId
    def reportLocation = "https://fusion.dettonville.int/sonar/overview?id=${parentPart}%3A${pom.artifactId}"

    def branchParam = ' '
    if(branch) {
      branchParam = " -Dsonar.branch=${branch} "
      reportLocation += "%3A" + branch.replace('/','%2F')
    }

    def jenkinsBuildLink = script.env.BUILD_URL

    steps.sh "${steps.tool 'M3'}/bin/mvn${pomFlag}-Dsonar.projectDescription=\"Jenkins build for this report can be found at: ${jenkinsBuildLink}\" -Dsonar.exclusions=${exclusions} -Djavax.net.ssl.trustStore=${script.globalVars.sonarTestTrustStore}${branchParam}-Dsonar.findbugs.timeout=1200000 -Dsonar.jacoco.reportMissing.force.zero=true -Dsonar.scm.disabled=true sonar:sonar"
    steps.echo "changed - URL for sonar project is at: ${reportLocation}"
    steps.currentBuild.description = """
    <b>Sonar results </b>
    <a href=${reportLocation}>here</a>
    """
  }

  /**
  * Runs static analysis on a project
  *
  * @todo arrive a code parity in parent poms and how sonar is run across projects. The pipeline code should be able to highlight the differences.
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param branch the git branch we are running sonar tests on. If this is null it will override the main sonar page for this project.
  * @param pomLoc The location of the pom file relative to the workspace. Optional parameter. Will use pom current directory if it is left off.
  */
  public void runSonar(script, String branch,String pomLoc = null) {

    def pomFlag = ' '
    if(pomLoc) pomFlag = " -f ${pomLoc} "
    else pomLoc = 'pom.xml'

    def pom = steps.readMavenPom file: pomLoc
    def parentPart = pom.groupId ?: pom.parent.groupId
    def reportLocation = "https://fusion.dettonville.int/sonar/overview?id=${parentPart}%3A${pom.artifactId}"

    def branchParam = ' '
    if(branch) {
      branchParam = " -Dsonar.branch=${branch} "
      reportLocation += "%3A" + branch.replace('/','%2F')
    }

    def jenkinsBuildLink = script.env.BUILD_URL

    steps.sh "${steps.tool 'M3'}/bin/mvn${pomFlag}-Dsonar.projectDescription=\"Jenkins build for this report can be found at: ${jenkinsBuildLink}\" -Dsonar.exclusions=**/domain/**,**/constants/**,**/config/**,**/repository/**,**/types/**,**/mock/**,**/model/**,**/initdata/** -Djavax.net.ssl.trustStore=${script.globalVars.sonarTestTrustStore}${branchParam}-Dsonar.findbugs.timeout=1200000 -Dsonar.jacoco.reportMissing.force.zero=true -Dsonar.scm.disabled=true sonar:sonar"
    steps.echo "changed - URL for sonar project is at: ${reportLocation}"
    steps.currentBuild.description = """
    <b>Sonar results </b>
    <a href=${reportLocation}>here</a>
    """
  }

  /**
  * Run Spock tests in the pipeline.
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param repo location of repo that contains your tests
  * @param branch branch you want to run of the tests
  * @param profile profile of the tests to run (i.e. dev, stage, or production)
  */
  public void runSpockTests(script, String gitBranch, String gitRepo, String profile, String reportDir='target/spock-reports', String reportFiles='index.html',reportName='Spock Reports') {
    steps.deleteDir()
    steps.git branch: "${gitBranch}", url: "${gitRepo}"
    steps.sh "${steps.tool 'M3'}/bin/mvn clean test -Dmaven.test.failure.ignore=true"

    steps.publishHTML (target: [
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: "${reportDir}",
            reportFiles: "${reportFiles}",
            reportName: "${reportName}"
    ])
  }


  /**
  * Build a project using maven.
  * executes clean and package. Currently this also installs npm and karma until it can be backed into the agent.
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param pom A string that gives the path of the pom file relative to the directory.
  * @param mvnArgs is an optional parameter that indicate any maven arguments you want to pass to the method. These will be appended to the end of the mvn clean package command
  *
  */
  public void mvnPackage(script, String pom, String mvnArgs = null) {
    def mvnHome = steps.tool 'M3'
    def node_path = script.env.NODE6

    steps.echo "NODE PATH: ${node_path}"

    script.env.JAVA_HOME="${steps.tool 'JDK 1.8'}"

    if(!mvnArgs) mvnArgs = ''

    def buildString = "${mvnHome}/bin/mvn clean package -f ${pom} ${mvnArgs}".trim()

    steps.sh 'curl -s -o phantomjs-2.1.1.zip http://gitrepository.dettonville.int/artifactory/third-party/phantomjs/phantomjs/2.1.1/phantomjs-2.1.1.zip'
    steps.sh 'unzip -o phantomjs-2.1.1.zip'
    steps.sh 'mkdir -p bin'
    steps.sh 'mv phantomjs-static bin/phantomjs'
    steps.withEnv(["PATH+PHANTOMJS=${script.env.WORKSPACE}/bin"]) {
      steps.withEnv(["PATH+NODEJS=${node_path}/bin"]) {
        steps.withCredentials([steps.usernameColonPassword(
          credentialsId: 'artifactory_deployer', variable: 'afact_repo_creds')]) {
            steps.sh """curl '-u${script.afact_repo_creds}' \
              'http://gitrepository.dettonville.int/artifactory/api/npm/auth' > .npmrc"""
          }
          steps.sh 'pwd'
          steps.sh 'npm install karma --save-dev'
          steps.sh 'npm install karma-jasmine --save-dev'
          steps.sh 'npm install karma-chrome-launcher --save-dev'
          steps.sh 'npm install jasmine-core --save-dev'
          steps.sh 'npm install karma-cli'
          steps.sh 'npm install karma-coverage --save-dev'
          steps.sh 'npm install karma-junit-reporter --save-dev'
          steps.sh 'npm install karma-phantomjs-launcher --save-dev'

          steps.echo "BUILD STRING: ${buildString}"
          steps.sh buildString
        }
      }
  }

  /**
  * Build a project using maven.
  * executes clean and package. This method does not install karma and will be depreicated once there is no distinction in the shared code. This will happen when npm and karma are baked into the agent.
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param pom A string that gives the path of the pom file relative to the directory.
  * @param mvnArgs is an optional parameter that indicate any maven arguments you want to pass to the method. These will be appended to the end of the mvn clean package command
  *
  */
  public void mvnPackageNoKarma(script, String pom, String mvnArgs = null) {
    def mvnHome = steps.tool 'M3'
    def node_path = script.env.NODE6

    steps.echo "NODE PATH: ${node_path}"

    script.env.JAVA_HOME="${steps.tool 'JDK 1.8'}"

    if(!mvnArgs) mvnArgs = ''

    def buildString = "${mvnHome}/bin/mvn clean package -f ${pom} ${mvnArgs}".trim()
      steps.echo "BUILD STRING: ${buildString}"
      steps.sh buildString
  }

}
