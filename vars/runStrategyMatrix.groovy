// vars/runMatrixStages.groovy
import com.dettonville.pipeline.utils.logging.Logger
import groovy.transform.Field
@Field Logger log = new Logger(this)

Map call(Map config) {
    // config should contain:
    // script: The CpsScript object (this from Jenkinsfile)
    // strategy: The map containing 'fail-fast' and 'matrix' (which contains 'versions') from YAML
    // stageNamePrefix: The prefix to use for stages
    // baseConfig: The overall config from runAnsibleTestManifest (e.g., testType, collectionDir)
    // executorScript: The Closure/function to execute for each matrix combination

    def script = config.script
    def strategy = config.strategy
    def stageNamePrefix = config.get("stageNamePrefix", "Stage")
    def baseConfig = config.baseConfig
    def executorScript = config.executorScript

    // Validate that an executorScript was provided and it's a Closure
    if (!(executorScript instanceof Closure)) {
        script.error("runStrategyJobs requires an 'executorScript' parameter which must be a Closure.")
    }

    def matrix = strategy.matrix

    def failFast = strategy.'fail-fast'

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

    // Add logging to inspect the matrix before iteration
    log.info("strategy received: ${strategy}")
    log.info("Parsed matrix: ${matrix}")

    Map parallelStages = [:]
    Map jobResults = [:] // To collect results from parallel jobs

    if (!matrix) {
        log.warn("'matrix' is null or empty. No parallel stages will be created.")
        return [stages: [:], results: [:]] // Return empty maps to prevent "No branches to run" error
    }

    def combinations

    // --- ENHANCED LOGIC ---
    // Check if the matrix is a simple list of combinations (the "versions" key case)
    if (matrix.keySet().size() == 1 && matrix.values().every { it instanceof List }) {
        // If there's only one key, take its list as the combinations
        combinations = matrix.values().flatten()
        log.info("Single-dimensional matrix detected. Directly using provided list of combinations.")
    } else {
        // Otherwise, use the Cartesian product logic for multi-dimensional matrices
        combinations = [[:]]
        matrix.each { key, values ->
            def nextCombinations = []
            values.each { value ->
                combinations.each { combo ->
                    def newCombo = combo.clone()
                    newCombo.put(key, value)
                    nextCombinations.add(newCombo)
                }
            }
            combinations = nextCombinations
        }
        log.info("Multi-dimensional matrix detected. Generated combinations using Cartesian product.")
    }
    // --- END OF ENHANCED LOGIC ---

    // Iterate over the determined list of combinations
    combinations.each { matrixElement ->
        // Dynamically build the stage name from all key-value pairs in the matrixElement
        def stageNameParts = []
        def sortedKeys = matrixElement.keySet().sort()
        sortedKeys.each { key ->
            stageNameParts << "${key.capitalize()} ${matrixElement.get(key)}"
        }
        def stageName = "${stageNamePrefix}: ${stageNameParts.join(' / ')}"

        parallelStages.put(stageName, {
            script.stage(stageName) {
                // Create a specific config for this matrix combination
                // Merge the baseConfig with the current matrixElement
//                 def comboConfig = baseConfig.clone()
                Map comboConfig = baseConfig.findAll { !["strategy"].contains(it.key) }
                comboConfig.putAll(matrixElement)

                script.echo "Executing matrix combination with config: ${comboConfig}"

                // Invoke the executorScript with the specific combination's config
                jobResults.put(stageName, executorScript.call(comboConfig))
            }
        })
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
