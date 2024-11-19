#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

// run daily 7pm CST
cron_cfg="H(0-5) 19 * * *"

properties([pipelineTriggers([cron("${cron_cfg}")])])

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

Logger.init(this, LogLevel.INFO)
Logger log = new Logger(this)

pipeline {

    agent {
        label 'DTL'
    }

    parameters {
        choice(choices: "STAGE\nDEV\nPROD", description: "Choose App Environment", name: 'AppEnvironment')
        string(defaultValue: "", description: "Specify component to be tested\nE.g., 'DEVPORTAL', 'FRONTEND', 'NOTIFIER', etc", name: 'componentName')
        string(defaultValue: "develop", description: "Specify component branch to be tested", name: 'componentBranch')
        string(defaultValue: "", description: "Specify changed email dist", name: 'changedEmailList')
        string(defaultValue: "", description: "Specify always email dist", name: 'alwaysEmailList')
        string(defaultValue: "", description: "Specify failed email dist", name: 'failedEmailList')
    }

    options {
        timestamps()
    }

    environment {
        ENV_SPEC="DCAPI/env_specs/stage_env_spec.json"
        RELEASE_SPEC="DCAPI/dcapi_flows/devportal.json"
        WORKFLOW="mc_api_dfs_workflows.dcapi_deploy_devportal"

//        SMOKE_TEST = "DCAPI/Acceptance_Test_Jobs/Smoke_Test_Stage"
//        SANITY_TEST = "DCAPI/Acceptance_Test_Jobs/Sanity_Test_Stage"
        SMOKE_TEST = "DCAPI/Jobs/Test_Jobs/Smoke Test"
        SANITY_TEST = "DCAPI/Jobs/Test_Jobs/Sanity Test"
    }

    stages {
        stage("Run Release Test") {
            Map config = [:]

            params.each { key, value ->
                config[key] = value
            }

//            config.smokeJobName = "DCAPI/Acceptance_Test_Jobs/Smoke_Test_Stage"
//            config.sanityJobName = "DCAPI/Acceptance_Test_Jobs/Sanity_Test_Stage"
            config.smokeJobName = SMOKE_TEST
            config.sanityJobName = SANITY_TEST

            List recipientList = (config.alwaysEmailList.contains(",")) ? config.alwaysEmailList.tokenize(',') : [config.alwaysEmailList]
            recipientList.add("ljohnson@dettonville.com")
            config.alwaysEmailList = recipientList.join(",")

            echo "config=${config}"

            // Note - need to pick up the component branch/version from the respective env config
            //
            // E.g., it is passed into the deploy pipeline in json format at
            //
            // DEVPORTAL: https://gitrepository.dettonville.int/stash/projects/DFSBIZOPS/repos/ara_spec_files/browse/DCAPI/dcapi_flows/devportal.json
            // FRONTEND: https://gitrepository.dettonville.int/stash/projects/DFSBIZOPS/repos/ara_spec_files/browse/DCAPI/ind_rel_specs/release_spec_content_DCAPI.DevZone.json
//            runSmokeThenSanity(config)
            runReleaseTest(config)

        }
    }
}
