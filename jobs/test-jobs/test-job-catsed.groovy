#!/usr/bin/env groovy

@Library("pipeline-automation-lib")

def getResourceFile(String fileName) {
    def file = libraryResource fileName
    // create a file with fileName
    writeFile file: "./${fileName}", text: file
}

node ('DEVCLD-LIN7') {

    String summaryFile="testdata/summary.txt"
    getResourceFile(summaryFile)

//    def summary = readFile(summaryFile).replaceAll('\n', "</li><li>")
//    def summary = "<li>${ readFile(summaryFile).replaceAll('\n', "</li>\n<li>") }</li>"
    def summary = "<li>${ readFile(summaryFile).replaceAll("\n", "</li>\n<li>").replaceAll("<li></li>\n","\n") }</li>"
//    summary = summary.replaceAll('~/\n/', "</li><li>")

//    def summary = sh(script: "cat ${summaryFile} | sed 's/\\(.*\\):\\(.*\\)/<li>\\1:\\2<\\/li>/g", returnStdout: true)
    echo "Setting build summary: ${summary}"

}
