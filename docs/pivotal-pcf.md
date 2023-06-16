
## Build and Deploy Spring Boot apps to PCF

### Requirements
Have a look at the [pipeline setup tutorial](docs/tutorial-setup-library.md) to configure and start using the shared pipeline library.

This a Jenkins library built to make it easier to configure PCF build-deploy pipelines without necessarily knowing about Jenkinsfile syntax.

When using this library, your Jenkinsfile should look something like this:

```groovy
#!/usr/bin/env groovy

@Library('dcapi-automation-library')

Map params = [:]

params['ymlConfigFile']="pcf/deployPcf.yml"

pcfBuildDeployMvn(this, params)

```

The example above loads the library, and calls the shared `pcfBuildDeployMvn` pipeline.

As an argument, `pcfBuildDeployMvn` receives the path to a configuration yaml file to define what options the pipeline will use.

For example, the config yaml file may look something like this:

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/api/devportal.git'
        branch: 'feature/pcf_pipeline'
        apphost_name: 'devportal'
        cn_name: 'devportal'
        sonar: false
        skip_tests: true

    services:
      services:
          user_defined_services:
            - service_name: 'devportal-db'
              use_credentials: true
              environment_overrides:
                nyc_dev:
                  credentials_string: 'dev_dcapi_oracle_db'
                  param: >
                    -p '{\"jdbcUrl\":\"jdbc:oracle:thin:#UNAME/#PWD@cds0stl20.dettonville.int:1527:dcup1s\",\"host\":\"cds0stl20.dettonville.int\",\"port\":\"1527\",\"username\":\"#UNAME\",\"password\":\"#PWD\"}'
            - service_name: 'dcapi-services-postgres-db'
              use_credentials: true
              environment_overrides:
                nyc_dev:
                  credentials_string: 'dev_dcapi_postgres_db'
                  param: >
                    -p '{\"uri\":\"postgres://#UNAME:#PWD@cds0stl20.dettonville.int:27017/initializer\"}'

    pcf_environments:
      nyc_dev:
        - pcf_org: 'DCAPI'
          pcf_space: 'DCAPI_Services'
          credentials_string: 'dcapi-pcf-nyc-dev-deploy'
```

### Alternative: Pass yaml directly as parameter into pipeline
Optionally you can specify the yaml inline in the Jenkinsfile in the following way:

```
#!/usr/bin/env groovy

@Library('dcapi-automation-library')

def ymlConfig='''
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/api/devportal.git'
        branch: 'feature/pcf_pipeline'
        apphost_name: 'devportal'
        cn_name: 'devportal'
        sonar: false
        skip_tests: true

    services:
      services:
          user_defined_services:
            - service_name: 'devportal-db'
              use_credentials: true
              environment_overrides:
                nyc_dev:
                  credentials_string: 'dev_dcapi_oracle_db'
                  param: >
                    -p '{\"jdbcUrl\":\"jdbc:oracle:thin:#UNAME/#PWD@cds0stl20.dettonville.int:1527:dcup1s\",\"host\":\"cds0stl20.dettonville.int\",\"port\":\"1527\",\"username\":\"#UNAME\",\"password\":\"#PWD\"}'
            - service_name: 'dcapi-services-postgres-db'
              use_credentials: true
              environment_overrides:
                nyc_dev:
                  credentials_string: 'dev_dcapi_postgres_db'
                  param: >
                    -p '{\"uri\":\"postgres://#UNAME:#PWD@cds0stl20.dettonville.int:27017/initializer\"}'

    pcf_environments:
      nyc_dev:
        - pcf_org: 'DCAPI'
          pcf_space: 'DCAPI_Services'
          credentials_string: 'dcapi-pcf-nyc-dev-deploy'
'''

Map params = [:]

params['yml']=ymlConfig

pcfBuildDeployMvn(this, params)
```

The following are PCF configuration examples:

### Simple Build with unit tests and Sonar (CI only)
A simple build pipeline that will build an app and run unit tests and run sonar. It will not deploy anywhere but rather just run as a CI job. 

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'

```

