/**
 * com.dettonville.api.pipeline.utility is a collection of utilities to perform common pipeline tasks.
 */
package com.dettonville.api.pipeline.utility

/**
 * Utility to interact with the HashiCorp Vault.
 *
 * @Author grant.gortsema@dettonville.com
 */
class VaultUtil implements Serializable {

  /**
  * a reference to the pipeline that allows you to run pipeline steps in your shared libary
  */
  def steps


  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public VaultUtil(steps) {this.steps = steps}

  public String getVaultUrl(script, String env) {
    return "https://${env.replace('-','.')}.vault.dettonville.int:8200"
  }

  public String getToken(script, String env, String roleId, String secretId, Boolean verbose = false) {
    def util = new com.dettonville.api.pipeline.utility.Utilities()
    def vaultAuthResponseFile = "vaultAuthResponse.json"
    def vaultDomain = getVaultUrl(script, env)

    //authenticate to vault
    if(verbose) {
      def outputVar = steps.sh(returnStdout: true, script: "curl -X POST -d '{\"role_id\": \"${roleId}\", \"secret_id\": \"${secretId}\"}' ${vaultDomain}/v1/auth/approle/login > ${vaultAuthResponseFile}")
      steps.echo "OUTPUT FROM CURL (AUTH): ${outputVar}"
    }else {
      def outputVar = steps.sh(returnStdout: true, script: "#!/bin/sh -e\n curl -X POST -d '{\"role_id\": \"${roleId}\", \"secret_id\": \"${secretId}\"}' ${vaultDomain}/v1/auth/approle/login > ${vaultAuthResponseFile}")
    }

    //get vaultresponse.json into a string and get client token from it
    def responseString = util.getStringFromFile(vaultAuthResponseFile)

    if(verbose) {
      steps.echo "RESPONSE STRING (AUTH) ${responseString}"
    }

    def clientToken = util.parseJSON(responseString)?.auth?.client_token

    if(verbose) {
      steps.echo "clientToken: ${clientToken}"
    }

    return clientToken
  }

  public void writeToVault(script, String env, String clientToken, String directory, Map secrets, Boolean verbose = false) {

    def util = new com.dettonville.api.pipeline.utility.Utilities()
    def vaultDomain = getVaultUrl(script, env)

    String keyValuePairToPost = ''
    secrets.each { k,v ->
      keyValuePairToPost += "\"${k}\": \"${v}\","
    }

    //remove trailing comma
    keyValuePairToPost = keyValuePairToPost[0..-2]

    //insert into vault
    def outputVar
    if(verbose) {
      steps.echo "DIRECTORY IS: ${directory}"
      steps.echo "clientToken: ${clientToken}"
      outputVar = steps.sh(returnStdout: true, script: "curl -H 'X-Vault-Token:${clientToken}' -X POST -d '{${keyValuePairToPost}}' ${vaultDomain}/v1/${directory}")
      steps.echo "OUTPUT FROM CURL (INSERT): ${outputVar}"
    }else {
      outputVar = steps.sh(returnStdout: true, script: "#!/bin/sh -e\n curl -H \"X-Vault-Token:${clientToken}\" -X POST -d '{${keyValuePairToPost}}' ${vaultDomain}/v1/${directory}")
    }
  }

  public String getSecret(script, String env, String clientToken, String directory, String key, Boolean verbose = false) {

    def util = new com.dettonville.api.pipeline.utility.Utilities()
    def vaultDomain = getVaultUrl(script, env)
    def vaultInsertResponseFile = "vaultInsertReponse.json"
    def outputVar
    def responseString

    if(verbose) {
      outputVar = steps.sh(returnStdout: true, script: "curl -H \"X-Vault-Token:${clientToken}\" ${vaultDomain}/v1/${directory} > ${vaultInsertResponseFile}")
      responseString = util.getStringFromFile(vaultInsertResponseFile)
      steps.echo "RESPOSE: ${responseString}"
    }else {
      outputVar = steps.sh(returnStdout: true, script: "#!/bin/sh -e\n curl -H \"X-Vault-Token:${clientToken}\" ${vaultDomain}/v1/${directory} > ${vaultInsertResponseFile}")
    }

    //parse response for value
    def secret = util.parseJSON(responseString)?.data?."${key}"

    if(verbose) {
      steps.echo "VALUE for ${key} is: ${secret}"
    }

    return secret
  }

  public void writeSecrets(script, String env, String roleId, String secretId, String directory, Map secrets, Boolean verbose = false) {

    def clientToken = getToken(script, env, roleId, secretId, verbose)

    writeToVault(script, env, clientToken, directory, secrets, verbose)

    if(verbose) {
      secrets.each{ k, v ->
        def secret = getSecret(script, env, clientToken, directory, k, verbose)
        steps.echo "Secret you just put in: ${secret}"
      }
    }
  }
}
