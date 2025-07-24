#!/usr/bin/env groovy

@Library("pipelineAutomationLib@develop")

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils

// ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/lastCompletedBuild/api/json?pretty=true
// ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/721/api/json?pretty=true
String deployJobInfoAfterStr='''
{
  "_class" : "org.jenkinsci.plugins.workflow.job.WorkflowRun",
  "actions" : [
    {
      "_class" : "hudson.model.CauseAction",
      "causes" : [
        {
          "_class" : "hudson.model.Cause$UpstreamCause",
          "shortDescription" : "Started by upstream project \\"DCAPI/DevzoneFrontend/hotfix%2F1.60.1\\" build number 8",
          "upstreamBuild" : 8,
          "upstreamProject" : "DCAPI/DevzoneFrontend/hotfix%2F1.60.1",
          "upstreamUrl" : "job/DCAPI/job/DevzoneFrontend/job/hotfix%252F1.60.1/"
        }
      ]
    },
    {
      
    },
    {
      "_class" : "hudson.model.ParametersAction",
      "parameters" : [
        {
          "_class" : "hudson.model.StringParameterValue",
          "name" : "ARTIFACT_VERSION",
          "value" : "1.60.1-SNAPSHOT"
        }
      ]
    },
    {
      "_class" : "jenkins.metrics.impl.TimeInQueueAction",
      "blockedDurationMillis" : 0,
      "blockedTimeMillis" : 0,
      "buildableDurationMillis" : 0,
      "buildableTimeMillis" : 3,
      "buildingDurationMillis" : 282193,
      "executingTimeMillis" : 276865,
      "executorUtilization" : 0.98,
      "subTaskCount" : 1,
      "waitingDurationMillis" : 5787,
      "waitingTimeMillis" : 5789
    },
    {
      "_class" : "jenkins.scm.api.SCMRevisionAction"
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "develop" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 721,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "branch" : [
              {
                "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "name" : "develop"
              }
            ]
          },
          "revision" : {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "branch" : [
              {
                "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "name" : "develop"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
        "branch" : [
          {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "name" : "develop"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/api/infra-jenkins-pipeline-libs.git"
      ],
      "scmName" : ""
    },
    {
      "_class" : "hudson.plugins.git.GitTagAction"
    },
    {
      
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 721,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
            "branch" : [
              {
                "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
                "name" : "main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
            "branch" : [
              {
                "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
                "name" : "main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
        "branch" : [
          {
            "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
            "name" : "main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/api/pipeline-automation-lib.git"
      ],
      "scmName" : ""
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 721,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "branch" : [
              {
                "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
                "name" : "main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "branch" : [
              {
                "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
                "name" : "main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
        "branch" : [
          {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "name" : "main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/API/ara-pipelines.git"
      ],
      "scmName" : ""
    },
    {
      "_class" : "org.jenkinsci.plugins.workflow.cps.EnvActionImpl"
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "origin/main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 721,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "2becaf9eed865452cee60f25842ca2aa7908ae2c",
            "branch" : [
              {
                "SHA1" : "2becaf9eed865452cee60f25842ca2aa7908ae2c",
                "name" : "origin/main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "2becaf9eed865452cee60f25842ca2aa7908ae2c",
            "branch" : [
              {
                "SHA1" : "2becaf9eed865452cee60f25842ca2aa7908ae2c",
                "name" : "origin/main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "2becaf9eed865452cee60f25842ca2aa7908ae2c",
        "branch" : [
          {
            "SHA1" : "2becaf9eed865452cee60f25842ca2aa7908ae2c",
            "name" : "origin/main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/dfsbizops/ara_spec_files.git"
      ],
      "scmName" : ""
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      "_class" : "org.jenkinsci.plugins.pipeline.modeldefinition.actions.RestartDeclarativePipelineAction"
    },
    {
      
    },
    {
      "_class" : "org.jenkinsci.plugins.workflow.job.views.FlowGraphAction"
    },
    {
      
    },
    {
      
    },
    {
      
    }
  ],
  "artifacts" : [
    
  ],
  "building" : false,
  "description" : null,
  "displayName" : "#721",
  "duration" : 282193,
  "estimatedDuration" : 292337,
  "executor" : null,
  "fullDisplayName" : "DC API » Deployment Jobs » Deploy Frontend (Stage) » main #721",
  "id" : "721",
  "keepLog" : false,
  "number" : 721,
  "queueId" : 681087,
  "result" : "SUCCESS",
  "timestamp" : 1566584575431,
  "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/721/",
  "changeSets" : [
    {
      "_class" : "hudson.plugins.git.GitChangeSetList",
      "items" : [
        {
          "_class" : "hudson.plugins.git.GitChangeSet",
          "affectedPaths" : [
            "DCAID/custom_rel_specs/release_spec_DC_AID_Allcomponent.json"
          ],
          "commitId" : "7b1275353e5bcf24bdbad2927bed682a158a5e95",
          "timestamp" : 1566502803000,
          "author" : {
            "absoluteUrl" : "https://cd.dettonville.int/jenkins/user/e083884",
            "fullName" : "Tulam, VenuGopal"
          },
          "authorEmail" : "VenuGopal.Tulam@dettonville.com",
          "comment" : "Updated custom release spec for all component work flow for testing ARA\\nin MTF\\n",
          "date" : "2019-08-22 14:40:03 -0500",
          "id" : "7b1275353e5bcf24bdbad2927bed682a158a5e95",
          "msg" : "Updated custom release spec for all component work flow for testing ARA",
          "paths" : [
            {
              "editType" : "edit",
              "file" : "DCAID/custom_rel_specs/release_spec_DC_AID_Allcomponent.json"
            }
          ]
        },
        {
          "_class" : "hudson.plugins.git.GitChangeSet",
          "affectedPaths" : [
            "DCAID/custom_rel_specs/release_spec_DC_AID_TEST_ALLComponent.json"
          ],
          "commitId" : "fb06593f1f34b73460d82e370b6f6fbdb946b7f4",
          "timestamp" : 1566502941000,
          "author" : {
            "absoluteUrl" : "https://cd.dettonville.int/jenkins/user/e083884",
            "fullName" : "Tulam, VenuGopal"
          },
          "authorEmail" : "VenuGopal.Tulam@dettonville.com",
          "comment" : "testing the ara workflow dcaid\\n",
          "date" : "2019-08-22 14:42:21 -0500",
          "id" : "fb06593f1f34b73460d82e370b6f6fbdb946b7f4",
          "msg" : "testing the ara workflow dcaid",
          "paths" : [
            {
              "editType" : "edit",
              "file" : "DCAID/custom_rel_specs/release_spec_DC_AID_TEST_ALLComponent.json"
            }
          ]
        },
        {
          "_class" : "hudson.plugins.git.GitChangeSet",
          "affectedPaths" : [
            "DCAID/custom_rel_specs/release_spec_DC_AID_TEST_ALLComponent.json"
          ],
          "commitId" : "2770de79550681f45a3d8f9ed6c5542cfdc850e2",
          "timestamp" : 1566574018000,
          "author" : {
            "absoluteUrl" : "https://cd.dettonville.int/jenkins/user/venugopal.tulam",
            "fullName" : "VenuGopal.Tulam"
          },
          "authorEmail" : "VenuGopal.Tulam@dettonville.com",
          "comment" : "remove MTF test spec\\n",
          "date" : "2019-08-23 10:26:58 -0500",
          "id" : "2770de79550681f45a3d8f9ed6c5542cfdc850e2",
          "msg" : "remove MTF test spec",
          "paths" : [
            {
              "editType" : "delete",
              "file" : "DCAID/custom_rel_specs/release_spec_DC_AID_TEST_ALLComponent.json"
            }
          ]
        }
      ],
      "kind" : "git"
    }
  ],
  "culprits" : [
    {
      "absoluteUrl" : "https://cd.dettonville.int/jenkins/user/venugopal.tulam",
      "fullName" : "VenuGopal.Tulam"
    },
    {
      "absoluteUrl" : "https://cd.dettonville.int/jenkins/user/e083884",
      "fullName" : "Tulam, VenuGopal"
    }
  ],
  "nextBuild" : null,
  "previousBuild" : {
    "number" : 720,
    "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/720/"
  }
}
'''