### Simple Build with unit tests and Sonar using custom Sonar exclusions (CI only)
A simple build pipeline that will build an app and run unit tests and run sonar. It will use custom sonar exclusions you set instead of the default. it will not deploy anywhere but rather just run as a CI job.

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        sonar_exclusions: '**/domain/**,**/constants/**'
```
 
### Simple Build with unit tests only (CI only)
A simple build pipeline that will build an app and run unit tests but will not run sonar. Since sonar is run by default you have to explicitly leave it out. It will not deploy anywhere but rather just run as a CI job. 


```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        sonar: false
```

### Simple Build using custom POM file
a simple build pipeline that will build an app and run unit tests and sonar using a custom POM file. This is the typical case for a Polaris starter project

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/cscauth/auth-server.git'
        branch: 'master'
        apphost_name: 'dettonville-auth'
        cn_name: 'dettonville-auth-client'
        pom_file: 'openid/pom.xml'
        manifest_file: './openid/manifest.yml'     
```

### Simple Build with unit tests, Sonar and Findbugs (CI only)
A simple build pipeline that will build an app and run unit tests and run sonar. after that it will also run Findbugs if you have it configured. It will not deploy anywhere but rather just run as a CI job. 

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        findbugs: true
```

### Simple Build and Deploy

This is one of the simplest pipelines you can run. A simple build and deploy to a single org/space in PCF Dev. It will run sonar as well since that defaults to true.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
       
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf' 
```

### Simple Build and Deploy using a custom POM file and a custom manifest file
This pipeline will build your project using a POM file you specify and will use a manifest file you specify to deploy to PCF Dev

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/cscauth/auth-server.git'
        branch: 'master'
        apphost_name: 'dettonville-auth'
        cn_name: 'dettonville-auth-client'
        pom_file: 'openid/pom.xml'
        manifest_file: './openid/manifest.yml'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Auth_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'auth_dev_pcf'
```
 
### Build and Deploy a single app to one org/space in dev and set or override env variables in PCF
This pipeline demonstrates how to deploy an app to PCF and how to set env variables in your target PCF env. These will either set the env variables or, if the pipeline is already setting them, these will override what the pipeline is currently setting.

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        sonar: false
        skip_tests: true
        environment_overrides:
          nyc_dev:
            - key: 'somekey'
              value: 'somevalue'
            - key: 'someotherkey'
              value: 'someothervalue'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
```

### Builds single application to single PCF space, skippings Sonar, skipping unit tests, and using environment based spring profiles.
This pipeline builds and deploys and application to PCF. By setting env_profiles to true we tell the pipeline to deploy with a spring profile of dev stage or prod depending where you are deploying. This is for legacy, but I recommend that you do not use any default spring profiles for deployment. In addition here we have an example of how to skip unit tests an how to skip the sonar phase.

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        sonar: false
        skip_tests: true
      
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'  
```

 
### Builds single application to single PCF space, and runs findbugs if it is configured
This pipeline will build and deploy a single application to PCF dev and before deployment it will run findbugs if it has been configured (mvn clean compile -Dfindbugs=true). This defaults to false so you need to explicitly specify you want to run it.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        findbugs: true
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'  
```


 
## Build and Deploy Services to PCF
 
Deploys your spring cloud config server to an org/space in both PCF Dev and PCF Stage
This pipeline will create your spring cloud config server and deploy it to the specified org/spaces. It will always destroy and re-create your cloud config server in order to update.

```yaml
release:
    services:
      cloud_config:
        name: 'consumer-lifecycle-config-server'
        gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-test.git'
        branch: 'master'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_stage_pcf'  
```

 
### Deploys your spring cloud config server to an org/space in both PCF Dev and PCF Stage - create only, do not update
This pipeline will create your spring cloud config server and deploy it to the specified org/spaces. It will only create the server it will not update it if it exists. this is to save time for development but not a recommended approach. The issue it is solving is that it takes 5 min per space to deploy the spring cloud config server. This is accomplished by setting the create_only property to true

```yaml
release:
    services:
      cloud_config:
        name: 'consumer-lifecycle-config-server'
        gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-test.git'
        branch: 'master'
        create_only: true
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_stage_pcf'  
```

 
### Deploy a PCF Service such as circuit breaker pcf a single org/space in dev
This is an example of how to create a PCF service and deploy it to PCF. you can combine it with other elements as well such as build and deploy etc.

