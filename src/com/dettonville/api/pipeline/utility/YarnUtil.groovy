package com.dettonville.api.pipeline.utility

/**
 * Utility to install dependencies with Yarn
 * Re-Writes the lock file url's to work with artifacory
 * @Author brent.bevolo@dettonville.com
 */
class YarnUtil implements Serializable {
  def steps

  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public YarnUtil(steps) { this.steps = steps }

  /**
  * method to install npm dependencies with yarn
  *
  * @param nodeSassUrl optional reference to the nodeSass Location in artifactory
  * @param skipCypress optional defaults to 1 which means skip
  * TODO: add url for cypress when available or refactor this stuff out entirely
  * Reference Docs: https://docs.cypress.io/guides/getting-started/installing-cypress.html#Advanced
  */
  public void install(
    script,
    String nodeSassUrl = 'https://gitrepository.dettonville.int/artifactory/archive-external-release/node-sass/',
    String skipCypress = '1' // TODO: Make boolean so its less confusing
    ) {
    // FIXME: Its probably better to make this entire module only re-write the lock.file
    // -> Then we can just pass in an arbirtary string and use it as a command prefix
    // -> Then you can modify the install command however you'd like for your app
    // -> However, for now these are pretty alberta specific

    def sassCommandPrefix = 'SASS_BINARY_SITE='
    def skipCypressPrefix = 'CYPRESS_SKIP_BINARY_INSTALL='

    def sassCommand = "${sassCommandPrefix}" + nodeSassUrl
    def skipCypessCommand = "${skipCypressPrefix}" + skipCypress

    def message = """
    Setting following build CL args:

    ${sassCommand}
    ${skipCypessCommand}
    """

    steps.echo "${message}"

    if (script.fileExists('yarn.lock')) {

      steps.echo '*** replacing registry url in yarn.lock for CI ***'
      steps.sh "sed -i 's,https://registry.yarnpkg.com,https://gitrepository.dettonville.int/artifactory/api/npm/npm-all,g' yarn.lock"
      // Execute Yarn install command
      steps.sh "${sassCommand} ${skipCypessCommand} yarn"

    } else {
      steps.echo " *** ERROR: NO YARN.LOCK FILE FOUND ***"
      steps.sh "exit 1"
    }
  }
}
