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

List runTagList = [
    'bootstrap-ansible',
    'bootstrap-docker',
    'bootstrap-docker-stack',
    'bootstrap-docker-swarm',
    'bootstrap-idrac',
    'bootstrap-jenkins-agent',
    'bootstrap-linux',
    'bootstrap-linux-core',
    'bootstrap-linux-firewall',
    'bootstrap-linux-logrotate',
    'bootstrap-linux-package',
    'bootstrap-linux-user',
    'bootstrap-mounts',
    'bootstrap-nfs',
    'bootstrap-nfs-client',
    'bootstrap-nfs-server',
    'bootstrap-ntp',
    'bootstrap-ntp-client',
    'bootstrap-ntp-server',
    'bootstrap-pip',
    'bootstrap-postfix',
    'bootstrap-postfix-client',
    'bootstrap-postfix-server',
    'bootstrap-signing-certs',
    'bootstrap-sshd',
    'bootstrap-vmware-esxi',
    'deploy-cacerts',
    'deploy-caroot',
    'deploy-registry-certs',
    'deploy-vm',
    "add-disk-space",
    "bitbucket-server",
    "cis-rhel7-remediation",
    "cis-rhel8-remediation",
    "controlm",
    "create-ocp-route",
    "diskspace-check",
    "distribute-ca-cert",
    "dmz-local-users",
    "etl-manager",
    "fetch-os-images",
    "insights-client",
    "install-IR-Agent",
    "install-cohesity",
    "install-ivanti-agent",
    "install-logrhythm-agent",
    "install-snmp-agent",
    "install-trellix-agent",
    "install-trend-agent",
    "linux-auth-management",
    "linux-patching-orchestration",
    "linux-post-provisioning",
    "manage-embold",
    "manage-etech",
    "manage-httpd",
    "manage-sentinelone-agent",
    "oracle-post-provisioning",
    "pci-audit",
    "postfix",
    "reboot-linux-machine",
    "register-to-satellite",
    "rhel-remediation",
    "root-password-change",
    "rsync-files",
    "scan-cis",
    "setup-adminapps",
    "setup-bamboo",
    "setup-java",
    "setup-mdm",
    "site",
    "soda-sql-prereqs",
    "uninstall-trellix-agent",
    "uninstall-trend-agent",
    "unregister-from-satellite",
    "vmtools-service-start",
    "yum-package-add"
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

    runTagList.each { String run_tag ->

        folder("${basePath}/${run_environment}") {
            description 'This folder contains jobs to build VM templates for release ${run_environment}/${run_tag}'
            properties {
                authorizationMatrix {
                    inheritanceStrategy {
                        inheriting()
                    }
                }
            }
        }

        pipelineJob("${basePath}/${run_environment}/${run_tag}") {
            description "Build VM template jobs for ${run_environment}/${run_tag}"
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
                                cleanAfterCheckout()
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
                                    tracking(true)
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