```yaml
release:
    services:
      pcf_services:              
        - service_name: 'my-circuit-breaker-dashboard'
          plan: 'standard'
          service: 'p-circuit-breaker-dashboard'
        - service_name: 'spring-config-refresh-bus'
          plan: 'standard'
          service: 'p-rabbitmq'
      pcf_environments:
        nyc_dev:
          - pcf_org: 'CSC_Auth_Service'
            pcf_space: 'csc_DEV'
            credentials_string: 'auth_dev_pcf'  
```

 
 
### Deploy User Defined Service that connects to oracle and deploys to 2 different spaces in dev with credentials, and with environmental overrides 
This is an example of a pipeline that deploys a user defined service to multiple spaces in dev. This user defined services needs to use username and password that are provided in the form of jenkins credentials. Notice the replace pattern that is used. #UNAME and #PWD

```yaml
release:
    services:
      user_defined_services:
        - service_name: 'dc-emoji-db-acceptance'
          use_credentials: true
          environment_overrides:
            nyc_dev:
              credentials_string: 'dev_notification_oracle_db'
              param: >
                -p '{\"jdbcUrl\":\"jdbc:oracle:thin:#UNAME/#PWD@cds0stl20.dettonville.int:1527:dcup1s\",\"host\":\"cds0stl20.dettonville.int\",\"port\":\"1527\",\"username\":\"#UNAME\",\"password\":\"#PWD\"}'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_dev_pcf'
```

 
### Deploy your user defined service that connects to oracle to 2 different spaces in dev with environmental overrides and without credentials
This is an example pipeline of a user defined service that does not require credentials but does require an env override (most of them will)

```yaml
release:
    services:
      services:
      user_defined_services:
        - service_name: 'polaris_audit_db'
          environment_overrides:
            nyc_stage:
              param: >
                -p '{\"uri\":\"postgres://conla_stage_rw:fo02mftk4@10.158.150.168:27017/initializer\"}'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_dev_pcf'
```

 
## Integration Testing
### Run integration tests against a specific org and space
This pipeline will run int tests against a space in dev. the command this generates is: 
mvn clean test -Dmaven.test.failure.ignore=true -Dtarget.service.base-url=.apps.nyc.pcfdev00.dettonville.int

```yaml
release:
    integration_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-test.git'
      branch: 'master'
      publish: false
      spockreport_title: 'spock-tests'
      timingreport_title: 'timing-tests'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
```

 
### Run integration tests against a specific org and space and add in an arbitrary parameter string
This pipeline will run int tests against a space in dev and add in an arbitrary parameter string for that space. The code this generates looks like:
mvn clean test -Dmaven.test.failure.ignore=true -Dtarget.service.base-url=.apps.nyc.pcfdev00.dettonville.int '-Dapp.test.variable=some thing'

```yaml
release:
    integration_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-test.git'
      branch: 'master'
      publish: false
      spockreport_title: 'spock-tests'
      timingreport_title: 'timing-tests'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
          testing_params: -Dapp.test.variable="some thing"
```

 
### Run integration tests skipping specific spaces
This pipeline will test 2 spaces in PCF Dev and will skip one of the spaces based on the flag that is set.

```yaml
release:
    integration_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/functionaltests.git/'
      branch: 'master'
      publish: true
      spockreport_title: 'spock-tests'
      timingreport_title: 'timing-tests'
      
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'notification_dev_pcf'
          testing_params: "-Pmo-router-test -Dspring.profiles.active=dev"
          skip_integration_tests: true
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'notification_dev_pcf'
          route_suffix: '-int'
          testing_params: "-Pmo-router-test -Dspring.profiles.active=dev"
```
 
## Performance testing

### Run performance tests against a specific org and space
This pipeline will run int tests against a space in dev. the command this generates is: 
mvn clean verify -Pperformance -Dmaven.test.failure.ignore=true -Dtarget.service.base-url=.apps.nyc.pcfdev00.dettonville.int

```yaml
release:
    performance_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/performancetests.git'
      branch: 'master'
      publish: true
      publish_title: 'jmeter-tests'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'notification_dev_pcf'                  
```

