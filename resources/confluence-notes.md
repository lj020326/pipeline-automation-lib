


```bash
bash -x ./resources/confluence-update.sh $USER $PASSWD resources/testdata/emailable1-14.html
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage'
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r .body
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r results[].body
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | python -mjson.tool
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r results[].id
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r .results[].id
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r .results[].body
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r .results[].body.storage
curl -u $USER:$PASSWD -X GET 'https://fusion.dettonville.int/confluence/rest/api/content?spaceKey=MAPI&title=Test+Results&expand=body.storage' | jq -r .results[].body.storage.value
bash ./resources/confluence-tool.sh cat "Test+Results" | python -mjson.tool
bash -x ./resources/confluence-tool.sh cat "Test+Results"
bash -x ./resources/confluence-tool.sh cat "Test Results"
bash -x ./resources/confluence-tool.sh info "Test Results"
bash ./resources/confluence-tool.sh info "Test Results"
bash ./resources/confluence-tool.sh cat "Test Results"
bash ./resources/confluence-tool.sh cat pp "Test Results" 
bash ./resources/confluence-tool.sh cat "Test Results" -f
bash ./resources/confluence-tool.sh cat "Test+Results" | jq
bash ./resources/confluence-tool.sh cat "Test Results" | jq
bash -x ./resources/confluence-tool.sh cat "Test Results" | jq
bash ./resources/confluence-get-page-content.sh $USER $PASSWD
bash ./resources/confluence-get-page-content.sh "Test Results"
bash ./resources/confluence-get-page-content.sh cat-storage "Test Results"
bash ./resources/confluence-tool.sh cat-storage "Test Results"
bash -x ./resources/confluence-tool.sh cat-storage "Test Results"
bash ./resources/confluence-tool.sh cat-storage "Test Results"

curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage'
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | jq
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmlline --html
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --html
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --htmlout
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --xml
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --xml --format -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --html -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --xml -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | jq -r
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | jq
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --xml --postvalid -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --postvalid -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --html --postvalid -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --html --nowarning --postvalid -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --schema resources/testdata/confluence.xsd -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --schema resources/confluence-schema/confluence.xsd -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --dtdvalid resources/confluence-schema/confluence.dtd -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --schema resources/confluence-schema/confluence.xsd -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --schema resources/confluence-schema/confluence.xsd -
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --schema resources/confluence-schema/confluence.xsd --noout -
bash ./resources/confluence-tool.sh cat-storage "Test Results"
bash ./resources/confluence-tool.sh cat-storage "Test Results" > test-results.xml
xmllint --schema resources/confluence-schema/confluence.xsd --noout test-results.xml 
xmllint --format --schema resources/confluence-schema/confluence.xsd --noout test-results.xml 
curl -so- -H 'Authorization: Basic ZTA3MTU5ODpXaWNrczNXaXRoaW4=' -X GET -L 'https://fusion.dettonville.int/confluence/rest/api/content/310462997?expand=body.storage' | jq -r .body.storage.value | xmllint --format --html - 2>/dev/null
bash ./resources/confluence-tool.sh cat-storage "Test Results"
bash ./resources/confluence-tool.sh cat-storage-html "Test Results"
bash ./resources/confluence-tool.sh cat-storage "Test Results"
bash -x ./resources/confluence-tool.sh cat-storage "Test Results"
bash -x ./resources/confluence-tool.sh cat-storage "Test Results" > 
history | grep confluence | grep update
bash -x ./resources/confluence-tool.sh create-ffile "Test Results" resources/testdata/testresultsdata.xml 
bash ./resources/confluence-tool.sh create-ffile "Test Results" resources/testdata/testresultsdata.xml 

```
