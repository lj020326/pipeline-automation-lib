#!/usr/bin/env groovy

// ref: http://javadoc.jenkins-ci.org/hudson/slaves/NodeList.html

//def nodeList = env.AGENTS.split(',')
def nodeList = env.AGENTS.tokenize(',').sort()
for (String nodeName: nodeList) {

    node(nodeName) {
        echo "NODE_NAME=${NODE_NAME} NODE_LABELS=${NODE_LABELS}"

    }
}

