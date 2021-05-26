
If running the branch feature/browsertests
The pipeline will check for the existance of the config yaml feature/browsertests.yaml

Assuming the config yml exists with the following defintion:

```yml
---
pipeline:
  athGitRepo: "https://repo.dettonville.int/stash/scm/api/dcapi-test.git"
  appEnvironment: "STAGE"
  metaFilterTags: +smoketest
  parallelRunCount: 11

  testGroups:

    - name: "Windows-Chrome"
      browserstackBrowser: "Chrome"
      parallelRunCount: 3
      testCases:
        - browserstackChromeVersion: 68
        - browserstackChromeVersion: 67
        - browserstackChromeVersion: 65
        - browserstackChromeVersion: 64

    - name: "Windows-Firefox"
      browserstackBrowser: "Firefox"
      parallelRunCount: 3
      testCases:
        - browserstackFirefoxVersion: 61
        - browserstackFirexVersion: 60
        - browserstackFirefoxVersion: 59

    - name: "MacOS"
      parallelRunCount: 3
      browserstackBrowser: "Safari"
      browserstackWebOS: "OSX"
      browserstackWebOSVersion: "High Sierra"
      testCases:
        - browserstackSafariVersion: 11.1
        - browserstackSafariVersion: 11.0
```

The pipeline would then run the following mvn commands:

```bash

[INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn clean test -Denv=STAGE -DskipTests=true

[split-group0-testcase0-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=68 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:68 -e

[split-group0-testcase0-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=68 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:68 -e

[split-group0-testcase0-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=68 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:68 -e

[split-group0-testcase1-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=67 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:67 -e

[split-group0-testcase1-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=67 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:67 -e

[split-group0-testcase1-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=67 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:67 -e

[split-group0-testcase2-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=65 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:65 -e

[split-group0-testcase2-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=65 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:65 -e

[split-group0-testcase2-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=65 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:65 -e

[split-group0-testcase3-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=64 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:64 -e

[split-group0-testcase3-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=64 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:64 -e

[split-group0-testcase3-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=64 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:64 -e

[split-group1-testcase0-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=61 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:61 -e

[split-group1-testcase0-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=61 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:61 -e

[split-group1-testcase0-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=61 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:61 -e

[split-group1-testcase1-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=43 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:43 -e

[split-group1-testcase1-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=43 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:43 -e

[split-group1-testcase1-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=43 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:43 -e

[split-group1-testcase2-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=59 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:59 -e

[split-group1-testcase2-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=59 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:59 -e

[split-group1-testcase2-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='Windows' -Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX  -Dbrowserstack.firefox.version=59 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox -Dinjected.tags=configuration:Firefox:59 -e

[split-group2-testcase0-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='OSX' -Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.1 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Safari -Dinjected.tags=configuration:Safari:11.1 -e

[split-group2-testcase0-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='OSX' -Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.1 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Safari -Dinjected.tags=configuration:Safari:11.1 -e

[split-group2-testcase0-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='OSX' -Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.1 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Safari -Dinjected.tags=configuration:Safari:11.1 -e

[split-group2-testcase1-run1] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='OSX' -Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.0 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Safari -Dinjected.tags=configuration:Safari:11.0 -e

[split-group2-testcase1-run2] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='OSX' -Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.0 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Safari -Dinjected.tags=configuration:Safari:11.0 -e

[split-group2-testcase1-run3] [INFO] runATH : runAcceptanceTest(): mvnCmd=/sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test -Denv=STAGE ... -Dbrowserstack.web.os='OSX' -Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari -Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.0 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Safari -Dinjected.tags=configuration:Safari:11.0 -e

[INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn test serenity:aggregate -Dserenity.outputDirectory=target/site/serenity -Denv=STAGE -DskipTests=true
```

