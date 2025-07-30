String baseFolder = "ADMIN"
// Keep jobName as a variable for the pipelineJob definition
String jobFolder = "${baseFolder}/reset-SSH-hostkeys"
String repoHost = "gitea.admin.dettonville.int"

pipelineJob(jobFolder) { // Define the pipeline job first
    parameters {
        booleanParam('InitializeParamsOnly', false, 'Set to true to only initialize parameters without full execution.')
    }
    definition {
        cps {
            script("resetSSHHostKeys()") // This calls your shared library
            sandbox()
        }
    }
}
