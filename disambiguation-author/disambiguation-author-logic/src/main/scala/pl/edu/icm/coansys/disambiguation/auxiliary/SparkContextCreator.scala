/*
 * Copyright (c) 2016-2016 ICM UW
 */

package pl.edu.icm.coansys.disambiguation.auxiliary

import org.apache.spark.SparkContext
import org.rogach.scallop.ScallopConf

object SparkContextCreator {

  /**
    * Constructs `SparkContext` from command-line arguments.
    *
    * @param args          Command-line arguments parsed with Scallop.
    * @param continuations Dependency injection, used only in testing.
    * @return `SparkContext`
    */
  def fromArgs(args: Args, continuations: LocalClusterContinuations = new LocalClusterContinuations()): SparkContext = {

    if (args.isLocal()) {
      continuations.createLocalSparkContext()
    } else {
      continuations.createClusterSparkContext()
    }
  }


  //------------------------ LOGIC --------------------------

  class Args(args: Seq[String]) extends ScallopConf(args) with Serializable {
    val isLocal = opt[Boolean](descr = "Flag distinguishing 'IDE (local) execution' from 'JAR (cluster) execution'")
  }

}