```bash
[INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn clean test -Denv=STAGE -DskipTests=true

[split-group0-testcase0-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=68 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:68 -e

[split-group0-testcase0-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=68 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:68 -e

[split-group0-testcase0-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=68 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:68 -e

[split-group0-testcase1-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=67 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:67 -e

[split-group0-testcase1-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=67 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:67 -e

[split-group0-testcase1-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=67 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:67 -e

[split-group0-testcase2-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=65 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:65 -e

[split-group0-testcase2-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=65 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:65 -e

[split-group0-testcase2-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=65 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:65 -e

[split-group0-testcase3-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=64 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:64 -e

[split-group0-testcase3-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=64 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:64 -e

[split-group0-testcase3-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=chrome ... -Dbrowserstack.chrome.version=64 ...
-Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3 -DserenityReport=true -DskipTests=true
-Dmaven.test.failure.ignore=true -Dcontext=Chrome -Dinjected.tags=configuration:Chrome:64 -e

[split-group1-testcase0-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=61 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:61 -e

[split-group1-testcase0-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=61 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:61 -e

[split-group1-testcase0-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=61 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:61 -e

[split-group1-testcase1-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=43 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:43 -e

[split-group1-testcase1-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=43 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:43 -e

[split-group1-testcase1-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=43 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:43 -e

[split-group1-testcase2-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=59 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=1
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:59 -e

[split-group1-testcase2-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=59 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=2
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:59 -e

[split-group1-testcase2-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='Windows'
-Dbrowserstack.web.os.version='10' ... -Dplatform=firefox -Ddefault.web.execution.platform=BROWSERSTACK_FIREFOX
-Dbrowserstack.firefox.version=59 ... -Dmeta.filter=+smoke -Dbatch.count=3 -Dbatch.number=3
-DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true -Dcontext=Firefox
-Dinjected.tags=configuration:Firefox:59 -e

[split-group2-testcase0-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='OSX'
-Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari
-Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.1 ... -Dmeta.filter=+smoke -Dbatch.count=3
-Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true
-Dcontext=Safari -Dinjected.tags=configuration:Safari:11.1 -e

[split-group2-testcase0-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='OSX'
-Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari
-Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.1 ... -Dmeta.filter=+smoke -Dbatch.count=3
-Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true
-Dcontext=Safari -Dinjected.tags=configuration:Safari:11.1 -e  

[split-group2-testcase0-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='OSX'
-Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari
-Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.1 ... -Dmeta.filter=+smoke -Dbatch.count=3
-Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true
-Dcontext=Safari -Dinjected.tags=configuration:Safari:11.1 -e  

[split-group2-testcase1-run1] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='OSX'
-Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari
-Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.0 ... -Dmeta.filter=+smoke -Dbatch.count=3
-Dbatch.number=1 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true
-Dcontext=Safari -Dinjected.tags=configuration:Safari:11.0 -e  

[split-group2-testcase1-run2] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='OSX'
-Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari
-Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.0 ... -Dmeta.filter=+smoke -Dbatch.count=3
-Dbatch.number=2 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true
-Dcontext=Safari -Dinjected.tags=configuration:Safari:11.0 -e  

[split-group2-testcase1-run3] [INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn integration-test
-Denv=STAGE ... -Dbrowserstack.web.os='OSX'
-Dbrowserstack.web.os.version='High Sierra' ... -Dplatform=safari
-Ddefault.web.execution.platform=BROWSERSTACK_SAFARI  -Dbrowserstack.safari.version=11.0 ... -Dmeta.filter=+smoke -Dbatch.count=3
-Dbatch.number=3 -DserenityReport=true -DskipTests=true -Dmaven.test.failure.ignore=true
-Dcontext=Safari -Dinjected.tags=configuration:Safari:11.0 -e  

[INFO] runATH : /sys_apps_01/maven/apache-maven-3.3.9/bin/mvn test serenity:aggregate -Dserenity.outputDirectory=target/site/serenity -Denv=STAGE -DskipTests=true

```