// ref: https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/720/api/json?pretty=true
String deployJobInfoBeforeStr='''
{
  "_class" : "org.jenkinsci.plugins.workflow.job.WorkflowRun",
  "actions" : [
    {
      "_class" : "hudson.model.CauseAction",
      "causes" : [
        {
          "_class" : "hudson.model.Cause$UpstreamCause",
          "shortDescription" : "Started by upstream project \\"DCAPI/DevzoneFrontend/hotfix%2F1.60.1\\" build number 4",
          "upstreamBuild" : 4,
          "upstreamProject" : "DCAPI/DevzoneFrontend/hotfix%2F1.60.1",
          "upstreamUrl" : "job/DCAPI/job/DevzoneFrontend/job/hotfix%252F1.60.1/"
        }
      ]
    },
    {
      
    },
    {
      "_class" : "hudson.model.ParametersAction",
      "parameters" : [
        {
          "_class" : "hudson.model.StringParameterValue",
          "name" : "ARTIFACT_VERSION",
          "value" : "1.60.1-SNAPSHOT"
        }
      ]
    },
    {
      "_class" : "jenkins.metrics.impl.TimeInQueueAction",
      "blockedDurationMillis" : 0,
      "blockedTimeMillis" : 0,
      "buildableDurationMillis" : 0,
      "buildableTimeMillis" : 17,
      "buildingDurationMillis" : 290260,
      "executingTimeMillis" : 277704,
      "executorUtilization" : 0.96,
      "subTaskCount" : 1,
      "waitingDurationMillis" : 6405,
      "waitingTimeMillis" : 6412
    },
    {
      "_class" : "jenkins.scm.api.SCMRevisionAction"
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "develop" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 720,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "branch" : [
              {
                "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "name" : "develop"
              }
            ]
          },
          "revision" : {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "branch" : [
              {
                "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "name" : "develop"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
        "branch" : [
          {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "name" : "develop"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/api/infra-jenkins-pipeline-libs.git"
      ],
      "scmName" : ""
    },
    {
      "_class" : "hudson.plugins.git.GitTagAction"
    },
    {
      
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 720,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
            "branch" : [
              {
                "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
                "name" : "main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
            "branch" : [
              {
                "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
                "name" : "main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
        "branch" : [
          {
            "SHA1" : "38cdd31cdaa3d07d1380cf91d282e08e71bb0e06",
            "name" : "main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/api/pipeline-automation-lib.git"
      ],
      "scmName" : ""
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 720,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "branch" : [
              {
                "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
                "name" : "main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "branch" : [
              {
                "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
                "name" : "main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
        "branch" : [
          {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "name" : "main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/API/ara-pipelines.git"
      ],
      "scmName" : ""
    },
    {
      "_class" : "org.jenkinsci.plugins.workflow.cps.EnvActionImpl"
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "origin/main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 720,
          "buildResult" : null,
          "marked" : {
            "SHA1" : "7174d9e6faed34f1bfaacf9e523dd7263d0c3e9b",
            "branch" : [
              {
                "SHA1" : "7174d9e6faed34f1bfaacf9e523dd7263d0c3e9b",
                "name" : "origin/main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "7174d9e6faed34f1bfaacf9e523dd7263d0c3e9b",
            "branch" : [
              {
                "SHA1" : "7174d9e6faed34f1bfaacf9e523dd7263d0c3e9b",
                "name" : "origin/main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "7174d9e6faed34f1bfaacf9e523dd7263d0c3e9b",
        "branch" : [
          {
            "SHA1" : "7174d9e6faed34f1bfaacf9e523dd7263d0c3e9b",
            "name" : "origin/main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/dfsbizops/ara_spec_files.git"
      ],
      "scmName" : ""
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      "_class" : "org.jenkinsci.plugins.pipeline.modeldefinition.actions.RestartDeclarativePipelineAction"
    },
    {
      
    },
    {
      "_class" : "org.jenkinsci.plugins.workflow.job.views.FlowGraphAction"
    },
    {
      
    },
    {
      
    },
    {
      
    }
  ],
  "artifacts" : [
    
  ],
  "building" : false,
  "description" : null,
  "displayName" : "#720",
  "duration" : 290260,
  "estimatedDuration" : 292337,
  "executor" : null,
  "fullDisplayName" : "DC API » Deployment Jobs » Deploy Frontend (Stage) » main #720",
  "id" : "720",
  "keepLog" : false,
  "number" : 720,
  "queueId" : 669480,
  "result" : "SUCCESS",
  "timestamp" : 1566573485535,
  "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/720/",
  "changeSets" : [
    
  ],
  "culprits" : [
    
  ],
  "nextBuild" : {
    "number" : 721,
    "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/721/"
  },
  "previousBuild" : {
    "number" : 719,
    "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployFrontendStage/job/main/719/"
  }
}'''

