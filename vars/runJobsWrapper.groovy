#!/usr/bin/env groovy

String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
echo "jobFolder=${jobFolder}"

def configYmlStr="""
--- 
pipeline: 
  continueIfFailed: false
  alwaysEmailList: ljohnson@dettonville.org
  runInParallel: false

  jobList: 
    - stage: SMOKE
      runInParallel: true
      jobs: 
#        - job: DCAPI/Acceptance_Test_Jobs/STAGE/SMOKE/Firefox
        - job: "${jobFolder}/SMOKE/Firefox"
        - job: "${jobFolder}/SMOKE/Chrome"
        - job: "${jobFolder}/SMOKE/Safari"
        - job: "${jobFolder}/SMOKE/Edge"
      
    - stage: SANITY
      jobs: 
#        - job: DCAPI/Acceptance_Test_Jobs/STAGE/SANITY/Firefox
        - job: "${jobFolder}/SMOKE/Firefox"
        - job: "${jobFolder}/SMOKE/Chrome"
        - job: "${jobFolder}/SMOKE/Safari"
        - job: "${jobFolder}/SMOKE/Edge"

    - stage: REGRESSION
      jobs: 
#        - job: DCAPI/Acceptance_Test_Jobs/STAGE/REGRESSION/Firefox
        - job: "${jobFolder}/SMOKE/Firefox"
        - job: "${jobFolder}/SMOKE/Chrome"
        - job: "${jobFolder}/SMOKE/Safari"
        - job: "${jobFolder}/SMOKE/Edge"
"""

Map config = readYaml text: configYmlStr

log.info("config=${JsonUtils.printToJsonString(config)}")

runJobs(config)

//node ("QA-LINUX") {
//
//    runJobs(config)
//
//}

