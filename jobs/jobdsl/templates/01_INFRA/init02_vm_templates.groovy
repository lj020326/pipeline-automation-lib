#!/usr/bin/env groovy

// separate configuration from job dsl "seedjob" code
// ref: https://stackoverflow.com/questions/47443106/jenkins-dsl-parse-yaml-for-complex-processing#54665138
@Grab('org.yaml:snakeyaml:1.17')
import org.yaml.snakeyaml.Yaml

String pipelineConfigYaml = "config.vm-template-jobs.yml"

// ref: https://stackoverflow.com/questions/47336502/get-absolute-path-of-the-script-directory-that-is-being-processed-by-job-dsl#47336735
String jobfilePath = "${new File(__FILE__).parent}"
println("jobfilePath: ${jobfilePath}")

Map seedJobConfigs = new Yaml().load(("${jobfilePath}/${pipelineConfigYaml}" as File).text)
// println("seedJobConfigs=${seedJobConfigs}")

Map pipelineConfig = seedJobConfigs.pipelineConfig
println("pipelineConfig=${pipelineConfig}")

String baseFolder = pipelineConfig.baseFolder
String repoUrl = pipelineConfig.repoUrl
String mirrorRepoDir = pipelineConfig.mirrorRepoDir
String gitCredentialsId = pipelineConfig.gitCredentialsId
List vmTemplateList = pipelineConfig.vmTemplateList

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
folder(baseFolder) {
    description "This folder contains jobs to build vmware/vsphere templates"
    properties {
        authorizationMatrix {
            inheritanceStrategy {
                inheriting()
            }
        }
    }
//     configure scmFolderLibraryTraits
}

vmTemplateList.each { Map config ->

    // folder("<%= "${baseFolder}/${config.build_distribution}" %>") {
    //     description "This folder contains jobs to build VM templates for distribution ${config.build_distribution}"
    // }

    folder("${baseFolder}/${config.build_distribution}") {
        description "This folder contains jobs to build VM templates for release ${config.build_distribution}"
        properties {
            authorizationMatrix {
                inheritanceStrategy {
                    inheriting()
                }
            }
        }
    }
    pipelineJob("${baseFolder}/${config.build_distribution}/${config.build_release}") {
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
//                             cleanBeforeCheckout()
//                             cleanCheckout {
//                                 // Deletes untracked submodules and any other subdirectories which contain .git directories.
//                                 deleteUntrackedNestedRepositories(true)
//                             }
                            cloneOptions {
                                shallow(true)
                                depth(2)
                                noTags(true)
                                reference(mirrorRepoDir)
                                honorRefspec(false)
                                timeout(1)
                            }
                            submoduleOptions {
                                disable(false)
                                recursive(true)
                                tracking(true)
                                reference(mirrorRepoDir)
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

pipelineJob("${baseFolder}/run-all-builds") {
	description()
	keepDependencies(false)
    parameters {
        booleanParam("ReplaceExistingTemplate", false, 'Replace Existing Template?')
    }
	definition {
        logRotator {
           daysToKeep(-1)
           numToKeep(10)
           artifactNumToKeep(-1)
           artifactDaysToKeep(-1)
        }
		cpsScm {
			scm {
				git {
					remote {
                        url(repoUrl)
                        credentials(gitCredentialsId)
					}
					branch("*/main")
				}
			}
			scriptPath("runAllBuilds.groovy")
		}
	}
	disabled(false)
}

// ref: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
// ref: https://stackoverflow.com/questions/24248222/jenkins-job-views-with-different-job-names
listView("${baseFolder}/VM-template-jobs") {
    jobs {
        regex("${baseFolder}/")
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

listView("${baseFolder}/VM-template-jobs-unstable") {
    jobs {
        regex("${baseFolder}/")
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