String jsonDeployInfoStr3='''
{
  "_class" : "org.jenkinsci.plugins.workflow.job.WorkflowRun",
  "actions" : [
    {
      "_class" : "hudson.model.ParametersAction",
      "parameters" : [
        {
          "_class" : "hudson.model.StringParameterValue",
          "name" : "ARTIFACT_VERSION",
          "value" : "1.61.0-SNAPSHOT"
        }
      ]
    },
    {
      "_class" : "hudson.model.CauseAction",
      "causes" : [
        {
          "_class" : "hudson.model.Cause$UserIdCause",
          "shortDescription" : "Started by user Ruddy, Matt",
          "userId" : "e080847",
          "userName" : "Ruddy, Matt"
        }
      ]
    },
    {
      "_class" : "jenkins.metrics.impl.TimeInQueueAction",
      "blockedDurationMillis" : 0,
      "blockedTimeMillis" : 0,
      "buildableDurationMillis" : 0,
      "buildableTimeMillis" : 2,
      "buildingDurationMillis" : 189857,
      "executingTimeMillis" : 186977,
      "executorUtilization" : 0.98,
      "subTaskCount" : 1,
      "waitingDurationMillis" : 0,
      "waitingTimeMillis" : 0
    },
    {
      "_class" : "jenkins.scm.api.SCMRevisionAction"
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "develop" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 213,
          "buildResult" : "",
          "marked" : {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "branch" : [
              {
                "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "name" : "develop"
              }
            ]
          },
          "revision" : {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "branch" : [
              {
                "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "name" : "develop"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
        "branch" : [
          {
            "SHA1" : "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
            "name" : "develop"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/api/infra-jenkins-pipeline-libs.git"
      ],
      "scmName" : ""
    },
    {
      "_class" : "hudson.plugins.git.GitTagAction"
    },
    {
      
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 213,
          "buildResult" : "",
          "marked" : {
            "SHA1" : "d162841d98eb088c40f9b5cb8d9bd24c6481b1cf",
            "branch" : [
              {
                "SHA1" : "d162841d98eb088c40f9b5cb8d9bd24c6481b1cf",
                "name" : "main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "d162841d98eb088c40f9b5cb8d9bd24c6481b1cf",
            "branch" : [
              {
                "SHA1" : "d162841d98eb088c40f9b5cb8d9bd24c6481b1cf",
                "name" : "main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "d162841d98eb088c40f9b5cb8d9bd24c6481b1cf",
        "branch" : [
          {
            "SHA1" : "d162841d98eb088c40f9b5cb8d9bd24c6481b1cf",
            "name" : "main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/api/pipeline-automation-lib.git"
      ],
      "scmName" : ""
    },
    {
      
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 213,
          "buildResult" : "",
          "marked" : {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "branch" : [
              {
                "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
                "name" : "main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "branch" : [
              {
                "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
                "name" : "main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
        "branch" : [
          {
            "SHA1" : "2c8143d26419e53c1495c9276d49871d7e42ff8f",
            "name" : "main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/API/ara-pipelines.git"
      ],
      "scmName" : ""
    },
    {
      "_class" : "org.jenkinsci.plugins.workflow.cps.EnvActionImpl"
    },
    {
      "_class" : "hudson.plugins.git.util.BuildData",
      "buildsByBranchName" : {
        "origin/main" : {
          "_class" : "hudson.plugins.git.util.Build",
          "buildNumber" : 213,
          "buildResult" : "",
          "marked" : {
            "SHA1" : "a2a0e642b6958086f9a5c852e08264de406df4a2",
            "branch" : [
              {
                "SHA1" : "a2a0e642b6958086f9a5c852e08264de406df4a2",
                "name" : "origin/main"
              }
            ]
          },
          "revision" : {
            "SHA1" : "a2a0e642b6958086f9a5c852e08264de406df4a2",
            "branch" : [
              {
                "SHA1" : "a2a0e642b6958086f9a5c852e08264de406df4a2",
                "name" : "origin/main"
              }
            ]
          }
        }
      },
      "lastBuiltRevision" : {
        "SHA1" : "a2a0e642b6958086f9a5c852e08264de406df4a2",
        "branch" : [
          {
            "SHA1" : "a2a0e642b6958086f9a5c852e08264de406df4a2",
            "name" : "origin/main"
          }
        ]
      },
      "remoteUrls" : [
        "https://gitrepository.dettonville.int/stash/scm/dfsbizops/ara_spec_files.git"
      ],
      "scmName" : ""
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      
    },
    {
      "_class" : "org.jenkinsci.plugins.pipeline.modeldefinition.actions.RestartDeclarativePipelineAction"
    },
    {
      
    },
    {
      "_class" : "org.jenkinsci.plugins.workflow.job.views.FlowGraphAction"
    },
    {
      
    },
    {
      
    },
    {
      
    }
  ],
  "artifacts" : [
    
  ],
  "building" : false,
  "description" : "",
  "displayName" : "#213",
  "duration" : 189857,
  "estimatedDuration" : 166506,
  "executor" : "",
  "fullDisplayName" : "DC API Â» Deployment Jobs Â» Deploy OpenAPI Notifier (Stage) Â» main #213",
  "id" : "213",
  "keepLog" : false,
  "number" : 213,
  "queueId" : 1566066,
  "result" : "FAILURE",
  "timestamp" : 1567605288538,
  "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployOpenAPINotifierStage/job/main/213/",
  "changeSets" : [
    
  ],
  "culprits" : [
    {
      "absoluteUrl" : "https://cd.dettonville.int/jenkins/user/narasareddy.palavalli",
      "fullName" : "Narasareddy.Palavalli"
    }
  ],
  "nextBuild" : "",
  "previousBuild" : {
    "number" : 212,
    "url" : "https://cd.dettonville.int/jenkins/job/DCAPI/job/DeploymentJobs/job/DeployOpenAPINotifierStage/job/main/212/"
  }
}
'''

