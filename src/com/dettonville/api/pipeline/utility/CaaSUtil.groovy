/**
 * com.dettonville.api.pipeline.utility is a collection of utilities to perform common pipeline tasks.
 */
package com.dettonville.api.pipeline.utility

/**
 * Utility to interact with the CaaS service. Used to create certs and trust stores for the applications.
 *
 * @Author grant.gortsema@dettonville.org
 */
class CaaSUtil implements Serializable {

  /**
  * a reference to the pipeline that allows you to run pipeline steps in your shared libary
  */
  def steps


  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public CaaSUtil(steps) {this.steps = steps}


  /**
  * Call CaaS with CSR and get back a multiple certificates for synpase as well as CaaS integration,
  * add the sub CA's to the root and turn the pem into a JKS, then combine the JKS files into a sungle JKS
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param appHostName Name of the app, should be same as in manifest.
  * @param env what env we want the certs for. values can be 'nyc-dev', 'nyc-stage', or 'bel-prod'
  * @param returnMap A Map shared back to the caller where it can add the certs and passwords to
  * @param cnForCert the CN name you want the cert to have. usually your appName + -client. like masterpass-switch-account-registry-services-client
  */
  public void getJKSFromCaaS(script, String appHostName, String env, Map returnMap, String cnForCert,String ouForCert=null) {

    def nyc_devTruststoreFilename = "devpaas-truststore.jks"
    def nyc_devTruststorePassword = "password"
    def nyc_stageTruststoreFilename = "stage-cacerts.jks"
    def nyc_stageTruststorePassword = "password"
    def bel_prodTruststoreFilename = "prod-cacerts.jks"
    def bel_prodTruststorePassword = "changeit"
    def nonProdPwdLabel = "X509-Default-non-PRD"
    def prodPwdLabel = "X509-prd-ci-cd-sw"
    def util = new com.dettonville.api.pipeline.utility.Utilities()
    def synapseClientProfileId
    def synapseClientJurisdictionId
    def otherClientProfileId
    def otherClientJurisdictionId
    def caasEndpoint
    def credentialsId
    def pwdLabel
    def truststoreFilename
    def truststorePassword
    def nyc_devDomain = "apps.nyc.pcfdev00.dettonville.int"
    def nyc_stageDomain = "apps.nyc.pcfstage00.dettonville.int"
    def bel_prodDomain = "apps.bel.pcfprod00.dettonville.int"
    def nyc_prodDomain = "apps.nyc.pcfprod00.dettonville.int"
    def jpn_prodDomain = "apps.jpn.pcfprod00.dettonville.int"
    def domain

    steps.git branch: "master", url: "https://gitrepository.dettonville.int/stash/scm/ca/truststore-certs.git"

    switch(env) {
      case 'nyc-dev':   truststorePassword = nyc_devTruststorePassword;
                        truststoreFilename = nyc_devTruststoreFilename;
                        pwdLabel = nonProdPwdLabel;
                        synapseClientProfileId = script.globalVars.nyc_devSynapseClientProfileId;
                        otherClientProfileId =  script.globalVars.nyc_devOtherClientProfileId;
                        synapseClientJurisdictionId = script.globalVars.nyc_devSynapseClientJurisdictionId;
                        otherClientJurisdictionId = script.globalVars.nyc_devOtherClientJurisdictionId;
                        caasEndpoint = script.globalVars.devCaaSEndpoint;
                        credentialsId = 'dev_caas_conversion';
                        domain =  nyc_devDomain;
                        break;
      case 'nyc-stage': truststorePassword = nyc_stageTruststorePassword;
                        truststoreFilename = nyc_stageTruststoreFilename;
                        pwdLabel = nonProdPwdLabel;
                        synapseClientProfileId = script.globalVars.nyc_stageSynapseClientProfileId;
                        otherClientProfileId =  script.globalVars.nyc_stageOtherClientProfileId;
                        synapseClientJurisdictionId = script.globalVars.nyc_stageSynapseClientJurisdictionId;
                        otherClientJurisdictionId = script.globalVars.nyc_stageOtherClientJurisdictionId;
                        caasEndpoint = script.globalVars.stageCaaSEndpoint;
                        credentialsId = 'stage_caas_conversion';
                        domain =  nyc_stageDomain;
                        break;
      case 'bel-prod':  truststorePassword = bel_prodTruststorePassword;
                        truststoreFilename = bel_prodTruststoreFilename;
                        pwdLabel = prodPwdLabel;
                        synapseClientProfileId = script.globalVars.bel_prodSynapseClientProfileId;
                        otherClientProfileId =  script.globalVars.bel_prodOtherClientProfileId;
                        synapseClientJurisdictionId = script.globalVars.bel_prodSynapseClientJurisdictionId;
                        otherClientJurisdictionId = script.globalVars.bel_prodOtherClientJurisdictionId;
                        caasEndpoint = script.globalVars.prodCaaSEndpoint;
                        credentialsId = 'prod_caas_conversion';
                        domain =  bel_prodDomain;
                        break;
      case 'jpn-':  truststorePassword = bel_prodTruststorePassword;
                        truststoreFilename = bel_prodTruststoreFilename;
                        pwdLabel = prodPwdLabel;
                        synapseClientProfileId = script.globalVars.bel_prodSynapseClientProfileId;
                        otherClientProfileId =  script.globalVars.bel_prodOtherClientProfileId;
                        synapseClientJurisdictionId = script.globalVars.bel_prodSynapseClientJurisdictionId;
                        otherClientJurisdictionId = script.globalVars.bel_prodOtherClientJurisdictionId;
                        caasEndpoint = script.globalVars.prodCaaSEndpoint;
                        credentialsId = 'prod_caas_conversion';
                        domain =  jpn_prodDomain;
                        break;
      case 'nyc-prod':  truststorePassword = bel_prodTruststorePassword;
                        truststoreFilename = bel_prodTruststoreFilename;
                        pwdLabel = prodPwdLabel;
                        synapseClientProfileId = script.globalVars.bel_prodSynapseClientProfileId;
                        otherClientProfileId =  script.globalVars.bel_prodOtherClientProfileId;
                        synapseClientJurisdictionId = script.globalVars.bel_prodSynapseClientJurisdictionId;
                        otherClientJurisdictionId = script.globalVars.bel_prodOtherClientJurisdictionId;
                        caasEndpoint = script.globalVars.prodCaaSEndpoint;
                        credentialsId = 'prod_caas_conversion';
                        domain =  nyc_prodDomain;
                        break;
    }

    try {

      def oValueLookup = ['synapse': 'Dettonville - CentralAuth', 'other': 'Dettonville - Common ProdInfra SSL']
      def typesOfCerts = ['synapse','other']

      typesOfCerts.each { certType ->

        def oValue = oValueLookup[certType]
        def jurisdictionId
        def profileId
        def alias

        switch(certType) {
          case 'synapse':   jurisdictionId = synapseClientJurisdictionId;
                            profileId = synapseClientProfileId;
                            alias = 'client-access'
                            break;
          case 'other':     jurisdictionId = otherClientJurisdictionId;
                            profileId = otherClientProfileId;
                            alias = 'client-common'
                            break;
        }

          def csrFile = "sslcert-${certType}-${env}.csr"
          def jsonFile = "sslcert-${certType}-${env}.json"
          def caasResponseFile = "caas_response-${certType}-${env}.json"
          def fullPemFile = "sslcert-${certType}-${env}.pem"

          //figure out if we are overriding ou value. it defaults to env
          def ouValue = env
          if(ouForCert) ouValue = ouForCert

          //Create the private keys in the same keystore
          steps.sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -keystore multiclient.jks -genkeypair -alias ${alias} -keyalg rsa -keysize 2048 -dname \"CN=${cnForCert}, OU=${ouValue}, O=${oValue}\" -ext \"san=dns:${appHostName}.${domain}\""

          //Create the cert request from the private keys in the keystore
          steps.sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -certreq -keystore multiclient.jks -alias ${alias} -file ${csrFile}"

          //create the json request for caas. remove the word NEW as that is a type of CSR that caas does not accept but that is what the keytool gives us.
          def replacefile = steps.readFile(csrFile)
          replacefile = replacefile.replace("END NEW CERTIFICATE REQUEST", "END CERTIFICATE REQUEST")
          replacefile = replacefile.replace("BEGIN NEW CERTIFICATE REQUEST", "BEGIN CERTIFICATE REQUEST")
          //replace actual line breaks with newline character sequence for the json request to caas
          String massagedFile = replacefile.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n")
          def requestTemplate = """
          {
            "p10":"${massagedFile}",
            "jurisdictionId":"${jurisdictionId}",
            "profileId":"${profileId}"
          }
          """

          steps.writeFile file: "${jsonFile}", text: requestTemplate
          def jsonFileString = steps.readFile(jsonFile)
          //steps.echo "jsonFileString: ${jsonFileString}"

          def csrFileString = steps.readFile(csrFile)
          //steps.echo "csrFileString: ${csrFileString}"

          //steps.echo "csrFileString after NEW removed: ${replacefile}"

          //get password for caas
          def caas_pwd = steps.sh(returnStdout: true, script: "mcgetpw -label ${pwdLabel} | tr -d '\n'")

          // send request to caas
          steps.sh(returnStdout: true, script: "#!/bin/sh -e\n curl --tlsv1.2 -H 'Content-Type: application/json' -k -X POST --key /apps_data_01/security/keystores/jenkins-agent.key --cert /apps_data_01/security/keystores/jenkins-agent.crt --pass ${caas_pwd} --cacert /apps_data_01/security/keystores/jenkins-agent-truststore.pem -T ${jsonFile} ${caasEndpoint}cert/sign > ${caasResponseFile}")

          //get caas_response.json into a string
          def responseString = util.getStringFromFile(caasResponseFile)
          steps.echo "responseString: ${responseString}"

          //parse it for pem
          def pemText = util.parseJSON(responseString).certificate

          def subCaPemText = util.parseJSON(responseString).caCertificates

          concatCerts(pemText,subCaPemText,env,certType)

          def fullPemFileString = steps.readFile(fullPemFile)
          //steps.echo "fullPemFileString: ${fullPemFileString}"

          //import the signed certificates using same aliases as private keys
          steps.sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -import -keystore multiclient.jks -file ${fullPemFile} -alias ${alias}"

          steps.sh "#!/bin/sh -e\n keytool -noprompt -storepass password -keypass password -list -v -keystore multiclient.jks"

          //encode keyfile password while in code block that has access
          returnMap["encodedKeyfilePassword"] = steps.sh(returnStdout: true, script: "printf password | base64")
      }

      returnMap["encodedKeyfile"] = steps.sh(returnStdout: true, script: "base64 -i multiclient.jks -w 0")
      returnMap["encodedTruststore"] = steps.sh(returnStdout: true, script: "base64 -i ${truststoreFilename} -w 0")
      returnMap["encodedTruststorePassword"] = steps.sh(returnStdout: true, script: "printf ${truststorePassword} | base64")

    } finally {
      steps.deleteDir()
    }

  }

  private void concatCerts(String cert,ArrayList subCa,String env, String certType) {

    steps.writeFile file: "sslcert-${certType}-${env}-test1.pem", text: cert
    int x = 0
    subCa.each {
      steps.writeFile file: "sslcert-${certType}-${env}-test${x+2}.pem", text: "${it}"
      x++;
    }

    def catString = "cat "
    for(int i = 1;i<=(x+1);i++) {
      catString += "sslcert-${certType}-${env}-test${i}.pem "
    }
    catString += ">> sslcert-${certType}-${env}.pem"

    //steps.echo "catString:"
    //steps.echo catString

    steps.sh "#!/bin/sh -e\n ${catString}"

  }
}
