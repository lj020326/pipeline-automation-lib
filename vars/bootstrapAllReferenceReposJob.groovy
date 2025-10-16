#!/usr/bin/env groovy

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

import com.dettonville.pipeline.utils.JsonUtils
import com.dettonville.pipeline.utils.Utilities

// ref: https://stackoverflow.com/questions/6305910/how-do-i-create-and-access-the-global-variables-in-groovy
import groovy.transform.Field
@Field Logger log = new Logger(this)

// ref: https://www.jenkins.io/doc/pipeline/examples/#trigger-job-on-all-nodes

def call() {

    String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
    log.info("jobFolder=${jobFolder}")

//    // Cron job configurations – configured to run every day at 23:00 PM
//     String cron_cfg="H 23 * * *"
    // Cron job configurations – configured to run every 30 minutes
//     String cron_cfg="H/30 * * * *"
    String cron_cfg="@midnight"

    properties([
        disableConcurrentBuilds(),
        pipelineTriggers([cron(cron_cfg)])
    ])

    def branches = [:]
    def names = nodeNames()
    for (int i=0; i<names.size(); ++i) {
        def nodeName = names[i];
        // Into each branch we put the pipeline code we want to execute
        branches["node_" + nodeName] = {
            node(nodeName) {
                log.info("Triggering on " + nodeName)
                build job: "${jobFolder}/bootstrap-reference-repos",
                        parameters: [
                            string(name: 'JenkinsNodeLabel', value: nodeName)
                        ]
//                 build job: "${jobFolder}/bootstrap-reference-repos",
//                         parameters: [
//                             new org.jvnet.jenkins.plugins.nodelabelparameter.NodeParameterValue
//                             ("JenkinsNodeLabel", "Specify the Jenkins node to run on", nodeName)
//                         ]
            }
        }
    }

    // Now we trigger all branches
    parallel branches

    log.info("finished")
}

// This method collects a list of Node names from the current Jenkins instance
// ref: https://stackoverflow.com/questions/46933722/how-to-get-a-list-of-all-jenkins-nodes-assigned-with-label-including-master-node#49625621
@NonCPS
def nodeNames() {
//     return jenkins.model.Jenkins.instance.nodes.collect { node -> node.name }
    def nodes = []
    jenkins.model.Jenkins.get().computers.each { c ->
        nodes.add(c.node.selfLabel.name)
    }
    return nodes
}