Logger.init(this, LogLevel.INFO)
//Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

//log.info("jsonTxt.getClass()= ${jsonTxt.getClass()}")

log.info("comparing simple json")

String json1 = '{"test1":10,"test2":"20","users":[{"name":"name1","age":"10"},{"name":"name2","age":"20"}]}'
String json2 = '{"test1":10,"test2":20,"users":[{"name":"name1","age":"12"},{"name":"name2","age":"20","date":""}]}'

JsonUtils jsonUtils = new JsonUtils(this)
//Boolean result = jsonUtils.jsonDiff('root', json1, json2, true)
Boolean result = jsonUtils.isJsonDiff(json1, json2, true)

log.info("isJsonDiff result = ${result}")

List diffs = jsonUtils.getJsonDiffs(json1, json2, true)

log.info("getJsonDiff diffs=${diffs}")

log.info("comparing build results json")

result = jsonUtils.isJsonDiff(deployJobInfoBeforeStr, deployJobInfoAfterStr, true)

log.info("isJsonDiff result = ${result}")

def deployJobInfoBefore = readJSON text: deployJobInfoBeforeStr
def deployJobInfoAfter = readJSON text: deployJobInfoAfterStr
def jsonDeployInfo3 = readJSON text: jsonDeployInfoStr3

//diffs = jsonUtils.getJsonDiffs(deployJobInfoBeforeStr, deployJobInfoAfterStr, true)
//
//Map diffMap = diffs.collectEntries{ Map diff ->
//    [(diff.label):diff.findAll { it.key != 'label' }]
//}
//log.debug("getJsonDiff diffs=${diffs}")

