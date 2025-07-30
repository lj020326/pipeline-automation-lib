// vars/runMatrixStages.groovy
import com.dettonville.pipeline.utils.logging.Logger
import groovy.transform.Field
@Field Logger log = new Logger(this)

def call(Map config) {
    // config should contain:
    // script: The CpsScript object (this from Jenkinsfile)
    // matrix: The map containing 'fail-fast' and 'matrix' (which contains 'versions') from YAML
    // baseConfig: The overall config from runAnsibleTestManifest (e.g., testingType, collectionDir)
    // executorScript: The Closure/function to execute for each matrix combination

    def script = config.script
    def matrixDefinition = config.matrix // This is actually the 'strategy' map from the YAML
    def baseConfig = config.baseConfig
    def executorScript = config.executorScript

    // Validate that an executorScript was provided and it's a Closure
    if (!(executorScript instanceof Closure)) {
        script.error("runMatrixStages requires an 'executorScript' parameter which must be a Closure.")
    }

    def matrixVersions = matrixDefinition.matrix.versions // Corrected access to the 'versions' list

    def failFast = matrixDefinition.'fail-fast'

    // --- Start of new random delay logic ---
    // Check for maxRandomDelaySeconds in jobParameters
    Integer maxDelay = 0
    if (config?.maxRandomDelaySeconds) {
        try {
            maxDelay = config.maxRandomDelaySeconds as Integer
        } catch (NumberFormatException e) {
            log.warn("Invalid value for MaxRandomDelaySeconds: ${config.maxRandomDelaySeconds}. Defaulting to 0.")
            maxDelay = 0
        }
    }

    if (maxDelay > 0) {
        // Generate a random delay between 0 and maxDelay
        Random rand = new Random()
        Integer delaySeconds = rand.nextInt(maxDelay + 1) // +1 to include maxDelay
        log.info("Waiting for a random delay of ${delaySeconds} seconds before starting the child job...")
        sleep(time: delaySeconds, unit: 'SECONDS')
    } else {
        log.info("No random delay configured (MaxRandomDelaySeconds is 0 or not set).")
    }
    // --- End of new random delay logic ---

    // Add logging to inspect the matrixVersions before iteration
    log.info("matrixDefinition received: ${matrixDefinition}")
    log.info("Parsed matrixVersions: ${matrixVersions}")

    def parallelStages = [:]
    def jobResults = [:] // To collect results from parallel jobs

    if (!matrixVersions) {
        log.warn("'versions' list is null or empty. No parallel stages will be created.")
        return [stages: [:], results: [:]] // Return empty maps to prevent "No branches to run" error
    }

    matrixVersions.each { versionCombo ->
        // Dynamically build the stage name from all key-value pairs in the versionCombo
        def stageNameParts = []
        def sortedKeys = versionCombo.keySet().sort() // Sort keys for consistent stage naming
        sortedKeys.each { key ->
            stageNameParts << "${key.capitalize()} ${versionCombo.get(key)}"
        }
        def stageName = "Ansible Test: ${stageNameParts.join(' / ')}"

        parallelStages["${stageName}"] = {
            script.stage("${stageName}") {
                // Create a specific config for this matrix combination
                // Merge the baseConfig with the current versionCombo
                def comboConfig = baseConfig.clone()
                comboConfig.putAll(versionCombo) // Dynamically add all elements from versionCombo

                script.echo "Executing matrix combination with config: ${comboConfig}"

                // Invoke the executorScript with the specific combination's config
                jobResults.put(stageName, executorScript.call(comboConfig))
            }
        }
    }

    // Apply failFast if specified in the matrix definition
    if (failFast) {
        parallelStages.failFast = true
        log.info("Matrix fail-fast is enabled.")
    } else {
        log.info("Matrix fail-fast is disabled.")
    }

    // Return the map of parallel stages and collected results
    return [stages: parallelStages, results: jobResults]
}