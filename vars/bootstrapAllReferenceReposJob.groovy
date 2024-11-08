#!/usr/bin/env groovy

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

// ref: https://www.jenkins.io/doc/pipeline/examples/#trigger-job-on-all-nodes

def call() {

    Logger.init(this, LogLevel.INFO)
    Logger log = new Logger(this)

    String logPrefix="bootstrapAllReferenceReposJob():"

    String jobFolder = "${JOB_NAME.substring(0, JOB_NAME.lastIndexOf("/"))}"
    echo "jobFolder=${jobFolder}"

//     // Cron job configurations – configured to run every day at 23:00 PM
//     cron_cfg="H 23 * * *"
    // Cron job configurations – configured to run every 30 minutes
//     cron_cfg="H/30 * * * *"
    cron_cfg="@midnight"

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
                echo "Triggering on " + nodeName
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

    log.info("${logPrefix} finished")
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
