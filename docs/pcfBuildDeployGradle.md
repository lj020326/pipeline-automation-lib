
This a Jenkins library built to make it easier for us at Wolox to configure pipelines without necessarily knowing about Jenkinsfile syntax.
All our projects are built using a Dockerfile

When using this library, your Jenkinsfile should look something like this:

```groovy
@Library('dcapi-automation-library') _

pcfBuildDeployGradle('pcf/config.yml');
```

It basically loads the library, clones the target repository and calls `pcfBuildDeployGradle` to make its magic.
As an argument, `pcfBuildDeployGradle` receives the path to a configuration yaml file.
This file looks something like this:

```yaml
pipeline:
  emailList: "ljohnson@dettonville.com"
  alwaysEmailList: "ljohnson@dettonville.com"
  pcfAppName: swagger-validator-service
  pcfAppRoute: dcapi-swagger-validator
  pcfAppRouteBlue: dcapi-swagger-validator
  pcfAppRouteGreen: dcapi-swagger-validator-green
  pcfMinInst: 1
  pcfOrg: DCAPI
  pcfSpace: DCAPI_Services
  pcfAppEnvVars:
    - name: foo1
      value: bar1
    - name: foo2
      value: bar2
    - jenkinsCredId: dcapi-jenkins-stage
      usernameVar: jenkins.username
      passwordVar: jenkins.password
```