### Run performance tests against a specific org and space and add in an arbitrary parameter string
This pipeline will run int tests against a space in dev and add in an arbitrary parameter string for that space. The code this generates looks like:
mvn clean verify -Pperformance -Dmaven.test.failure.ignore=true -Dtarget.service.base-url=.apps.nyc.pcfdev00.dettonville.int -DnumberOfThreads=10 -DrampUp=1

```yaml
release:
    performance_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/performancetests.git'
      branch: 'master'
      publish: true
      publish_title: 'jmeter-tests'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'notification_dev_pcf'
          testing_params: "-Pmt-api-test -Dspring.profiles.active=dev"
          performance_params: "-DnumberOfThreads=10 -DrampUp=10"
```
 
### Run integration tests skipping specific spaces
This pipeline will test 2 spaces in PCF Dev and will skip one of the spaces based on the flag that is set.

```yaml
release:
    performance_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/performancetests.git'
      branch: 'master'
      publish: true
      publish_title: 'jmeter-tests'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'notification_dev_pcf'
          performance_params: "-DnumberOfThreads=10 -DrampUp=10 -DloopCount=10 -DserviceName=MT_ShortText"
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'notification_dev_pcf'
          route_suffix: '-int'
          skip_performance_tests: true
```

## Artifactory Deployment and Releasing and Versioning
### Deploy to Artifactory
This pipeline deploys your artifact to Artifactory. Notice you will need artifactory credentials.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/test-snapshot-versioning'
        artifactory_deploy: true
        artifactory_creds: 'artifactory-publisher'
```

 
### Create snapshot
This pipeline will create a snapshot branch based off the pom you have supplied and jar up the application. We create the branch because deployments are 'slice in time' deployments and the manifest and pom are used. IMPORTANT: if you create a snapshot at the end of the pipeline your branch you specified will have the pom updated with an incremented minor version.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/test-snapshot-versioning'
        create_snapshot: true
        artifactory_creds: 'artifactory-publisher'
        bitbucket_creds: 'dcapi_bitbucket'
```

### Create release
This pipeline will create a release branch based off the pom you have supplied and jar up the application. We create the branch because deployments are 'slice in time' deployments and the manifest and pom are used.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/test-snapshot-versioning'
        create_release: true
        artifactory_creds: 'artifactory-publisher'
        bitbucket_creds: 'dcapi_bitbucket'
```

### Deploy from  snapshot
This pipeline will get the version from the pom and look for that RELEASE version in Artifactory. it will use that jar and manifest and pom from that release branch to deploy to PCF when indicated.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/test-snapshot-versioning'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
          deploy_from_snapshot: true
```

### Deploy from  release
This pipeline will get the version from the pom and look for that RELEASE in Artifactory. it will use that jar and manifest and pom from that release branch to deploy to PCF when indicated.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/test-snapshot-versioning'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
          deploy_from_release: true
```

### Deploy from  release and use a JAR file that has a suffix such as -runnable (usually Polaris)
This pipeline will get the version from the pom and look for that RELEASE in Artifactory. it will use that jar and manifest and pom from that release branch to deploy to PCF when indicated. The main part of this example is to show that it will actually look for the -runnable jar rather than the normal jar it would infer from the pom. 

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/test-snapshot-versioning'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
          deploy_from_release: true
          jar_suffix: '-runnable'
```

## Notifications for Pipelines
### Send email notifications to team members and a flowdock room when a build fails or is fixed
This pipeline will build an app and notify the recipients listed in the address_list when the build fails or when it is fixed. In there is an example of a user email and a flowdock room email.

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lookup-user-services.git'
        branch: 'master'
        apphost_name: 'consumer-lookup-user-services'
        cn_name: 'consumer-lifecycle-services-client'
        skip_build: true
        sonar: false
        #findbugs: true
    notifications:
      email:
        address_list:
          - 'ljohnson@dettonville.org'
          - 'consumer-shared-components@dettonville.flowdock.com'
