#!/usr/bin/env bash

ENV_DIRS="DEV DEV_CLOUD STAGE STAGE_EXTERNAL PROD_COPRO PROD_STL PROD_KSC PROD_EXTERNAL"
#ENV_DIRS="DEV DEV_CLOUD STAGE_EXTERNAL PROD_COPRO PROD_STL PROD_KSC PROD_EXTERNAL"
TEST_STAGE_DIRS="SMOKE SANITY REGRESSION"
BROWSERS="chrome firefox safari iphone android edge ie multi-platform"

for envdir in $ENV_DIRS
do

    for testdir in $TEST_STAGE_DIRS
    do

        for browser in $BROWSERS
        do

            echo "#!/usr/bin/env groovy

@Library(\"dcapi-automation-pipeline\")_

runATHEnvJob()
" > ./jobs/ath-jobs/${envdir}/${testdir}/dcapi-run-test-${browser}.groovy

            echo "updated ${envdir}/${testdir}/dcapi-run-test-${browser}.groovy"
        done

#        echo "#!/usr/bin/env groovy
#
#@Library(\"dcapi-automation-pipeline\")_
#
#runATHEnvJobMultiPlatform()
#" > ./jobs/ath-jobs/${envdir}/${testdir}/dcapi-run-test-multi-platform.groovy
#
#        echo "updated ${envdir}/${testdir}/dcapi-run-test-multi-platform.groovy"

    done

done

