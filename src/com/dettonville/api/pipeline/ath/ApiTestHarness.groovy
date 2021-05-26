package com.dettonville.api.pipeline.ath

/*-
 *
 * For info on how to configure pipeline - see here:
 * https://fusion.dettonville.int/confluence/display/MAPI/How+to+use+the+Acceptance+Test+Harness+Pipeline
 *
 * OR here:
 * ref: https://fusion.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
 * ref: https://gitrepository.dettonville.int/stash/projects/API/repos/pipeline-automation-lib/browse/vars/runATH.md
 *
*/

import com.dettonville.api.pipeline.utils.logging.LogLevel

import com.dettonville.api.pipeline.utils.JsonUtils
import com.dettonville.api.pipeline.utils.Utilities

import java.time.*

class ApiTestHarness extends AcceptanceTestHarness {
    private static final long serialVersionUID = 1L

    /**
     * @param dsl The DSL object of the current pipeline script (available via this.steps in pipeline scripts)
     */
    ApiTestHarness(def dsl) {
        super(dsl)
    }

    Map getDefaultSettings() {
        // get default job config settings
        return ApiPipelineDefaults.getDefaultSettings(dsl)
    }

    /**
     * Build the Maven Integration Test command line that will be used by automation execution
     **/
    String prepareMvnIntegrationTestCommand(Map config) {
        String logPrefix="prepareMvnIntegrationTestCommand():"
        log.debug("${logPrefix} started")

        String mvnCmd = "${dsl.env.M3}/bin/mvn"
        mvnCmd += " clean install"
        // https://stackoverflow.com/questions/21638697/disable-maven-download-progress-indication
        mvnCmd += " ${config.mvnLogOptions}"
        mvnCmd += " -Denv=${config.appEnvironment}"

        if (config.debugPipeline) mvnCmd += " -X"
        if (config.useDryRun) mvnCmd += " -DdryRun"

        mvnCmd += " -Ddefault.web.execution.platform=${config.webPlatform}"

        if (config?.storyName) mvnCmd += " -DstoryName=${config.storyName}"
        if (config?.metaFilterTags) mvnCmd += " -Dmeta.filter=${config.metaFilterTags}"
        if (config?.jbehaveExecutionThreads) mvnCmd += " -Djbehave.execution.threads=${config.jbehaveExecutionThreads}"

        // enable serenity reporting
        // ref: https://fusion.dettonville.int/stash/projects/QE/repos/mtaf-jbehave-tools/browse/src/main/java/com/dettonville/testing/mtaf/jbehave/serenity/SerenitySupport.java?at=refs%2Fheads%2Fmaster
        if (config.runSerenityRpt) mvnCmd += " -DserenityReport=true"
        // skip unit tests
        mvnCmd += " -DskipTests=${config.skipTests}"
        mvnCmd += " -Dmaven.test.failure.ignore=${config.failureIgnore}"

        if (config.useRallyIntegration) {
            mvnCmd += " '-Djbehave.reporters=RALLY,LOGGING'"
            mvnCmd += " -Denable.rally.proxy=${config.enableRallyProxy}"
            mvnCmd += " -DtestSetName=${config.testSetName}"
            mvnCmd += " -DtestSetId=${config.testSetId}"

            mvnCmd += " -Drally.url=${config.rallyUrl}"

            withCredentials([string(credentialsId: config.jenkinsRallyCredId, variable: 'RALLY_KEY')]) {
                mvnCmd += " -Drally.key=${RALLY_KEY}"
            }
            mvnCmd += " -DrallyBuild=${config.rallyBuild}"
            mvnCmd += " -DrallyIntegration=${config.useRallyIntegration}"
            mvnCmd += " -DrallyScreenShotMode=${config.rallyScreenShotMode}"
            mvnCmd += " -DprojectName='${config.rallyProjectName}'"
        }

//    log.debug("${logPrefix} 9: mvnCmd=${mvnCmd}")

        mvnCmd+=" -e"

        log.debug("${logPrefix} final command: mvnCmd=${mvnCmd}")

        return mvnCmd
    }


}
