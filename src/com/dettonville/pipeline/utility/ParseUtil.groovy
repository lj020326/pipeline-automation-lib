/**
 * com.dettonville.pipeline.utility is a collection of utilities to perform common pipeline tasks.
 */
package com.dettonville.pipeline.utility

/**
 * Common Utilities.
 *
 * @Author grant.gortsema@dettonville.com
 */
class ParseUtil implements Serializable {

  /**
  * a reference to the pipeline that allows you to run pipeline steps in your shared libary
  */
  def steps

  /**
  * Constructor
  *
  * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
  */
  public ParseUtil(steps) {this.steps = steps}



  /**
  * parses yaml text
  *
  * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
  * @param textToParse text we want turned into a traversable object
  */
  @NonCPS
  public Object parseYaml(script, String textToParse) {
    script.readYaml text:textToParse
  }
}
