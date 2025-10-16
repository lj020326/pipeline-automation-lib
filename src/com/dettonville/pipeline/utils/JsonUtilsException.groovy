package com.dettonville.pipeline.utils

/**
 * Custom Exception class for errors encountered within the JsonUtils utility.
 * Defined in its own file to ensure clean class loading in Jenkins Shared Libraries.
 */
class JsonUtilsException extends Exception {
    JsonUtilsException(String message) {
        super(message)
    }
}
