#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String projectName = "INFRA"

String projectFolder = projectName.toUpperCase()
String basePath = "${projectFolder}/ansible"

String repoUrl = "git@bitbucket.org:lj020326/ansible-datacenter.git"
String pipelineRepoUrl = "ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git"

String gitCredentialsId = "bitbucket-ssh-jenkins"
String gitPipelineLibCredId = "bitbucket-ssh-jenkins"


List runEnvList = [
    'dev',
    'test',
    'prod'
]

List ansibleJobList = [
    [ansible_tag: 'bootstrap-ansible'],
    [ansible_tag: 'bootstrap-ansible-user', skip_untagged: true],
    [ansible_tag: 'bootstrap-docker'],
    [ansible_tag: 'bootstrap-docker-stack'],
    [ansible_tag: 'bootstrap-docker-swarm'],
    [ansible_tag: 'bootstrap-idrac'],
    [ansible_tag: 'bootstrap-jenkins-agent'],
    [ansible_tag: 'bootstrap-linux'],
    [ansible_tag: 'bootstrap-linux-core'],
    [ansible_tag: 'bootstrap-linux-firewall'],
    [ansible_tag: 'bootstrap-linux-logrotate'],
    [ansible_tag: 'bootstrap-linux-package'],
    [ansible_tag: 'bootstrap-linux-user'],
    [ansible_tag: 'bootstrap-mounts'],
    [ansible_tag: 'bootstrap-nfs'],
    [ansible_tag: 'bootstrap-nfs-client'],
    [ansible_tag: 'bootstrap-nfs-server'],
    [ansible_tag: 'bootstrap-ntp'],
    [ansible_tag: 'bootstrap-ntp-client'],
    [ansible_tag: 'bootstrap-ntp-server'],
    [ansible_tag: 'bootstrap-pip'],
    [ansible_tag: 'bootstrap-postfix'],
    [ansible_tag: 'bootstrap-postfix-client'],
    [ansible_tag: 'bootstrap-postfix-server'],
    [ansible_tag: 'bootstrap-python3'],
    [ansible_tag: 'bootstrap-signing-certs'],
    [ansible_tag: 'bootstrap-sshd'],
    [ansible_tag: 'bootstrap-vcenter', skip_untagged: true],
    [ansible_tag: 'bootstrap-vm', skip_untagged: true],
    [ansible_tag: 'bootstrap-vmware-esxi', skip_untagged: true],
    [ansible_tag: 'deploy-cacerts'],
    [ansible_tag: 'deploy-caroot'],
    [ansible_tag: 'deploy-registry-certs'],
    [ansible_tag: 'deploy-vm', skip_untagged: true],
    [ansible_tag: 'display-controller-vars'],
    [ansible_tag: 'display-hostvars']
]


// def scmFolderLibraryTraits = { folder ->
//   folder / 'properties' / 'org.jenkinsci.plugins.workflow.libs.FolderLibraries' / 'libraries' / 'org.jenkinsci.plugins.workflow.libs.LibraryConfiguration' / retriever / scm / traits {
//     'jenkins.plugins.git.traits.BranchDiscoveryTrait'()
//     'jenkins.plugins.git.traits.CleanBeforeCheckoutTrait'()
//   }
// }

// withTraits {
//     'jenkins.plugins.git.traits.BranchDiscoveryTrait'()
//     cleanBeforeCheckoutTrait("extension": ["deleteUntrackedNestedRepositories": true])
// }

// ref: https://gist.github.com/nocode99/d4c654514ff2b683af90d7dd5e0156e0
folder(basePath) {
    description 'This folder contains jobs to build vmware/vsphere templates'
    properties {
        folderLibraries {
            libraries {
                // ref: https://issues.jenkins.io/browse/JENKINS-66402
                // ref: https://devops.stackexchange.com/questions/11833/how-do-i-load-a-jenkins-shared-library-in-a-jenkins-job-dsl-seed
                libraryConfiguration {
                    name("pipeline-automation-lib")
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
                inheriting()
            }
        }
    }
//     configure scmFolderLibraryTraits
}

runEnvList.each { String run_environment ->

    ansibleJobList.each { Map job_config ->
        String ansible_tag = job_config.ansible_tag
        boolean skip_untagged = job_config.get('skip_untagged', false)

        folder("${basePath}/${run_environment}") {
            description 'This folder contains jobs to build VM templates for release ${run_environment}/${ansible_tag}'
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        pipelineJob("${basePath}/${run_environment}/${ansible_tag}") {
            description "Build VM template jobs for ${run_environment}/${ansible_tag}"
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
            keepDependencies(false)
            // ref: https://stackoverflow.com/questions/49262174/how-to-pass-paramaters-to-a-pipelinejob-in-dsl
            // ref: https://jenkinsci.github.io/job-dsl-plugin/#path/job-parameters
            parameters {
                stringParam("AnsibleLimitHosts", "", "Limit playbook to specified inventory hosts\nE.g., 'app_adm','app_tableau','host01', 'host01,host02'" )
                choiceParam("AnsibleDebugFlag", ['', '-v', '-vv', '-vvv', '-vvvv'], "Choose Ansible Debug Level")
                booleanParam("AnsibleGalaxyForceOpt", false, 'Use Ansible Galaxy Force Mode?')
                booleanParam("UseCheckDiffMode", false, 'Use Check+Diff Mode (Dry Run with Diffs)?')
                booleanParam("SkipUntagged", skip_untagged, 'Skip Untagged plays?')
            }
            definition {
                logRotator {
                   daysToKeep(-1)
                   numToKeep(10)
                   artifactNumToKeep(-1)
                   artifactDaysToKeep(-1)
                }
    //             configure {
    //                  it / definition / lightweight(true)
    //             }

                cpsScm {
                    lightweight(true)
                    scriptPath("runAnsiblePlay.groovy")
                    scm {
                        git {
                            remote {
                                url(repoUrl)
                                credentials(gitCredentialsId)
                            }
                            branch("*/main")
                            // ref: https://jenkinsci.github.io/job-dsl-plugin/#method/javaposse.jobdsl.dsl.helpers.ScmContext.git
                            // ref: https://stackoverflow.com/questions/47620060/jenkins-add-git-submodule-to-multibranchpipelinejob-with-dsl#48693179
                            extensions {
                                cleanBeforeCheckout()
                                cleanCheckout {
                                    // Deletes untracked submodules and any other subdirectories which contain .git directories.
                                    deleteUntrackedNestedRepositories(true)
                                }
                                cloneOptions {
                                    shallow(true)
                                    depth(1)
                                    noTags(true)
                                    reference(null)
                                    honorRefspec(false)
                                    timeout(10)
                                }
                                submoduleOptions {
                                    disable(false)
                                    recursive(true)
                                    tracking(false)
                                    reference(null)
                                    timeout(null)
                                    parentCredentials(true)
                                }
                            }
                        }
                    }
                }
            }
            disabled(false)
        }
    }
}
