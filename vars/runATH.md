
Runs the [Acceptance Test Harness](https://repo.dettonville.int/stash/projects/API/repos/dcapi-automation-pipeline/browse/vars/runATH.groovy) in a configurable way

The configuration is divided into two parts, one related to the step itself and another related to how the ATH is run.

- To configure the step just use the step's parameters described below.
- To configure how the ATH runs, a metadata file (in YAML format) is used.

Further sections describe the metadata file in detail.

**Note that if the metadata file does not exist the ATH will only run with default configuration detailed below**

## Prerequisites
The following resources must be available in order to run the ATH pipeline:

- browserstack account credentials must be available in the jenkins credentials store.
- a java project to run the automated testing must be available as a git repo url to be used in the ATH. See `athGitRepo` config property mentioned below.
- a jenkins project with the ATH library added.  Have a look at the [setup tutorial](docs/tutorial-setup-library.md) to configure and start using Pipeline Library.

## How to Use

The list of step's params and the related default values are:

* athGitRepo: The URL to get the ATH sources. It can point to a local path (by using the file:// protocol) or a github destination. Defaults to https://repo.dettonville.int/stash/scm/api/dcapi-test.git. Can be overridden from the metadata file
* athGitBranch: The ATH revision to use, can be a branch or tag name or a commit id. Defaults to branch master. Can be overridden from the metadata file
* athConfigFile: A String indicating the file path (relative to the where this step is executed) to use as metadata file for the ATH pipeline, more details about the metadata file are provided belows. Defaults to configurations specified in the [runAthDefaults.yml](https://repo.dettonville.int/stash/projects/API/repos/dcapi-automation-pipeline/browse/resources/runAthDefaults.yml) sourced from the shared library resources.


You use the config Map key “jenkinsNodeLabel” to overwrite the node to use. If you need to specify more than one label just use label operators (e.g., "&&" and "||").

To make it usable in PR builders this step allows users to run the ATH using custom (typically previously built in the same Jenkinsfile) env/branch versions, for that you need to set the metadata file’s useLocalDir property to true to indicate the ATH run to use the local directory assuming the test code is local.

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
      configs.athConfigFile="metadata.yml"
      configs.athBranch="master"

      runATH(configs)
    }
}
```

## Test Configuration Hierarchy/Structure

The athConfigFile metadata file is a YAML file with the following structure:
```yaml
pipeline:
  athGitRepo: https://repo.dettonville.int/stash/scm/api/dcapi-test.git
  athGitBranch: acceptance-test-harness-1.59

  [global test config map options]

  testGroups:
    - [group config options]
      testCases:
        - [testcase config options]
```

If any option is specified in more than 1 level in the hierarchy. the lowest level config overrides the higher level(s).


## Configuration Options:

The config map options include the following keys and default values:
```
#!/usr/bin/env groovy

// following assumes the config map is defined in a Jenkinsfile or groovy file
def config = [:]

//
// essential/minimal params
//
config.athGitRepo = "https://repo.dettonville.int/stash/scm/api/dcapi-test.git"
config.athGitBranch = env.BRANCH_NAME
config.athConfigFile = 'jenkins/master.yml'

config.useLocalDir = false
config.useTestGroups = false
config.appEnvironment = "STAGE"

config.webPlatform = "browserstack"
config.metaFilterTags = null
config.storyName = null
config.jenkinsNodeLabel = "DEVCLD-LIN7"

config.jenkinsBsCredId = "dcapi_browserstack_creds"

//
// performance params
//
config.batchCount = "3"
config.jbehaveExecutionThreads = 1

//
// see BS configuration capabilities here:
// https://www.browserstack.com/automate/capabilities
//
config.browserstackResolution = "1920x1080"
config.browserstackWebOS = "Windows"
config.browserstackWebOSVersion = "10"

config.browserstackIdleTimeout = "300"
config.browserstackFirefoxVersion = "43"
config.browserstackChromeVersion = "65"
config.browserstackBrowser = "Chrome"
config.useBrowserstackLocalAgent = true
config.browserstackUseIdentifier = true
config.browserstackLocalIdentifier=UUID.randomUUID()

//
// static / other ATH environmental params
//
config.jenkinsBsCredId = "dcapi_browserstack_creds"
config.browserstackHubUrl = "hub-cloud.browserstack.com"
config.browserstackWebLocalfileupload = true
config.browserstackAcceptSSLCerts = true
config.browserstackProxyHost = "outboundproxy.dettonville.int"
config.browserstackProxyPort = 15768

config.emailList = "lee.johnson@dettonville.com"
config.alwaysEmailList = "lee.johnson@dettonville.com"
config.sendEmail = false
config.sendCDREmail = false
config.sendInlineReport = true
config.skipTests = true
config.failFast = false
config.failureIgnore = true

// pipeline debugging/diagnostics
config.debugPipeline = false
config.logLevel = "INFO"

config.checkoutDir = "."

config.browserstackProject = config.application
config.bsAgentBinType = "linux-x64"
config.bsAgentBinPath = "tmp/dcapi"

config.emailFrom= "DCAPI.TestAutomation@dettonville.com"

```



## Configuration Examples:

### Using Inline Declarative Config Map in Jenkinsfile

Using a config Map to define the configuration for the ATH tests:
```
#!/usr/bin/env groovy

def config = [:]
config.appEnvironment="PROD'
config.browserstackBrowser="Chrome"
config.metaFilterTags="+SimpleSignup"
config.jenkinsNodeLabel="DEVCLD-LIN7"
config.athConfigFile="jenkins/master.yml"

runATH(config)

```

### Using Declarative Yaml Configuration File

An example ATH config yaml:
```yaml
---
pipeline:
  athGitRepo: "https://repo.dettonville.int/stash/scm/api/dcapi-test.git"
  athGitBranch: "master"
  failFast: false

  appEnvironment: "STAGE"

  logLevel: "INFO"

  jenkinsNodeLabel: "DEVCLD-LIN7"

  jenkinsBsCredId: "dcapi_browserstack_creds"

  browserstackResolution: "1920x1080"
  browserstackWebOS: "Windows"
  browserstackWebOSVersion: "10"

  browserstackIdleTimeout: "300"
  browserstackFirefoxVersion: "43"
  browserstackChromeVersion: "65"
  browserstackBrowser: "Chrome"

  metaFilterTags: +SimpleSignup

  emailList: "lee.johnson@dettonville.com"
  alwaysEmailList: "lee.johnson@dettonville.com"

  batchCount: "3"

  testGroups:

    - name: "Windows-Chrome"
      browserstackBrowser: "Chrome"
      testCases:
        - browserstackChromeVersion: 68
        - browserstackChromeVersion: 67
        - browserstackChromeVersion: 65
        - browserstackChromeVersion: 64

    - name: "Windows-Firefox"
      browserstackBrowser: "Firefox"
      testCases:
        - browserstackFirefoxVersion: 61
        - browserstackFirefoxVersion: 60
        - browserstackFirefoxVersion: 59

    - name: "MacOS"
      browserstackBrowser: "Safari"
      browserstackWebOS: "OSX"
      browserstackWebOSVersion: "High Sierra"
      testCases:
        - browserstackSafariVersion: 11.1
        - browserstackSafariVersion: 11.0

    - name: "iPhone"
      testCases:
        - browserstackWebOS: "iPhone X"
          browserstackWebOSVersion: "11"
        - browserstackWebOS: "iPhone 7"
          browserstackWebOSVersion: "10.3"

```

Where:

*   athGitRepo: The URL to get the ATH sources. It can point to a local path or a github destination. If specified it will override the parameter in the runATH step
*   athGitBranch: The ATH revision to use can be a branch or tag name or a commit id. If specified it will override the parameter in the runATH step
*   failFast: If the run has to fail fast or not. Defaults to false if not specified
*   metaFilterTags: The list of metafilters to run. Defaults to nothing

In case you want to use the defaults for all properties you can use

```

runATH()

```
