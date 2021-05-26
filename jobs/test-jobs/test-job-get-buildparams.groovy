#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils

String jsonDeployInfoString='''
{
    "_class": "org.jenkinsci.plugins.workflow.job.WorkflowRun",
    "actions":     [
                {
            "_class": "hudson.model.CauseAction",
            "causes": [            {
                "_class": "hudson.model.Cause$UpstreamCause",
                "shortDescription": "Started by upstream project \\"DCAPI/Jobs/DeploymentJobs/STAGE/BACKENDS\\" build number 1",
                "upstreamBuild": 1,
                "upstreamProject": "DCAPI/Jobs/DeploymentJobs/STAGE/BACKENDS",
                "upstreamUrl": "job/DCAPI/job/Jobs/job/DeploymentJobs/job/STAGE/job/BACKENDS/"
            }]
        },
        {},
                {
            "_class": "hudson.model.ParametersAction",
            "parameters":             [
                                {
                    "_class": "hudson.model.StringParameterValue",
                    "name": "ArtifactVersion",
                    "value": "1.62.0-SNAPSHOT"
                },
                                {
                    "_class": "hudson.model.StringParameterValue",
                    "name": "AppComponentBranch",
                    "value": "master"
                },
                                {
                    "_class": "hudson.model.BooleanParameterValue",
                    "name": "DebugReleaseScript",
                    "value": false
                },
                                {
                    "_class": "hudson.model.BooleanParameterValue",
                    "name": "RunPostDeployTests",
                    "value": false
                },
                                {
                    "_class": "hudson.model.BooleanParameterValue",
                    "name": "UseSimulationMode",
                    "value": true
                }
            ]
        },
                {
            "_class": "jenkins.metrics.impl.TimeInQueueAction",
            "blockedDurationMillis": 0,
            "blockedTimeMillis": 0,
            "buildableDurationMillis": 0,
            "buildableTimeMillis": 6,
            "buildingDurationMillis": 14791,
            "executingTimeMillis": 9823,
            "executorUtilization": 0.66,
            "subTaskCount": 2,
            "waitingDurationMillis": 5265,
            "waitingTimeMillis": 5270
        },
                {
            "_class": "hudson.plugins.git.util.BuildData",
            "buildsByBranchName": {"refs/remotes/origin/develop":             {
                "_class": "hudson.plugins.git.util.Build",
                "buildNumber": 3,
                "buildResult": "",
                "marked":                 {
                    "SHA1": "e40ea607d14509c4c2d317116f8c2f5162ed4363",
                    "branch": [                    {
                        "SHA1": "e40ea607d14509c4c2d317116f8c2f5162ed4363",
                        "name": "refs/remotes/origin/develop"
                    }]
                },
                "revision":                 {
                    "SHA1": "e40ea607d14509c4c2d317116f8c2f5162ed4363",
                    "branch": [                    {
                        "SHA1": "e40ea607d14509c4c2d317116f8c2f5162ed4363",
                        "name": "refs/remotes/origin/develop"
                    }]
                }
            }},
            "lastBuiltRevision":             {
                "SHA1": "e40ea607d14509c4c2d317116f8c2f5162ed4363",
                "branch": [                {
                    "SHA1": "e40ea607d14509c4c2d317116f8c2f5162ed4363",
                    "name": "refs/remotes/origin/develop"
                }]
            },
            "remoteUrls": ["https://gitrepository.dettonville.int/stash/scm/api/pipeline-automation-lib.git"],
            "scmName": ""
        },
        {"_class": "hudson.plugins.git.GitTagAction"},
        {},
        {},
        {},
                {
            "_class": "hudson.plugins.git.util.BuildData",
            "buildsByBranchName": {"develop":             {
                "_class": "hudson.plugins.git.util.Build",
                "buildNumber": 3,
                "buildResult": "",
                "marked":                 {
                    "SHA1": "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                    "branch": [                    {
                        "SHA1": "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                        "name": "develop"
                    }]
                },
                "revision":                 {
                    "SHA1": "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                    "branch": [                    {
                        "SHA1": "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                        "name": "develop"
                    }]
                }
            }},
            "lastBuiltRevision":             {
                "SHA1": "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                "branch": [                {
                    "SHA1": "c695d1d1fc99c00683fb81a2e2a5ea18d8eed4ba",
                    "name": "develop"
                }]
            },
            "remoteUrls": ["https://gitrepository.dettonville.int/stash/scm/api/dcapi-jenkins-pipeline-libs.git"],
            "scmName": ""
        },
        {"_class": "org.jenkinsci.plugins.workflow.cps.EnvActionImpl"},
        {},
        {},
                {
            "_class": "hudson.plugins.git.util.BuildData",
            "buildsByBranchName": {"origin/master":             {
                "_class": "hudson.plugins.git.util.Build",
                "buildNumber": 3,
                "buildResult": "",
                "marked":                 {
                    "SHA1": "10381198359a284010cfe18577e706e22937bb60",
                    "branch": [                    {
                        "SHA1": "10381198359a284010cfe18577e706e22937bb60",
                        "name": "origin/master"
                    }]
                },
                "revision":                 {
                    "SHA1": "10381198359a284010cfe18577e706e22937bb60",
                    "branch": [                    {
                        "SHA1": "10381198359a284010cfe18577e706e22937bb60",
                        "name": "origin/master"
                    }]
                }
            }},
            "lastBuiltRevision":             {
                "SHA1": "10381198359a284010cfe18577e706e22937bb60",
                "branch": [                {
                    "SHA1": "10381198359a284010cfe18577e706e22937bb60",
                    "name": "origin/master"
                }]
            },
            "remoteUrls": ["https://gitrepository.dettonville.int/stash/scm/dfsbizops/ara_spec_files.git"],
            "scmName": ""
        },
        {},
        {},
        {},
        {},
        {},
        {},
        {},
        {"_class": "org.jenkinsci.plugins.pipeline.modeldefinition.actions.RestartDeclarativePipelineAction"},
        {},
        {"_class": "org.jenkinsci.plugins.workflow.job.views.FlowGraphAction"},
        {},
        {},
        {}
    ],
    "artifacts": [    {
        "displayPath": "runAppDeployment-results.json",
        "fileName": "runAppDeployment-results.json",
        "relativePath": "runAppDeployment-results.json"
    }],
    "building": false,
    "description": "Deployment Status: SUCCESS<br>Test Status: SKIPPED<br>AppEnvironment: STAGE_EXTERNAL",
    "displayName": "#3",
    "duration": 14791,
    "estimatedDuration": 25025,
    "executor": "",
    "fullDisplayName": "MC API Â» Jobs Â» Deployment Jobs Â» STAGE Â» DevPortal #3",
    "id": "3",
    "keepLog": false,
    "number": 3,
    "queueId": 2436429,
    "result": "SUCCESS",
    "timestamp": 1571083778232,
    "url": "https://cd.dettonville.int/jenkins/job/DCAPI/job/Jobs/job/DeploymentJobs/job/STAGE/job/DevPortal/3/",
    "changeSets": [],
    "culprits": [],
    "nextBuild": "",
    "previousBuild":     {
        "number": 2,
        "url": "https://cd.dettonville.int/jenkins/job/DCAPI/job/Jobs/job/DeploymentJobs/job/STAGE/job/DevPortal/2/"
    }
}
'''

