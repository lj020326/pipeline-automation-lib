#!/usr/bin/env groovy

@Library("pipelineAutomationLib@develop")_

// once every 4 hours every day
//cron_cfg="H(0-15) */4 * * *"
//cron_cfg="H(0-15) 4 * * *"
//
//properties([pipelineTriggers([cron("${cron_cfg}")])])


def ymlConfig='''
---
## for info on how to configure pipeline - see here:
## ref: https://gitrepository.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
pipeline:
  gitRepoUrl: "https://gitrepository.dettonville.int/stash/scm/api/testsslserver.git"

  logLevel: "INFO"
#  logLevel: "DEBUG"
#  useSimulationMode: true

#  alwaysEmailDist: "SIT-engineer@dettonville.com, ljohnson@dettonville.com"
  alwaysEmailDist: "ljohnson@dettonville.com"

  timeout: 4
  runcount: 10

  testList:
    - command: nslookup
#    - command: dig
#    - command: mtr
#    - command: traceroute
#    - command: bash
#    - command: ping
    - command: telnet
#    - command: openssl
#    - command: sslpoke
    - command: curl
    - command: curl-verifycerts
    - command: httpclienttest
    - command: sslhandshake

  nodeList:
    - nodeLabel: jnk2stl0
      network: nyc-prod-admin
      testNetworks:
        - prod-copro
        - prod-stl
        - prod-ksc
        - prod-external
        - prod-admin
        - external
    - nodeLabel: jnk2stl1
      network: nyc-prod-admin
      testNetworks:
        - prod-copro
        - prod-stl
        - prod-ksc
        - prod-external
        - prod-admin
        - external
    - nodeLabel: jnk4stl1
      network: stage-admin
      testNetworks:
        - stage
        - stage-external
        - stage-admin
#        - devcloud
        - dev
        - dev-admin
        - external
    - nodeLabel: jnk4stl2
      network: stage-admin
      testNetworks:
        - stage
        - stage-external
        - stage-admin
#        - devcloud
        - dev
        - dev-admin
        - external
    - nodeLabel: jnk4stl3
      network: stage-admin
      testNetworks:
        - stage
        - stage-external
        - stage-admin
#        - devcloud
        - dev
        - dev-admin
        - external

  networks:
    prod-external:
      httpProxyHost: outboundproxy.dettonville.int
      httpProxyPort: 15768
      siteList:
        - endpoint: developer.dettonville.org
        - endpoint: api.dettonville.org

    prod-copro:
      network: prod
      siteList:
        - endpoint: internal.cicd.developer.dettonville.int
        - endpoint: api.dettonville.org
        - endpoint: sandbox.api.dettonville.org

    prod-stl:
      network: prod
      siteList:
        - endpoint: nyc.internal.cicd.developer.dettonville.int

    prod-ksc:
      network: prod
      siteList:
        - endpoint: jpn.internal.cicd.developer.dettonville.int

    prod-admin:
      siteList:
        - endpoint: dcapiadmin.dettonville.org/dcapiadmin/DCAPIAdmin.html
        - endpoint: iapp.dettonville.int/devzone/cms/
        - endpoint: sso.iapp.dettonville.int/dcapi/prod/dashboard

    stage-external:
      httpProxyHost: outboundproxy.dettonville.int
      httpProxyPort: 15768
      siteList:
        - endpoint: https://stage.developer.dettonville.org
        - endpoint: stage.api.dettonville.org

    stage:
      siteList:
        - endpoint: internal.cicd.stage.developer.dettonville.int
        - endpoint: stage.api.dettonville.org

    stage-admin:
      siteList:
        - endpoint: stage.dcapiadmin.dettonville.org/dcapiadmin/DCAPIAdmin.html
        - endpoint: stage2.dcapiadmin.dettonville.org/dcapiadmin/DCAPIAdmin.html
        - endpoint: stage.iapp.dettonville.int/devzone/cms/
        - endpoint: stage.sso.iapp.dettonville.int/dcapi/stage/dashboard

    dev:
      siteList:
        - endpoint: dev.developer.dettonville.org
        - endpoint: dev.api.dettonville.org

    dev-admin:
      siteList:
        - endpoint: dev.eportal.dettonville.org/infra-dev-devportal/cms/
        - endpoint: dev.dcapiadmin.dettonville.org/dcapiadmin/DCAPIAdmin.html
        - endpoint: dev.sso.eportal.dettonville.org
        - endpoint: stage.sso.iapp.dettonville.int/dcapi/stage/dashboard

    devcloud:
      siteList:
        - endpoint: 10.157.136.151:52101

    external:
      httpProxyHost: outboundproxy.dettonville.int
      httpProxyPort: 15768
      siteList:
        - endpoint: http://hub-cloud.browserstack.com/wd/hub
        - endpoint: rally1.rallydev.com
        - endpoint: api.mailinator.com

'''

Map config = [:]

config.yml=ymlConfig

node ("QA-LINUX") {

    runConnectivityTest(config)

}
