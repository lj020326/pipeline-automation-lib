
# Notes on how to develop/test pipelines

The following sections highlight the provisioning/PR steps that a project pipeline developer follows in order develop, enhance and test pipeline functionality using config-as-code practices.

Table of Contents
- Example 1 - How to add project folders with LDAP mapped group(s)
- Example 2 - How to add credential(s) to jenkins for pipeline usage/consumption
- Example 3 - How to modify jenkins controller/agent image(s)
- Example 4 - How to develop and test changes to a groovy pipeline library function


## Example 1 - How to add projects with LDAP mapped group(s)

### Prerequisite - security group or groups defined/setup already in LDAP

Each project team must first/already have an appropriate security group or groups defined/setup in LDAP.

The security group(s) have prefix `{{ team_name }}-{{ team_role }}` according to organization security group naming standards.

### Overview - Project update pipeline for all project Seed Jobs

The jenkins environment is set-up using config-as-code.

The jenkins controller is a docker container instance.

The projects seedjob job definition for the controllers/agents for all jenkins projects_ is located in the [seedjobs.groovy](https://github.com/lj020326/ansible-datacenter/blob/main/roles/bootstrap_docker_stack/templates/jenkins_jcac/job_dsl/seedjob.groovy).  

As can be seen, the projects seedjob logic sources all of the project definitions from the [jenkins repo jobs/jobdsl/templates folder](https://github.com/lj020326/pipeline-automation-lib/tree/main/jobs/jobdsl/templates/). 

Note that each project folder and respective project groovy file has a left-zero-padded two-digit numbering.

The seedjob logic will run each project and project groovy files in each alphanumeric sort order of the folder and then the respective groovy file.

This allows for handling the configurations for use cases where prior job dependencies must be performed before any subsequent dsl jobs.

For example, the __01_INFRA__ team project configuration for the project root folder with name `jobs/jobdsl/templates/01_INFRA/init00_project_root.groovy` will run before any of the other DSL job definitions with higher numbering in the same folder.

Each jenkins controller instance has a [project updates pipeline job](https://cicd01.dev.example.int/jenkins/job/ADMIN/job/bootstrap-projects/) that runs the [seedjobs.groovy](https://github.com/lj020326/ansible-datacenter/blob/main/roles/bootstrap_docker_stack/templates/jenkins_jcac/job_dsl/seedjob.groovy) __every 30 minutes__.

### Initial project folder provisioning steps

When the need exists for a project team to provisioning a new jenkins project folder workspace, the project team admin creates a PR branch into the jenkins repo with the necessary folder addition.

For the following example we assume an application project team with application name __abc123__ wants to provision a new project team folder workspace.

Then project team admin will 

1) create a `PR branch` from the `main` branch of the [jenkin pipeline repo](https://github.com/lj020326/pipeline-automation-lib).
2) create a new folder in the jenkins repo in the `jobs/jobdsl/templates` directory for the new app team folder.<br>
   The folder name should abide by any standard naming convention per the core jenkins team.<br>
   Currently, all team folders are prefixed with a padded 2-digit consecutive number to determine the order that the project folders will get updated/created __every 30 minutes__. <br>
   E.g., the __abc123__ team would set up a folder directory named `02_ABC123`.<br>
3) Create any/all job dsl groovy file(s) necessary to fully create the project folder hierarchy used by the project team. <br>
   A simple start could be to simply copy another teams top level project root groovy file.
E.g., in this example, the abc123 team project admin copies the admin teams file:
```shell
$ mkdir -p jobs/jobdsl/templates/13_ABC123
$ cd jobs/jobdsl/templates/13_ABC123
$ cp -p ../00_ADMIN/init00_project_root.groovy init00_project_root.groovy
```

Note that the _admin project team_ has an LDAP security group named `infra-admin` referenced in the [Job DSL for ADMIN project folder LDAP group credentials](https://github.com/lj020326/pipeline-automation-lib/tree/main/jobs/jobdsl/templates/01_ADMIN/init00_project_root.groovy)

For the example, we assume the app team __abc123__ team has following __LDAP security groups__ already existing:
- `ABC123-Admin`
- `ABC123`

The project team admin now must change all references from the `infra-admin` to the `ABC123-Admin` __LDAP security group__ in the `jobdsl/templates/13_ABC123/init00_project_root.groovy`.

jobdsl/templates/13_ABC123/init00_project_root.groovy:
```groovy
#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String projectName = "ADMIN"

String projectFolder = projectName.toUpperCase()

String pipelineRepoUrl = "ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git"
String gitPipelineLibCredId = "bitbucket-ssh-jenkins"

// ref: https://blog.pavelsklenar.com/jenkins-creating-dynamic-project-folders-with-job-dsl/
// def adminGroup = "sg_${projectName}_admin"
// def devGroup = "sg_${projectFolder}_dev"
// def opsGroup = "sg_${projectFolder}_ops"

folder(projectFolder) {
    description "This project folder contains jobs for the ${projectName} project"
    properties {
      folderLibraries {
          libraries {
              // ref: https://issues.jenkins.io/browse/JENKINS-66402
              // ref: https://devops.stackexchange.com/questions/11833/how-do-i-load-a-jenkins-shared-library-in-a-jenkins-job-dsl-seed
              libraryConfiguration {
                  name("pipelineAutomationLib")
                  defaultVersion("main")
                  implicit(true)
                  includeInChangesets(false)
                  retriever {
                      modernSCM {
                          scm {
                              git {
                                  remote(pipelineRepoUrl)
                                  credentialsId(gitPipelineLibCredId)
                              }
                          }
                      }
                  }
              }
          }
      }
      authorizationMatrix {
        inheritanceStrategy {
            nonInheriting()
//             inheriting()
        }
        // ref: https://github.com/jenkinsci/matrix-auth-plugin/releases
        entries {
          user {
              name('admin')
              permissions([
                'Overall/Administer'
              ])
          }
          group {
              name('admin')
              permissions([
                'Overall/Administer'
              ])
          }
          group {
              name('infra-admin')
              permissions([
                'Overall/Administer'
              ])
          }
          group {
              name('Domain Admins')
              permissions([
                'Overall/Administer'
              ])
          }
          group {
              name('authenticated')
              permissions([
                'Overall/Read'
              ])
          }
        }
      }
    }
}

```

## Example 2 - How to add credential(s) to jenkins for pipeline usage/consumption

The jenkins environment is set-up using config-as-code.

The jenkins controller is a docker container instance.

The ansible role [`bootstrap_docker_stack`](https://github.com/lj020326/ansible-datacenter/blob/main/roles/docker_stack/README.md) sets up the docker container and any configurations used by the container. 

The credential settings are referenced in the following code locations:
- [jenkins_casc.yml.j2](https://github.com/lj020326/ansible-datacenter/blob/main/roles/docker_stack/templates/jenkins_jcac/jenkins_casc.yml.j2)
- [jenkins.env.j2](https://github.com/lj020326/ansible-datacenter/blob/main/roles/docker_stack/templates/jenkins_jcac/jenkins.env.j2)
- [docker_stack_jenkins_jcac.yml](https://github.com/lj020326/ansible-datacenter/blob/main/roles/docker_stack/vars/app-services/common/docker_stack_jenkins_jcac.yml)
- [vault.yml](https://github.com/lj020326/ansible-datacenter/blob/main/roles/docker_stack/vars/vault.yml)

When the need exists for a developer to add/update jenkins credential(s) for pipeline usage/consumption, the developer creates a PR branch along with the necessary credential additions.


## Example 3 - How to modify jenkins controller/agent image(s)

The jenkins environment is set-up using config-as-code.

The jenkins controller is a docker container instance.

The image definitions for the controllers/agents are located at:
- [docker-jenkins repo](https://github.com/lj020326/jenkins-docker)
- [docker-jenkins-agent repo](https://github.com/lj020326/jenkins-docker-agent)

The specification for the `__docker_stack__jenkins_jcac__image` variable used to set the image used in the config-as-code is defined in [docker_stack_jenkins_jcac.yml file](https://github.com/lj020326/ansible-datacenter/blob/main/roles/docker_stack/vars/app-services/common/docker_stack_jenkins_jcac.yml).

The pipeline that performs the image builds is here:
[docker-jenkins build pipeline](https://cicd01.dev.example.int/jenkins/job/INFRA/job/docker-build-jobs/job/docker-jenkins/job/main/)

When the need exists for a developer to enhance any of the jenkins images, the developer creates a PR branch for the [docker-jenkins repo](https://github.com/lj020326/jenkins-docker).


## Example 4 - How to develop and test changes to a groovy pipeline library function

In the following example, a pipeline developer needs to enhance and test changes to functionality used by the `project-update-jobs` pipelines for the `ocp_management` repository.

As can be viewed in the respective pipeline configuration below, the pipeline configuration/setup has the branch filtering setup for 'main', 'development' and 'release*' branches:

https://cicd01.example.int/jenkins/job/INFRA/job/project-update-jobs/job/ocp_management/configure

There are currently only 2 infrastructure related `jenkins-config-as-code` environments set up:

ENV | Instance URL
--- | ---
DEV | https://cicd01.example.int/jenkins/
SANDBOX | https://cicd01.dev.example.int/jenkins/

In order to develop and test, it is done using the sandbox test machine for cicd setup at https://cicd01.dev.example.int/jenkins/.


### Setup the pipeline configuration in sandbox

The developer should first make sure the respective pipeline configuration is setup in the sandbox machine.

In this specific example using the `project-update-jobs` pipelines, one can see after logging in that those pipelines are not setup in sandbox.

The developer can view the existing yaml pipeline job configuration at:

https://bitbucket.example.int/projects/AAP/repos/pipeline-automation-lib/browse/jobs/jobdsl/templates/01_INFRA/config.project-update-jobs.yml#8-17

The yaml configuration shows that the `project-update-jobs` pipeline jobs are only setup for the DEV environment running at https://cicd01.example.int/jenkins/:

```yaml
---
pipelineConfig:
  baseFolder: "INFRA/project-update-jobs"
  gitCredentialsId: "bitbucket-ssh-jenkins"
  jobScript: pipelines/tagGitBranches.groovy
  periodicFolderTriggerInterval: "2m"
  runEnvMap:
    DEV:
      environment: DEV
#    PROD:
#      environment: PROD
  branchesToBuild:
  - "main"
  - "development"
  - "release*"
```

The developer can modify this to define the same jobs to run on the sandbox machine at https://cicd01.dev.example.int/jenkins/ by adding the sandbox environment:

```yaml
---
pipelineConfig:
  baseFolder: "INFRA/project-update-jobs"
  gitCredentialsId: "bitbucket-ssh-jenkins"
  jobScript: pipelines/tagGitBranches.groovy
  periodicFolderTriggerInterval: "2m"
  runEnvMap:
    SANDBOX:
      environment: SANDBOX
    DEV:
      environment: DEV
#    PROD:
#      environment: PROD
  branchesToBuild:
  - "main"
  - "development"
  - "release*"
```

Then the developer must add the branch that the developer intends to use to run tests to develop the respective pipeline function.

In this example, the developer's branch is to `develop-lj` as seen below:

```yaml
---
pipelineConfig:
  baseFolder: "INFRA/project-update-jobs"
  gitCredentialsId: "bitbucket-ssh-jenkins"
  jobScript: pipelines/tagGitBranches.groovy
  periodicFolderTriggerInterval: "2m"
  runEnvMap:
    SANDBOX:
      environment: SANDBOX
    DEV:
      environment: DEV
#    PROD:
#      environment: PROD
  branchesToBuild:
  - "main"
  - "development"
  - "release*"
  - "develop-lj"
```


The developer then commits and pushes this modification to the `development` branch of https://bitbucket.example.int/projects/AAP/repos/pipeline-automation-lib/.

### Run the `ADMIN/bootstrap-projects` pipeline job to sync with git code definition

After making the enhancements/modifications per the prior section, the developer can now run the [`ADMIN/bootstrap-projects`](https://cicd01.dev.example.int/jenkins/job/ADMIN/job/bootstrap-projects/) pipeline job to bootstrap/synchronize the pipeline projects with the git code defined project configurations.

The pipeline synchronization job usually completes within 10 seconds.

> * NOTE: the [`ADMIN/bootstrap-projects`](https://cicd01.dev.example.int/jenkins/job/ADMIN/job/bootstrap-projects/) synchronization job runs on a scheduled basis every 30 minutes. 

After the `ADMIN/bootstrap-projects` pipeline job is complete, the developer can confirm that the job folder and respective pipeline jobs exist.

### Enhance the respective pipeline library function

In the example we are using, the developer seeks to enhance the function used in the `pipelines/tagGitBranches.groovy` wrapper function.

The [ocp_management repo](https://bitbucket.example.int/projects/AAP/repos/ocp_management/browse/pipelines/tagGitBranches.groovy) pipeline wrapper function invokes the pipeline library function `runAnsibleProjectUpdate`:

```groovy
#!/usr/bin/env groovy

// tag branches for ansible and then sync ansible projects in environments.
runAnsibleProjectUpdate("OpenShift Management")
```

All pipeline library functions exist in the pipeline library `vars` directory at: https://bitbucket.example.int/projects/AAP/repos/pipeline-automation-lib/browse/vars

The groovy source for the `runAnsibleProjectUpdate` library function can be found at [`runAnsibleProjectUpdate.groovy`](https://bitbucket.example.int/projects/AAP/repos/pipeline-automation-lib/browse/vars/runAnsibleProjectUpdate.groovy) 

### Mapping from jenkins runtime environment to pipeline automation library branches

The infrastructure project folder configuration contains the pipeline library definition:
https://cicd01.dev.example.int/jenkins/job/INFRA/configure

For the DEV and SANDBOX instances, both folders are set to the pipeline library `development` branch.

The mapping of pipeline automation library repo branch to each jenkins environment is defined in git code below.

['jobdsl/templates/01_INFRA/config.infra-jobs-root.yml'](https://bitbucket.example.int/projects/AAP/repos/pipeline-automation-lib/browse/jobs/jobdsl/templates/01_INFRA/config.infra-jobs-root.yml):
```yaml
---

pipelineConfig:
  pipelineRepoUrl: "ssh://git@bitbucket.example.int:7999/aap/pipeline-automation-lib.git"
  gitCredentialsId: "bitbucket-ssh-jenkins"
  envConfigs:
    PROD:
      pipelineLibraryBranch: "main"
    QA:
      pipelineLibraryBranch: "main"
    DEV:
      pipelineLibraryBranch: "development"
    SANDBOX:
      pipelineLibraryBranch: "development"

```


As can be seen in the definition above, the "DEV" and "SANDBOX" jenkins environments both point to the [pipeline-automation-lib](https://bitbucket.example.int/projects/AAP/repos/pipeline-automation-lib/) `development` branch.

The developer can create a developer-specific or feature branch and temporarily change/set the `SANDBOX` environment to point to the developer-specific branch for the feature development and testing purposes. 

ENV | Instance URL | pipeline-automation-lib branch
--- | --- | ---
DEV | https://cicd01.example.int/jenkins/ | development
SANDBOX | https://cicd01.dev.example.int/jenkins/ | develop-lj

And modify the `jobs/jobdsl/templates/01_INFRA/config.infra-jobs-root.yml` with the respective configuration change:
```yaml
---

pipelineConfig:
  pipelineRepoUrl: "ssh://git@bitbucket.example.int:7999/aap/pipeline-automation-lib.git"
  gitCredentialsId: "bitbucket-ssh-jenkins"
  envConfigs:
    PROD:
      pipelineLibraryBranch: "main"
    QA:
      pipelineLibraryBranch: "main"
    DEV:
      pipelineLibraryBranch: "development"
    SANDBOX:
      pipelineLibraryBranch: "develop-lj"

```

After making the enhancements/modifications to the infrastructure folder configuration, the developer can now re-run the [`ADMIN/bootstrap-projects`](https://cicd01.dev.example.int/jenkins/job/ADMIN/job/bootstrap-projects/) pipeline job to bootstrap/synchronize the pipeline projects with the git code defined project configurations.

After the sync job has completed, the developer can then confirm that the correct pipeline library branch is defined/configured at:
https://cicd01.dev.example.int/jenkins/job/INFRA/configure

### Enhance the developer branch

The developer can now make enhancements to the pipeline-automation-lib repo developer branch and test in the [respective pipeline in the `SANDBOX` jenkins instance](https://cicd01.dev.example.int/jenkins/job/INFRA/job/project-update-jobs/job/ocp_management).

Say the developer starts by adding a log message of "********** TESTING ********** " to the pipeline function as a start.

runAnsibleProjectUpdate.groovy before update:
```groovy
...

def call(String project) {
    Logger log = new Logger(this, LogLevel.INFO)
    String credentialId = 'bitbucket-ssh-jenkins'
    String sandbox
    pipeline {
        agent any
        stages {
            stage('Tagging Branches') {
                steps {
                    script {
                        def tag_prefix = sh(returnStdout: true, script: 'date +%Y.%-m.').trim()
                        def current_tags = sh(returnStdout: true, script: "git tag")
                        log.info("${GIT_BRANCH}")
                        String branch = "${GIT_BRANCH}"
...
```


After:
```groovy
...

def call(String project) {
    Logger log = new Logger(this, LogLevel.INFO)
    String credentialId = 'bitbucket-ssh-jenkins'
    String sandbox
    pipeline {
        agent any
        stages {
            stage('Tagging Branches') {
                steps {
                    script {
                        log.info("********** TESTING **********")
                        def tag_prefix = sh(returnStdout: true, script: 'date +%Y.%-m.').trim()
                        def current_tags = sh(returnStdout: true, script: "git tag")
                        log.info("GIT_BRANCH=${GIT_BRANCH}")
                        String branch = "${GIT_BRANCH}"
...
```

Now the developer can test the enhanced function by starting the pipeline.  

This can be done simply by pressing the "Build Now" button at the developer's branch under https://cicd01.example.int/jenkins/job/INFRA/job/project-update-jobs/job/ocp_management/.

Then use the console to view the job output:

```shell
...
[Pipeline] // stage
[Pipeline] withEnv
[Pipeline] {
[Pipeline] stage
[Pipeline] { (Tagging Branches)
[Pipeline] script
[Pipeline] {
[INFO] runAnsibleProjectUpdate : ********** TESTING **********
[INFO] runAnsibleProjectUpdate : GIT_BRANCH=development
...
```

### Online groovy debugger

A pipeline developer can utilize an [online groovy debugger located here](https://onecompiler.com/groovy/42axt5atm).
This is useful for performing simple groovy development, tests and sharing of ideas/concepts among groovy developers.