```

 
## Vault Integration
### Build your app and deploy and set env variables related to HashiCorp Vault.
This pipeline will set env variables in your app that your app can use to connect to HashiCorp Vault

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'feature/vault-integration'
        apphost_name: 'consumer-lifecycle-services-vault'
        cn_name: 'consumer-lifecycle-services-client'
        vault:
          nyc_dev:
            vault_credentials: 'lifecycle_nyc_dev_vault_creds'
            app_role: 'csc_lifecycle'
            backend_id: 'csc_lifecycle'
            application_name: 'secrets'
          nyc_stage:
            vault_credentials: 'lifecycle_nyc_stage_vault_creds'
            app_role: 'csc_lifecycle'
            backend_id: 'csc_lifecycle'
            application_name: 'secrets'
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_stage_pcf'
```
 
### Write a plain text value to dev vault from the pipeline to a specific vault directory
 
```yaml
release:
    vault:
      environments:
        nyc_prod:
          vault_credentials: 'lifecycle_nyc_prod_vault'
          verbose: 'true'
          directory: 'csc_lifecycle/secrets'
          insert:
            - key: 'key1'
              value: 'value1'           
```

### Write multiple plain text values to multiple vault environments to a specific vault directory
 
```yaml
release:
    vault:
      environments:
        nyc_prod:
          vault_credentials: 'lifecycle_nyc_prod_vault'
          directory: 'csc_lifecycle/secrets'
          insert:
            - key: 'key1'
              value: 'value1'
            - key: 'key2'
              value: 'value2'
```

### Write a key/value pair stored in Jenkins credentials to dev vault from the pipeline into a specific vault directory
 
```yaml
release:
    vault:
      environments:
        nyc_prod:
          vault_credentials: 'lifecycle_nyc_prod_vault'
          verbose: 'true'
          directory: 'csc_lifecycle/secrets'
          jenkins_credentials:
            - credentials_id: 'test-insert-vault-creds1'
```

### Write multiple key/value pairs stored in Jenkins credentials to multiple vault environments to a specific vault directory
 
```yaml
release:
    vault:
      environments:
        nyc_prod:
          vault_credentials: 'lifecycle_nyc_prod_vault'
          verbose: 'true'
          directory: 'csc_lifecycle/secrets'
          jenkins_credentials:
            - credentials_id: 'test-insert-vault-creds1'
            - credentials_id: 'test-insert-vault-creds2'
```

### Write multiple plain text values and multiple key/value pairs stored in Jenkins credentials to multiple vault environments to a specific vault directory
 
```yaml
release:
    vault:
      environments:
        nyc_prod:
          vault_credentials: 'lifecycle_nyc_prod_vault'
          verbose: 'true'
          directory: 'csc_lifecycle/secrets'
          jenkins_credentials:
            - credentials_id: 'test-insert-vault-creds1'
            - credentials_id: 'test-insert-vault-creds2'
          insert:
            - key: 'key1'
              value: 'value1'
            - key: 'key2'
              value: 'value2'
```

## Autoscaling
### set default instance counts for your apps

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        autoscaling:
            default_instance_count: 2
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_INT'
          credentials_string: 'lifecycle_stage_pcf'
```
 
### override default instance counts for a specific org/space

```yaml
release:
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        autoscaling:
            default_instance_count: 2
            pcf_environments:
              nyc_dev:
                - pcf_org: 'CSC_Lifecycle_Service'
                  pcf_space: 'csc_DEV'
                  instance_count: 3
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_INT'
          credentials_string: 'lifecycle_stage_pcf'
