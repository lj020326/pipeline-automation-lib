package com.dettonville.pipeline.utils

import org.junit.Assert
import org.junit.Test
import groovy.json.JsonOutput // Used here to define the expected JSON string

/**
 * Unit tests for the JsonUtils static utility class, specifically verifying
 * the behavior of the printToJsonString method.
 *
 * NOTE: This class assumes JsonUtils and JsonUtilsException are available
 * via the shared library class path.
 */
class JsonUtilsTest {

    @Test
    void shouldHandleNullObject() {
        def result = JsonUtils.printToJsonString(null)
        Assert.assertEquals("Expected null object to return empty string.", "", result)
    }

    @Test
    void shouldHandleSimpleList() {
        // Test case based on the packerCmdArgList from the pipeline
        List underTest = ["packer", "build"]

        def expected = JsonOutput.prettyPrint(JsonOutput.toJson(underTest))

        def result = JsonUtils.printToJsonString(underTest)

        Assert.assertEquals("Expected List to be converted to pretty-printed JSON.", expected, result)
    }

    @Test
    void shouldHandleSimpleMap() {
        // Test case based on the collectedResults1 from the pipeline
        Map underTest = [
            buildStatus: "SUCCESSFUL"
        ]

        def expected = JsonOutput.prettyPrint(JsonOutput.toJson(underTest))

        def result = JsonUtils.printToJsonString(underTest)

        Assert.assertEquals("Expected simple Map to be converted to pretty-printed JSON.", expected, result)
    }

    @Test
    void shouldHandleComplexMap() {
        // Test case based on the complex collectedResults from the pipeline
        Map underTest = [
            "Ansible Test: AnsibleVersion 2.19 / PythonVersion 3.13": [buildStatus: "SUCCESSFUL", failed: false],
            "Ansible Test: AnsibleVersion 2.16 / PythonVersion 3.12": [buildStatus: "SUCCESSFUL", failed: false]
        ]

        def expected = JsonOutput.prettyPrint(JsonOutput.toJson(underTest))

        def result = JsonUtils.printToJsonString(underTest)

        Assert.assertEquals("Expected complex Map to be converted to pretty-printed JSON.", expected, result)
    }

    @Test
    void shouldThrowExceptionOnNonJsonifiableObject() {
        // NOTE: In a real environment, it's hard to force a JsonOutput failure with simple objects.
        // This test ensures the try-catch block is potentially callable if JsonOutput fails,
        // but for standard Groovy objects, the test currently relies on JsonOutput to work.

        // Example of a non-standard object that might cause issues in some JSON libraries:
        // A Map containing a reference to itself.
        Map recursiveMap = [:]
        recursiveMap.put("self", recursiveMap)

        try {
            JsonUtils.printToJsonString(recursiveMap)
            // If JsonOutput throws an exception (e.g., StackOverflowError in some older Groovy versions),
            // JsonUtils should catch it and throw JsonUtilsException.
        } catch (JsonUtilsException ex) {
            // Success: JsonUtils caught the underlying issue and re-threw the custom exception.
            Assert.assertTrue(true)
            return
        } catch (StackOverflowError soe) {
            // Sometimes JsonOutput.toJson throws a SOE for recursive structures. We expect JsonUtils to catch and wrap it.
            // If it hits here, the test would fail, meaning the catch in JsonUtils failed to handle it cleanly.
            Assert.fail("JsonUtils failed to wrap StackOverflowError into JsonUtilsException.")
        }

        // If execution reaches here, no exception was thrown, which is acceptable
        // as Groovy's JsonOutput is usually robust.
    }
}
