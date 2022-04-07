#!/usr/bin/env bash

DIRS="DEV DEV_CLOUD STAGE STAGE_EXTERNAL PROD_COPRO PROD_STL PROD_KSC PROD_EXTERNAL"
for dir in $DIRS
do

    mkdir -p ${dir}/{SMOKE,SANITY,REGRESSION}
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-chrome.groovy
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-firefox.groovy
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-safari.groovy
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-iphone.groovy
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-android.groovy
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-edge.groovy
    touch ${dir}/{SMOKE,SANITY,REGRESSION}/dcapi-run-test-ie.groovy

done
tree

