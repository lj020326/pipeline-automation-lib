#!/usr/bin/env groovy

String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
echo "jobFolder=${jobFolder}"

def configYmlStr="""
--- 
pipeline: 
  continueIfFailed: false
  alwaysEmailList: lee.johnson@dettonville.com
  runInParallel: false

  jobList: 
    - stage: SMOKE
      runInParallel: true
      jobList:
#        - jobName: DCAPI/Acceptance_Test_Jobs/STAGE/SMOKE/Firefox
        - jobName: "${jobFolder}/SMOKE/Firefox"
        - jobName: "${jobFolder}/SMOKE/Chrome"
        - jobName: "${jobFolder}/SMOKE/Safari"
        - jobName: "${jobFolder}/SMOKE/Edge"
      
    - stage: SANITY
      jobList:
#        - jobName: DCAPI/Acceptance_Test_Jobs/STAGE/SANITY/Firefox
        - jobName: "${jobFolder}/SMOKE/Firefox"
        - jobName: "${jobFolder}/SMOKE/Chrome"
        - jobName: "${jobFolder}/SMOKE/Safari"
        - jobName: "${jobFolder}/SMOKE/Edge"

    - stage: REGRESSION
      jobList:
#        - jobName: DCAPI/Acceptance_Test_Jobs/STAGE/REGRESSION/Firefox
        - jobName: "${jobFolder}/SMOKE/Firefox"
        - jobName: "${jobFolder}/SMOKE/Chrome"
        - jobName: "${jobFolder}/SMOKE/Safari"
        - jobName: "${jobFolder}/SMOKE/Edge"
"""

Map config = readYaml text: configYmlStr

log.info("config=${JsonUtils.printToJsonString(config)}")

runJobs(config)

//node ("QA-LINUX") {
//
//    runJobs(config)
//
//}

