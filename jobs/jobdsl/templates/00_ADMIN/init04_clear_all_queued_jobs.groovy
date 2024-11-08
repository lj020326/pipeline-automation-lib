
String baseFolder = "ADMIN"

jobFolder = "${baseFolder}/clear-all-queued-jobs"

// ref: https://xanderx.com/post/cancel-all-queued-jenkins-jobs/
// ref: https://stackoverflow.com/questions/12305244/cancel-queued-builds-and-aborting-executing-builds-using-groovy-for-jenkins
def pipelineText = """
echo "Clearing out all queued jobs"
Jenkins.instance.queue.clear()
"""

pipelineJob(jobFolder) {
    definition {
        cps {
            script(pipelineText)
            sandbox()
        }
    }
}
