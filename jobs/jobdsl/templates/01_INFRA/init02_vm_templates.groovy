#!/usr/bin/env groovy

// ref: https://github.com/sheehan/job-dsl-gradle-example/blob/master/src/jobs/example4Jobs.groovy
String projectName = "INFRA"

String projectFolder = projectName.toUpperCase()
String basePath = "${projectFolder}/vm-templates"

// String repoUrl = "ssh://git@gitea.admin.dettonville.int:2222/infra/packer-boxes.git"
String repoUrl = "git@bitbucket.org:lj020326/packer-templates.git"
String pipelineRepoUrl = "ssh://git@gitea.admin.dettonville.int:2222/infra/pipeline-automation-lib.git"

// String gitCredentialsId = "infra-jenkins-git-user"
String gitCredentialsId = "bitbucket-ssh-jenkins"
String gitPipelineLibCredId = "bitbucket-ssh-jenkins"

List vmTemplateList = [
    [ build_distribution: 'CentOS',  build_release: '9'],
    [ build_distribution: 'CentOS',  build_release: '8-stream'],
    [ build_distribution: 'CentOS',  build_release: '8'],
    [ build_distribution: 'CentOS',  build_release: '7'],
    [ build_distribution: 'Debian',  build_release: '10'],
    [ build_distribution: 'Debian',  build_release: '9'],
    [ build_distribution: 'RedHat',  build_release: '9'],
    [ build_distribution: 'RedHat',  build_release: '8'],
    [ build_distribution: 'RedHat',  build_release: '7'],
    [ build_distribution: 'Ubuntu',  build_release: '22.04'],
    [ build_distribution: 'Ubuntu',  build_release: '20.04'],
    [ build_distribution: 'Ubuntu',  build_release: '18.04'],
    [ build_distribution: 'Windows',  build_release: '2019'],
    [ build_distribution: 'Windows',  build_release: '2016'],
    [ build_distribution: 'Windows',  build_release: '2012r2'],
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
//             configure {
//                  it / definition / lightweight(true)
//             }

            cpsScm {
                lightweight(true)
                scriptPath("buildTemplate.groovy")
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
                                shallow(false)
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

// ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
// ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
listView("VM-template-jobs") {
    jobs {
        regex("${basePath}/")
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("VM-template-jobs-unstable") {
    jobs {
        regex("${basePath}/")
    }
    jobFilters {
        status {
            status(Status.UNSTABLE)
        }
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}
