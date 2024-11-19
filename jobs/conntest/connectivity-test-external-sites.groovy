#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")_

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
  runcount: 20

  testList:
    - command: nslookup
#    - command: dig
#    - command: mtr
#    - command: traceroute
#    - command: bash
#    - command: ping
#    - command: telnet
#    - command: openssl
#    - command: sslpoke
    - command: curl
#    - command: curl-verifycerts
#    - command: httpclienttest
#    - command: sslhandshake

  nodeList:
    - nodeLabel: jnk2stl0
      network: nyc-prod-admin
      testNetworks:
        - external
    - nodeLabel: jnk2stl1
      network: nyc-prod-admin
      testNetworks:
        - external
    - nodeLabel: jnk4stl1
      network: stage-admin
      testNetworks:
        - external
    - nodeLabel: jnk4stl2
      network: stage-admin
      testNetworks:
        - external
    - nodeLabel: jnk4stl3
      network: stage-admin
      testNetworks:
        - external

  networks:

    external:
      httpProxyHost: outboundproxy.dettonville.int
      httpProxyPort: 15768
      siteList:
        - endpoint: http://hub-cloud.browserstack.com/wd/hub
        - endpoint: https://hub-cloud.browserstack.com/wd/hub
        - endpoint: rally1.rallydev.com
        - endpoint: api.mailinator.com

'''

Map config = [:]

config.yml=ymlConfig

node ("QA-LINUX") {

    runConnectivityTest(config)

}
