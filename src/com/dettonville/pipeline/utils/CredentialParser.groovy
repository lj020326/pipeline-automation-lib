package com.dettonville.pipeline.utils

import com.cloudbees.groovy.cps.NonCPS
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.json.JsonOutput
//import groovy.json.JsonBuilder
import net.sf.json.JSON
import net.sf.json.JSONObject

import com.dettonville.pipeline.utils.logging.LogLevel
import com.dettonville.pipeline.utils.logging.Logger

//import static groovy.json.JsonOutput.*

import net.sf.json.JSON
//import org.jenkinsci.plugins.workflow.cps.DSL

import com.cloudbees.plugins.credentials.Credentials

// @formatter:off
/**
 * Parses an incoming json object into Credential objects
 *
 * Expected json file format:
 * [
 *{*          "pattern": "subdomain\.domain\.tld[:/]group1",
 *          "id": "Id of the credential in the jenkins instance",
 *          "comment": "Comment for the credential"
 *},
 *{ .. }* ]
 *
 * @see Credential
 */
// @formatter:on
class CredentialParser implements Serializable {

    private static final long serialVersionUID = 1L
    Logger log = new Logger(this)
//    DSL dsl
    def dsl

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
//    CredentialParser(DSL dsl) {
    CredentialParser(dsl) {
        this.dsl = dsl
    }

    /**
     * Fetches a list of credential objects into a list
     *
     * ref: https://github.com/cloudbees/jenkins-scripts/blob/master/list-credential.groovy
     *
     * @param jsonContent The json content loaded via JsonLibraryResource
     * @return The parsed list of valid Credential objects
     */
    @NonCPS
    @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
    List<Credentials> fetch() {

        Set<Credentials> allCredentials = new HashSet<Credentials>();

        def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                com.cloudbees.plugins.credentials.Credentials.class
        );

        allCredentials.addAll(creds)

        Jenkins.instance.getAllItems(com.cloudbees.hudson.plugins.folder.Folder.class).each { f ->
            creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                    com.cloudbees.plugins.credentials.Credentials.class, f)
            allCredentials.addAll(creds)

        }

        for (c in allCredentials) {
            println(c.id + ": " + c.description)
            log.info("${c.id} : ${c.description}")
        }

        return allCredentials
    }

    @NonCPS
    @SuppressFBWarnings('SE_NO_SERIALVERSIONID')
    List<Credentials> parse(JSON jsonContent) {
        Credentials credential = null
        List<Credentials> parsedCredentials = []
        // Walk through entries, try to parse them as Credential object and add it to the returned list
        jsonContent.each { JSONObject entry ->
            log.info("entry: ", entry)
//            String comment = entry.comment ?: null
//            String id = entry.id ?: null
//            String pattern = entry.pattern ?: null
//            String username = entry.username ?: null
//            credential = new Credential(pattern, id, comment, username)
//            log.trace("parsed credential file: ", credential)
//            if (credential.isValid()) {
//                parsedCredentials.push(credential)
//            } else {
//                log.debug("credential is invalid because id and/or pattern is missing")
//            }
        }

        return parsedCredentials
    }

    List getCredentialIdList(String jsonStr) {
        JSON envConfigs = dsl.readJSON(text: jsonStr)

        return getCredentialIdListFromJSON(envConfigs)
    }

//    String printToJsonString(Map config) {
    String printToJsonString(def config) {
        if (config instanceof List) {
            return config.toString()
        }
        return JsonOutput.prettyPrint(JsonOutput.toJson(config))
    }

    List getCredentialIdListFromJson(def json) {
        log.debug("json=${printToJsonString(json)}")

        return getCredentialIdListFromJson('root', json)
    }

//    List getCredentialIdListFromJson(JSON json) {
    List getCredentialIdListFromJson(String label, def json) {
        log.debug("json=${printToJsonString(json)}")

        List credList = []
        if (json instanceof Map) {
            credList = getCredentialIdListFromMap(label, json as Map)
        } else if (json instanceof List) {
            credList = getCredentialIdListFromList(label, json as List)
        } else if (json instanceof String) {
            credList = getCredentialIdListFromString(label, json as String)
        } else {
            log.error("type not handled for json=${json}")
        }
        if (!credList.isEmpty()) credList.unique()
        log.debug("credList=${credList}")
        return credList
    }

    List getCredentialIdListFromMap(String label, Map map) {
        log.debug("json=${printToJsonString(map)}")

        List credList = []

        map.each { k, v ->
            List results = getCredentialIdListFromJson(label + '.' + k, v)
            // ref: https://stackoverflow.com/questions/15760667/how-to-add-only-non-null-item-to-a-list-in-groovy
            if (!results.isEmpty()) credList.addAll(results)
        }

        log.debug("credList=${credList}")
        return credList
    }

    List getCredentialIdListFromList(String label, List list) {
        log.debug("json=${printToJsonString(list)}")

        List credList = []

        list.eachWithIndex { v, i ->
            List results = getCredentialIdListFromJson("${label}[${i}]", v)
            // ref: https://stackoverflow.com/questions/15760667/how-to-add-only-non-null-item-to-a-list-in-groovy
            if (!results.isEmpty()) credList.addAll(results)
        }

        log.debug("credList=${credList}")
        return credList
    }

    List getCredentialIdListFromString(String label, String string) {
        log.debug("string=${printToJsonString(string)}")
        List credList = []

        def matchStr = '^\\$password\\.'
        def matchPattern = /${matchStr}(.*)/
        def match = (string =~ matchPattern)

        match.each { it ->
            log.debug("it=${it}")
            String credentialId = it[1]
            log.debug("string=${string} credentialId=${credentialId}")
            credList.add(dsl.string(credentialsId: credentialId, variable: credentialId))
        }
        log.debug("credList=${credList}")
        return credList
    }

}
