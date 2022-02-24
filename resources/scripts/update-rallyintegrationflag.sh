#!/usr/bin/env bash


DIRS="DEV STAGE PROD_COPRO PROD_STL PROD_KSC"
for dir in $DIRS
do

    for i in `grep -Ir "config.useRallyIntegration = false" $dir/* | cut -f1 -d:`; do
        sed -i 's/config.useRallyIntegration = false/config.useRallyIntegration = true/g' $i
        echo updated $dir $i
    done

done
