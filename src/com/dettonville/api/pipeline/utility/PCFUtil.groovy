/**
 * com.dettonville.api.pipeline.utility is a collection of utilities to perform common pipeline tasks.
 */
package com.dettonville.api.pipeline.utility

/**
 * Utility to interact with the PCF platform. Used to deploy artifacts and manage services.
 *
 * @Author grant.gortsema@dettonville.org
 */
class PCFUtil implements Serializable {

  /**
  * a reference to the pipeline that allows you to run pipeline steps in your shared libary
  */
  def steps

  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public PCFUtil(steps) {this.steps = steps}

  /**
  * use the CLI to do a CF push on an artifact and deploy it to a PCF env. This method assumes you have populated the keyMap
  * with values from the  getJKSFromCaaS method if you are setting synapseEnabled to true. This method will use the keymap to set different variables.
  * The aliases for these certs are client-common, and client-access. Other than the env variables that are being set in this
  * method, if you want to add any other pcf env varibles or override ones that you know are being set in here, you can prepare-agent
  * populate the keyMap with the values you want to take precedence and just prepend them with PCFENV_ . An example would
  * be to override the value that is being set for API_REGION = dev we would preopulate the keyMap with the values
  * keyMap['PCFENV_API_REGION'] = 'something else'. This will cause the varible API_REGION to be set, or if it is already
  * being set by the method it will override the value and use the value you have chosen. This version uses the following keytool:
  *
  * <groupId>com.dettonville</groupId>
  * <artifactId>ref-arch-keystore</artifactId>
  * <version>1.2.1</version>
  *
  * https://polaris-documentation.apps.nyc.pcfdev00.dettonville.int/starter-guides/  *
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param appHostName Name of the app, should be same as in manifest.
  * @param env what we are deploying to. values can be 'nyc-dev', 'nyc-stage', or 'bel-prod'
  * @param org what PCF org we want to deploy to
  * @param space what PCF space we want to deploy to
  * @param pcfCredentialsId the name of the credentialsId you want to use to deploy to PCF (configured in Jenkins)
  * @param vaultCredentialsId the name of the credentialsId you want to use for vault. If you are setting vaultEnabled to false then make this null. it will be ignored in any case.
  * @param vaultBackendId the name The string you have configured with vault for your generic backend id. If you have vaultEnabled to false this will be ignored.
  * @param keyMap A Map containing the encrypted cert information as wel as any special env variables your app would need. PCF env variables should be in the map in the form of PCFENV_<keyname> , <value>. env variables will be removed after they are set
  * @param synapseEnabled determins if we are setting synapse properties.
  * @param vaultEnabled determins if we are setting spring cloud vault variables
  * @param activeProfile sets springs active profile env variable. if you do not want to set one just give null value and it will not be set.
  * @param dirToPush optional relative directory to cf push from. defaults to current workspace directory.
  * @param manifestLoc an optional string to be passed to the -f option of pcf push
  */
  public void deployToPCFGoRouter(script, String appHostName, String env, String org, String space, String pcfCredentialsId, String vaultCredentialsId, String vaultBackendId, Map keyMap, boolean synapseEnabled, boolean vaultEnabled, String activeProfile, String dirToPush='.',String manifestLoc=null) {

    def appDomain = ''
    def paas_url = ''

    switch(env) {
      case 'nyc-dev': appDomain = script.globalVars.devDomain; paas_url = script.globalVars.devPaas_url; break;
      case 'nyc-stage': appDomain = script.globalVars.stageDomain; paas_url = script.globalVars.stagePaas_url; break;
      case 'bel-prod': appDomain = script.globalVars.prodDomain; paas_url = script.globalVars.prodPaas_url;  break;
      case 'nyc-prod': appDomain = script.globalVars.nyc_prodDomain; paas_url = script.globalVars.nyc_prodPaas_url; break;
      case 'jpn-': appDomain = script.globalVars.jpn_prodDomain; paas_url = script.globalVars.jpn_prodPaas_url; break;
    }

    def appRoute = "https://${appHostName}.${appDomain}"

      steps.withEnv(["CF_HOME=${script.env.WORKSPACE}"]) {

        try {

          steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${pcfCredentialsId}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
            steps.sh "cf login -a ${paas_url} -u ${script.PCF_USERNAME} -p ${script.PCF_PASSWORD} -o ${org} -s ${space}"
          }

          def pushString = ''
          if(manifestLoc) pushString = " -f ${manifestLoc}"

          steps.dir(dirToPush) {
            //push application but don't start it so we can set env variables
            steps.sh "cf push ${appHostName} ${pushString} --no-start"
          }

          if(activeProfile) {
            steps.sh "cf set-env ${appHostName} SPRING_PROFILES_ACTIVE ${activeProfile}"
          }

          if(vaultEnabled) {
            steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${vaultCredentialsId}", usernameVariable: 'VAULT_ID', passwordVariable: 'VAULT_SECRET']]) {
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_APP-ROLE_AUTH-PATH approle"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_APP-ROLE_ROLE-ID ${script.VAULT_ID}"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_APP-ROLE_SECRET-ID ${script.VAULT_SECRET}"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_AUTHENTICATION APPROLE"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_GENERIC_BACKEND ${vaultBackendId}"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_HOST ${env.replace('-','.')}.vault.dettonville.int"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_PORT 8200"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_SCHEME https"
              steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_ENABLED true"
              if(keyMap["vaultApplicationName"]) {
                def vaultApplicationName = keyMap["vaultApplicationName"]
                steps.sh "cf set-env ${appHostName} SPRING_CLOUD_VAULT_GENERIC_APPLICATION-NAME ${vaultApplicationName}"
              }
            }
          }

          def encodedClientKeyFile = keyMap["encodedKeyfile"]
          def encodedClientKeyFilePassword = keyMap["encodedKeyfilePassword"]

          steps.sh "cf set-env ${appHostName} KEYSTORE ${encodedClientKeyFile}"
          steps.sh "cf set-env ${appHostName} TRUSTSTORE /etc/ssl/certs/ca-certificates.crt"
          steps.sh "cf set-env ${appHostName} KEYSTORE_PASSWORD ${encodedClientKeyFilePassword}"

          if(synapseEnabled) {
            steps.sh "cf set-env ${appHostName} SYNAPSE_SSL_KEYSTORE_CLIENT_ALIAS client-common"
            steps.sh "cf set-env ${appHostName} SYNAPSE_SSL_KEYSTORE_LOCATION KEYSTORE"

            switch(env) {
              case 'nyc-dev':
                steps.sh "cf set-env ${appHostName} APIE_REGION US"
                steps.sh "cf set-env ${appHostName} APIE_PLATFORM DEV"
                steps.sh "cf set-env ${appHostName} APIE_ZONE STL"
                break;
              case 'nyc-stage':
                steps.sh "cf set-env ${appHostName} APIE_REGION US"
                steps.sh "cf set-env ${appHostName} APIE_PLATFORM STAGE"
                steps.sh "cf set-env ${appHostName} APIE_ZONE STL"
                break;
              case 'bel-prod':
                steps.sh "cf set-env ${appHostName} APIE_REGION EU"
                steps.sh "cf set-env ${appHostName} APIE_PLATFORM PROD"
                steps.sh "cf set-env ${appHostName} APIE_ZONE BEL"
                break;
              case 'jpn-':
                steps.sh "cf set-env ${appHostName} APIE_REGION US"
                steps.sh "cf set-env ${appHostName} APIE_PLATFORM PROD"
                steps.sh "cf set-env ${appHostName} APIE_ZONE KSC"
                break;
              case 'nyc-prod':
                steps.sh "cf set-env ${appHostName} APIE_REGION US"
                steps.sh "cf set-env ${appHostName} APIE_PLATFORM PROD"
                steps.sh "cf set-env ${appHostName} APIE_ZONE STL"
                break;
              }
            }

            //set custom variables including any overrides
            setPCFEnvVarsFromMap(script,appHostName,keyMap)

            //restarge and start the application
            steps.sh "cf restage ${appHostName}"
            steps.sh "cf start ${appHostName}"

            if(keyMap["instance_count"] != null) {
              def instanceCount = keyMap["instance_count"]
              steps.sh "cf scale ${appHostName} -i ${instanceCount}"
            }

        } finally {
            steps.sh "cf logout"
            steps.deleteDir()
        }
      }
  }

  // This sets pcf env variables from the key map
  private void setPCFEnvVarsFromMap(script,String appHostName, Map keyMap) {
    keyMap.each { k,v ->
      if(k.contains('PCFENV')) {
        steps.echo "set ${k.replace('PCFENV_','')} to ${v}"
        steps.sh "cf set-env ${appHostName} ${k.replace('PCFENV_','')} ${v}"
      }
    }
  }

}
