
Runs the [Connectivity Test](https://repo.dettonville.int/stash/projects/API/repos/dcapi-automation-pipeline/browse/vars/runConnectivityTest.groovy) in a configurable way

The configuration is divided into two parts, one related to the step itself and another related to how the Connectivity Test is run.

- To configure the step just use the step's parameters described below.
- To configure how the Connectivity Test runs, a metadata file (in YAML format) is used.

Further sections describe the metadata file in detail.

**Note that if the metadata file does not exist the Connectivity Test will only run with default configuration detailed below**

## Prerequisites
The following resources must be available in order to run the Connectivity Test pipeline:

- a jenkins project with the Connectivity Test library added.  Have a look at the [setup tutorial](docs/tutorial-setup-library.md) to configure and start using Pipeline Library.

## How to Use

The list of step's params and the related default values are:

* configFile: A String indicating the file path (relative to the where this step is executed) to use as metadata file for the Connectivity Test pipeline, more details about the metadata file are provided belows. Defaults to configurations specified in the [runConnectivityTestDefaults.yml](https://repo.dettonville.int/stash/projects/API/repos/dcapi-automation-pipeline/browse/resources/runConnectivityTestDefaults.yml) sourced from the shared library resources.


To make it usable in PR builders this step allows users to run the Connectivity Test using custom (typically previously built in the same Jenkinsfile) env/branch versions, for that you need to set the metadata file’s useLocalDir property to true to indicate the Connectivity Test run to use the local directory assuming the test code is local.

```groovy
#!/usr/bin/env groovy

node("DEVCLD-LIN7") {
    dir("sources") {
      checkout scm
      List mavenEnv = \[
                "JAVA_HOME=${tool 'jdk8'}",
                'PATH+JAVA=${JAVA_HOME}/bin',
                "PATH+MAVEN=${tool 'mvn'}/bin"\]

      withEnv(mavenEnv) {
        sh "mvn clean install -DskipTests"
      }

      Map configs = [:]
      configs.configFile="metadata.yml"

      runConnectivityTest(configs)
    }
}
```

## Test Configuration Hierarchy/Structure

The configFile metadata file is a YAML file with the following structure:
```yaml
pipeline:
  gitRepoUrl: "https://repo.dettonville.int/stash/scm/api/testsslserver.git"

  logLevel: "INFO"
#  logLevel: "DEBUG"
#  useSimulationMode: true

  alwaysEmailDist: "SIT-engineer@dettonville.com, lee.johnson@dettonville.com"
#  alwaysEmailDist: "lee.johnson@dettonville.com"

  timeout: 4
  runcount: 5

  testList:
    - command: nslookup
#    - command: dig
#    - command: mtr
#    - command: traceroute
#    - command: bash
#    - command: ping
    - command: telnet
#    - command: sslpoke
    - command: curl
    - command: curl-verifycerts
    - command: httpclienttest
    - command: sslhandshake

  nodeList:
    - nodeLabel: jnk2stl0
      network: nyc-prod-admin
      testNetworks:
        - prod
        - prod-admin
        - nyc-prod
        - jpn-
     [jenkins node maps]

  networks:
    prod:
      siteList:
        - endpoint: internal.cicd.developer.dettonville.int
        - endpoint: api.dettonville.org
        - endpoint: sandbox.api.dettonville.org

    external:
      httpProxyHost: outboundproxy.dettonville.int
      httpProxyPort: 15768
      siteList:
        - endpoint: rally1.rallydev.com
        - endpoint: api.mailinator.com

    [defined test network maps]

...

```


## Configuration Options:

The config map options include the following keys and default values:
```
#!/usr/bin/env groovy

// following assumes the config map is defined in a Jenkinsfile or groovy file
def config = [:]

//
// essential/minimal params
//
config.configFile = 'connectivity-check-defaults.yml'

//
// static / other Connectivity Test environmental params
//
config.emailDist = "lee.johnson@dettonville.com"
config.alwaysEmailDist = "lee.johnson@dettonville.com"
config.sendEmail = false
config.failFast = false

// pipeline debugging/diagnostics
config.debugPipeline = false
config.logLevel = "INFO"
config.emailFrom= "DCAPI.TestAutomation@dettonville.com"

```



## Configuration Examples:

### Using Inline Declarative Config Map in Jenkinsfile

Using a config Map to define the configuration for the Connectivity Test tests:
```
#!/usr/bin/env groovy

def config = [:]
config.configFile="jenkins/connectivity-tests.yml"

runConnectivityTest(config)

```

### Using Declarative Yaml Configuration File

An example Connectivity Test config yaml:
```yaml
---
---
## for info on how to configure pipeline - see here:
## ref: https://repo.dettonville.int/stash/projects/API/repos/dcapi-automation-pipeline/browse/vars/runATH.md
pipeline:
  gitRepoUrl: "https://repo.dettonville.int/stash/scm/api/testsslserver.git"

  logLevel: "INFO"
#  logLevel: "DEBUG"
#  useSimulationMode: true

#  emailDist: "lee.johnson@dettonville.com"
#  alwaysEmailDist: "lee.johnson@dettonville.com"
#  emailFrom: "DCAPI.TestAutomation@dettonville.com"

  timeout: 10
  runcount: 2

  testList:
    - command: nslookup
    - command: curl
    - command: telnet
    - command: sslhandshake

  nodeList:
    - nodeLabel: jnk2stl0
      network: nyc-prod-admin
      testNetworks:
        - prod-admin
        - nyc-prod-admin
        - jpn-admin
        - external
    - nodeLabel: jnk2stl1
      network: nyc-prod-admin
      testNetworks:
        - prod-admin
        - nyc-prod-admin
        - jpn-admin
        - external

  networks:
    prod-admin:
      siteList:
        - endpoint: developer.dettonville.org
        - endpoint: api.dettonville.org
        - endpoint: sandbox.api.dettonville.org
        - endpoint: sandbox.proxy.api.dettonville.org
        - endpoint: dcapiadmin.dettonville.org
        - endpoint: iapp.dettonville.int
        - endpoint: jenkins.sandbox.api.dettonville.int

    nyc-prod-admin:
      siteList:
        - endpoint: 10.154.246.101:443
        - endpoint: 10.154.246.136:10443
        - endpoint: nyc.jenkins.sandbox.api.dettonville.int
        - endpoint: nyc.internal.cicd.sandbox.proxy.api.dettonville.int
        - endpoint: nyc.internal.cicd.developer.dettonville.int

    jpn-admin:
      siteList:
        - endpoint: jpn.jenkins.sandbox.api.dettonville.int
        - endpoint: jpn.internal.cicd.sandbox.proxy.api.dettonville.int
        - endpoint: jpn.internal.cicd.developer.dettonville.int

    qa-admin:
      siteList:
        - endpoint: stage.dcapiadmin.dettonville.org
        - endpoint: stage2.dcapiadmin.dettonville.org
          ip:
            - 209.64.211.214
        - endpoint: 209.64.211.214
          dns:
            - stage2.dcapiadmin.dettonville.org
        - endpoint: stage.api.dettonville.org
          ip:
            - 10.157.246.63
        - endpoint: 10.157.246.63
          dns:
            - stage.api.dettonville.org
        - endpoint: stage.proxy.api.dettonville.org
        - endpoint: sbx.stage.api.dettonville.org
        - endpoint: stage.iapp.dettonville.int
        - endpoint: stage.developer.dettonville.org
        - endpoint: stage.sso.api.dettonville.int
        - endpoint: dev.developer.dettonville.org
        - endpoint: dev.api.dettonville.org
        - endpoint: dev.eportal.dettonville.org
        - endpoint: dev.sso.eportal.dettonville.org
        - endpoint: dev.dcapiadmin.dettonville.org
        - endpoint: internal.cicd.stage.developer.dettonville.int

    external:
      httpProxyHost: outboundproxy.dettonville.int
      httpProxyPort: 15768
      siteList:
        - endpoint: rally1.rallydev.com

```


In case you want to use the defaults for all properties you can use

```

runConnectivityTest()

```