Logger.init(this, LogLevel.INFO)
//Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

def jsonDeployInfo = readJSON text: jsonDeployInfoString

//Map componentBuildCausesAction = jsonDeployInfo.actions.find { key, value -> key == "causes" }
//List componentBuildVersionParam = jsonDeployInfo.actions.find { key, value -> key == "parameters" }

Map componentBuildCausesAction = jsonDeployInfo.actions.findResult { it.causes }[0]
//Map componentBuildVersionParam = jsonDeployInfo.actions.findResult { it.parameters }[0]

//List componentBuildParams = jsonDeployInfo.actions.findResult { it.parameters }
//log.info("componentBuildParams=${componentBuildParams}")
//Map componentBuildVersionParam = componentBuildParams.findResult { it.name=="ArtifactVersion" ? it : null }

//Map componentBuildVersionParam = jsonDeployInfo.actions.findResult { it.parameters }.findResult { it.name=="ArtifactVersion" ? it : null }
//log.info("componentBuildVersionParam=${componentBuildVersionParam}")

String componentBuildVersion = jsonDeployInfo.actions.findResult { it.parameters }.findResult { it.name=="ArtifactVersion" ? it.value : null }
log.info("componentBuildVersion=${componentBuildVersion}")

componentBuildNumber = componentBuildCausesAction.upstreamBuild
//componentBuildProject = componentBuildCausesAction.upstreamProject
//componentBuildVersion = componentBuildVersionParam.value
componentBuildDeployUrl = jsonDeployInfo.url

//componentName="DCAPI-${componentBuildProject.split("/")[1]}"
componentName="DCAPI-${componentBuildDeployUrl.split("/")[-4]}"

log.info("${componentName}:${componentBuildVersion}:${componentBuildNumber} => ${componentBuildDeployUrl}")

