package com.dettonville.api.pipeline.utility

/**
 * Utility to set the dot-env config file on the Jenkins workspace
 * This is necessary for all frontend builds
 *
 * @Author brent.bevolo@dettonville.com
 */
class DotEnvUtil implements Serializable {
  def steps

  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public DotEnvUtil(steps) { this.steps = steps }

  /**
  * method to create the .env file on the workspace which is utilized in building frontend apps
  *
  * @param cerseiHost required full host url of the srci or cersei application
  * @param varysHost required full host url of the src or varys application
  */
  public void create(String cerseiHost, String varysHost) {
    steps.echo "Creating the .env file for the monorepo"

    def envContent = """
    SRCI_HOST=${cerseiHost}
    DETTONVILLE_SRC_HOST=${varysHost}
    """

    steps.writeFile file: ".env", text: envContent
    steps.echo ".env file created, see values:"
    steps.sh "head .env"
  }
}
