#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String gitCredentialsId = "dcapi-jenkins-git-user"
String gitSubmoduleCredentialsId = "bitbucket-ssh-lj020326"

String projectName = "INFRA"

String projectFolder = projectName.toUpperCase()
String basePath = "${projectFolder}/vm-templates"
String repoUrl = "https://gitea.admin.dettonville.int/infra/packer-boxes.git"

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
                    defaultVersion("master")
                    implicit(true)
                    includeInChangesets(false)
                    retriever {
                        modernSCM {
                            scm {
                                git {
                                    remote("https://gitea.admin.dettonville.int/infra/pipeline-automation-lib.git")
                                    credentialsId("dcapi-jenkins-git-user")
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

List vmTemplateList = [
    ['build_distribution': 'RHEL', 'build_release': '8-Stream'],
    ['build_distribution': 'RHEL', 'build_release': '8'],
    ['build_distribution': 'RHEL', 'build_release': '7'],
    ['build_distribution': 'CentOS', 'build_release': '8-Stream'],
    ['build_distribution': 'CentOS', 'build_release': '8'],
    ['build_distribution': 'CentOS', 'build_release': '7'],
    ['build_distribution': 'Windows', 'build_release': '2019'],
    ['build_distribution': 'Windows', 'build_release': '2016'],
    ['build_distribution': 'Windows', 'build_release': '2012r2'],
]

vmTemplateList.each { Map config ->

    // folder("<%= "${basePath}/${config.build_distribution}" %>") {
    //     description 'This folder contains jobs to build VM templates for distribution ${config.build_distribution}'
    // }

    folder("${basePath}/${config.build_distribution}") {
        description 'This folder contains jobs to build VM templates for release ${config.build_distribution}/${config.build_release}'
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }

    pipelineJob("${basePath}/${config.build_distribution}/${config.build_release}") {
        description "Build VM template jobs for ${config.build_distribution}/${config.build_release}"
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
        keepDependencies(false)
        definition {
            logRotator {
               daysToKeep(-1)
               numToKeep(10)
               artifactNumToKeep(-1)
               artifactDaysToKeep(-1)
            }
//             lightweight(true)
            configure {
                 it / definition / lightweight(true)
            }

            cpsScm {
                scm {
                    git {
                        remote {
                            url(repoUrl)
                            credentials(gitCredentialsId)
                        }
                        branch("*/master")
                        // ref: https://jenkinsci.github.io/job-dsl-plugin/#method/javaposse.jobdsl.dsl.helpers.ScmContext.git
                        // ref: https://stackoverflow.com/questions/47620060/jenkins-add-git-submodule-to-multibranchpipelinejob-with-dsl#48693179
                        extensions {
                            cleanBeforeCheckout()
                            cleanAfterCheckout()
                            cloneOptions {
                                shallow(true)
                                noTags(true)
                                reference(null)
                                depth(0)
                                honorRefspec(false)
                                timeout(10)
                            }
                            submoduleOptions {
                                disable(false)
                                recursive(true)
                                tracking(false)
                                reference(null)
                                timeout(null)
//                                 parentCredentials(true)
                            }
                        }
                    }
                }
                scriptPath("buildTemplate.groovy")
            }
        }
        disabled(false)
    }

}