Map diffMap = jsonUtils.getJsonDiffMap(deployJobInfoBefore, deployJobInfoAfter, true)

log.info("jsonUtils.getJsonDiffMap()=${JsonUtils.printToJsonString(diffMap)}")

String componentBuildNumber = deployJobInfoBefore.actions[0].causes[0].upstreamBuild
String componentBuildProject = deployJobInfoBefore.actions[0].causes[0].upstreamProject
String componentBuildVersion = deployJobInfoBefore.actions[2].parameters[0].value
String componentBuildDeployUrl = deployJobInfoBefore.url

String componentName="INFRA-${componentBuildProject.split("/")[1]}"

log.info("${componentBuildDeployUrl} => ${componentName}:${componentBuildVersion}:${componentBuildNumber}")

//Map componentBuildCausesAction = jsonDeployInfo3.actions.find { key, value -> key == "causes" }
//List componentBuildParams = jsonDeployInfo3.actions.find { key, value -> key == "parameters" }

Map componentBuildCausesAction = jsonDeployInfo3.actions.findResult { it.causes }[0]
Map componentBuildParams = jsonDeployInfo3.actions.findResult { it.parameters }[0]

componentBuildNumber = componentBuildCausesAction.upstreamBuild
//componentBuildProject = componentBuildCausesAction.upstreamProject
componentBuildVersion = componentBuildParams.value
componentBuildDeployUrl = jsonDeployInfo3.url