```
 
## Multi Purpose Pipelines
### Build your app, deploy cloud config server and deploy to a single org/space in dev and stage
This pipeline will build an app and deploy cloud config to each space the app is getting deployed to before deploying the application to those spaces. This shows how you can target multiple orgs and spaces as well as multiple PCF env and how you can create a pipeline that will deploy dependent services when you deploy your app.

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        instance_count: 3
        sonar: false
        skip_tests: true
      
    services:
      cloud_config:
        name: 'consumer-lifecycle-config-server'
        gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-test.git'
        branch: 'master'
    
    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
        
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_stage_pcf'
```

 
### Build multiple apps, deploy services and deploy apps to a multiple org/spaces in all PCF orgs/spaces
This pipeline shows how you can build multiple apps and deploy them to multiple org/spaces in multiple pcf environments while deploying dependent services to those env as well.
  

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-services.git'
        branch: 'master'
        apphost_name: 'consumer-lifecycle-services'
        cn_name: 'consumer-lifecycle-services-client'
        instance_count: 1
        findbugs: true
      
      - gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lookup-user-services.git'
        branch: 'master'
        apphost_name: 'consumer-lookup-user-services'
        cn_name: 'consumer-lookup-user-services-client'
        instance_count: 3
        findbugs: true
    
    services:
      cloud_config:
        name: 'consumer-lifecycle-config-server'
        gitrepo: 'https://repo.dettonville.int/stash/scm/csclife/consumer-lifecycle-test.git'
        branch: 'master'
        create_only: true

    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'lifecycle_dev_pcf'
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_dev_pcf'
          route_suffix: '-int'
      nyc_stage:
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'lifecycle_stage_pcf'
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_QA'
          credentials_string: 'lifecycle_stage_pcf'
          route_suffix: '-qa'
        - pcf_org: 'CSC_Lifecycle_Service'
          pcf_space: 'csc_Perf'
          credentials_string: 'lifecycle_stage_pcf'
          route_suffix: '-perf'
       nyc_prod:
        - pcf_org: 'CSC_Lifecycle_Service_prod'
          pcf_space: 'csc_Prod'
          credentials_string: 'lifecycle_nyc_prod_pcf'
       bel_prod:
        - pcf_org: 'CSC_Lifecycle_Service_prod'
          pcf_space: 'csc_Prod'
          credentials_string: 'lifecycle_bel_prod_pcf'
       jpn_prod:
        - pcf_org: 'CSC_Lifecycle_Service_prod'
          pcf_space: 'csc_Prod'
          credentials_string: 'lifecycle_jpn_prod_pcf'
```
 
### Build an app, create dependent services, deploy to multiple spaces in multiple env and run integration tests and performance tests against specific env and spaces
This pipeline will deploy an app and create dependent services and run performance tests and integration tests against multiple env and spaces targeting specific ones to skip different types of testing

```yaml
release:
    env_profiles: true
    spring_boot:
      - gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/mobileterminateinternalapi.git'
        branch: 'master'
        apphost_name: 'mobile-terminate-provider-app'
        cn_name: 'notification.service.dettonville.int'
        instance_count: 3
        sonar: true
        skip_tests: true
    services:
      pcf_services:
          - service_name: 'spring-config-refresh-bus'
            plan: 'standard'
            service: 'p-rabbitmq'
    integration_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/functionaltests.git/'
      branch: 'master'
      publish: true
      spockreport_title: 'spock-tests'
      timingreport_title: 'timing-tests'

    performance_tests:
      gitrepo: 'https://repo.dettonville.int/stash/scm/cscnot/performancetests.git'
      branch: 'master'
      publish: true
      publish_title: 'jmeter-tests'

    pcf_environments:
      nyc_dev:
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_DEV'
          credentials_string: 'notification_dev_pcf'
          testing_params: "-Pmt-api-test -Dspring.profiles.active=dev"
          performance_params: "-DnumberOfThreads=10 -DrampUp=10 -DloopCount=10 -DserviceName=MT_ShortText"
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'notification_dev_pcf'
          route_suffix: '-int'
          testing_params: "-Pmt-api-test -Dspring.profiles.active=dev"
          performance_params: "-DnumberOfThreads=10 -DrampUp=10 -DloopCount=10 -DserviceName=MT_ShortText"
      nyc_stage:
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_Integration'
          credentials_string: 'notification_stage_pcf'
          skip_integration_tests: true
          skip_performance_tests: true
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_QA'
          credentials_string: 'notification_stage_pcf'
          route_suffix: '-qa'
          testing_params: "-Pmt-api-test -Dspring.profiles.active=stage"
          skip_performance_tests: true
        - pcf_org: 'CSC_Notification_Service'
          pcf_space: 'csc_Perf'
          credentials_string: 'notification_stage_pcf'
          route_suffix: '-perf'
          performance_params: "-DnumberOfThreads=10 -DrampUp=10 -DloopCount=10 -DserviceName=MT_ShortText"
          skip_integration_tests: true
```

 