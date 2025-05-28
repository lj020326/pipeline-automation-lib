package com.dettonville.api.pipeline.utils

import com.dettonville.api.pipeline.utils.logging.Logger

class AnsibleTestUtil implements Serializable {
    private static final long serialVersionUID = 1L

    Logger log = new Logger(this)
    def dsl

    String testIntegrationDir = "tests/integration"
    String integrationTestConfigVault = "${testIntegrationDir}/integration_config.vault.yml"
    String integrationTestConfig = "${testIntegrationDir}/integration_config.yml"

    /**
     * The ansible-test test command (env, sanity, coverage, units, integration, network-integration)
     */
    String command = null

    /**
     * The ansible-test target
     */
    String target = null

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    AnsibleTestUtil(dsl) {
        this.dsl = dsl
    }

    def withTestConfigVault(String ansibleVaultCredId="ansible-vault-password", def actions) {

        dsl.withCredentials([string(credentialsId: ansibleVaultCredId, variable: 'VAULT_PASSWORD')]) {

            String ansibleDecryptCmd = """
                ansible-vault decrypt ${this.integrationTestConfigVault} --output ${integrationTestConfig} --vault-password ${dsl.env.VAULT_PASSWORD}
            """
            dsl.echo "decrypting ${this.integrationTestConfigVault}"
            dsl.sh "${ansibleDecryptCmd}"

            actions()

            dsl.echo "removing ${this.integrationTestConfig}"
            dsl.sh "rm ${this.integrationTestConfig}"

        }
    }

    def runAnsibleTest(
            String command="integration",
            String color = null,
            Boolean debug = false,
            String verbosity=null,
            String pythonVersion="3.6",
            String target = null
    ) {

        String ansibleTestCmd = "ansible-test"
        ansibleTestCmd += " ${command}"
        if (color) {
            ansibleTestCmd += " --color ${color}"
        }
        if (debug) {
            ansibleTestCmd += " --debug"
        }
        if (verbosity) {
            ansibleTestCmd += " ${verbosity}"
        }
        ansibleTestCmd += " --python ${pythonVersion}"

        if (target) {
            ansibleTestCmd += " ${target}"
        }

        dsl.sh "${ansibleTestCmd}"

    }
