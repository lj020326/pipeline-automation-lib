#!/usr/bin/env groovy

String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
echo "jobFolder=${jobFolder}"

def ymlConfig='''
--- 
pipeline: 
  continueIfFailed: false
  alwaysEmailList: lee.johnson@dettonville.com
  runInParallel: false

  testJobList: 
    - testStage: SMOKE
      runInParallel: true
      jobs: 
#        - jobName: DCAPI/Acceptance_Test_Jobs/STAGE/SMOKE/Firefox
        - jobName: "${jobFolder}/SMOKE/Firefox"
        - jobName: "${jobFolder}/SMOKE/Chrome"
        - jobName: "${jobFolder}/SMOKE/Safari"
        - jobName: "${jobFolder}/SMOKE/Edge"
      
    - testStage: SANITY
      jobs: 
#        - jobName: DCAPI/Acceptance_Test_Jobs/STAGE/SANITY/Firefox
        - jobName: "${jobFolder}/SMOKE/Firefox"
        - jobName: "${jobFolder}/SMOKE/Chrome"
        - jobName: "${jobFolder}/SMOKE/Safari"
        - jobName: "${jobFolder}/SMOKE/Edge"

    - testStage: REGRESSION
      jobs: 
#        - jobName: DCAPI/Acceptance_Test_Jobs/STAGE/REGRESSION/Firefox
        - jobName: "${jobFolder}/SMOKE/Firefox"
        - jobName: "${jobFolder}/SMOKE/Chrome"
        - jobName: "${jobFolder}/SMOKE/Safari"
        - jobName: "${jobFolder}/SMOKE/Edge"
'''

Map config = [:]

config['yml']=ymlConfig

node ("QA-LINUX") {

    runTestJobs(config)

}

