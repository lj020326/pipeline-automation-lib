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
#        - job: DCAPI/Acceptance_Test_Jobs/STAGE/SMOKE/Firefox
        - job: "${jobFolder}/SMOKE/Firefox"
        - job: "${jobFolder}/SMOKE/Chrome"
        - job: "${jobFolder}/SMOKE/Safari"
        - job: "${jobFolder}/SMOKE/Edge"
      
    - testStage: SANITY
      jobs: 
#        - job: DCAPI/Acceptance_Test_Jobs/STAGE/SANITY/Firefox
        - job: "${jobFolder}/SMOKE/Firefox"
        - job: "${jobFolder}/SMOKE/Chrome"
        - job: "${jobFolder}/SMOKE/Safari"
        - job: "${jobFolder}/SMOKE/Edge"

    - testStage: REGRESSION
      jobs: 
#        - job: DCAPI/Acceptance_Test_Jobs/STAGE/REGRESSION/Firefox
        - job: "${jobFolder}/SMOKE/Firefox"
        - job: "${jobFolder}/SMOKE/Chrome"
        - job: "${jobFolder}/SMOKE/Safari"
        - job: "${jobFolder}/SMOKE/Edge"
'''

Map config = [:]

config['yml']=ymlConfig

node ("QA-LINUX") {

    runTestJobs(config)

}

