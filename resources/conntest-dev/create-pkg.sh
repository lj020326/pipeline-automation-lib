#!/usr/bin/env bash

cd ..
cp conntest-dev/graph.html conntest

rm conntest.zip
rm conntest.zip.b64
cd conntest
zip -r ../conntest.zip *
cd ..
base64 conntest.zip > conntest.zip.b64

