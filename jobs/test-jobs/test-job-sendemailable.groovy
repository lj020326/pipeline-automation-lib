#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

def getResourceFile(String fileName) {
    def file = libraryResource fileName
    // create a file with fileName
    writeFile file: "./${fileName}", text: file
}

node ('DEVCLD-LIN7') {

    try {
        String imgFile="testdata/screen-snapshot.png"
        String imgFileB64="testdata/screen-snapshot-base64.txt"
        getResourceFile(imgFileB64)

        sh "base64 --decode ${imgFileB64} > ${imgFile}"

        def emailBody=createScreenSnapRpt(imgFile)

        emailext body: emailBody,
                mimeType: 'text/html',
                attachmentsPattern: "**/${imgFile}",
                subject: "Test",
                to: "ljohnson@dettonville.com"

    } catch (Exception err) {
        echo "exception: [${err}]"
    }

    archiveArtifacts artifacts: '**'
}

// ref: https://github.com/buildit/jenkins-pipeline-libraries/blob/master/src/mavensettings.groovy
def createScreenSnapRpt(imgFile) {
    """
<html>
<head>
<title>Test Results</title>
</head>
<body>
<img src="cid:${imgFile}">
</body>
</html>
"""
}