//componentName="INFRA-${componentBuildProject.split("/")[1]}"
componentName="INFRA-${componentBuildDeployUrl.split("/")[-4]}"

log.info("${componentName}:${componentBuildVersion}:${componentBuildNumber} => ${componentBuildDeployUrl}")

///////

//String componentBuildNumberBefore = diffMap["root.actions[0].causes[0].upstreamBuild"].value1
//String componentBuildNumberAfter = diffMap["root.actions[0].causes[0].upstreamBuild"].value2
//
//String componentBuildVersionBefore = diffMap["root.actions[2].parameters[0].value"].value1
//String componentBuildVersionAfter = diffMap["root.actions[2].parameters[0].value"].value2
//
//String componentBuildDeployUrlBefore = diffMap["root.url"].value1
//String componentBuildDeployUrlAfter = diffMap["root.url"].value2

String componentBuildNumberBefore = deployJobInfoBefore.actions.findResult { it.causes }[0].upstreamBuild
String componentBuildNumberAfter = deployJobInfoAfter.actions.findResult { it.causes }[0].upstreamBuild

String componentBuildVersionBefore = deployJobInfoBefore.actions.findResult { it.parameters }[0].value
String componentBuildVersionAfter = deployJobInfoAfter.actions.findResult { it.parameters }[0].value

String componentBuildDeployUrlBefore = deployJobInfoBefore.url
String componentBuildDeployUrlAfter = deployJobInfoAfter.url

String cvbBefore = "${componentName}:${componentBuildVersionBefore}:${componentBuildNumberBefore}"
String cvbAfter = "${componentName}:${componentBuildVersionAfter}:${componentBuildNumberAfter}"

log.info("${cvbBefore} at test start => ${componentBuildDeployUrlBefore}")
log.info("${cvbAfter} ran during the test cycle => ${componentBuildDeployUrlAfter}")
