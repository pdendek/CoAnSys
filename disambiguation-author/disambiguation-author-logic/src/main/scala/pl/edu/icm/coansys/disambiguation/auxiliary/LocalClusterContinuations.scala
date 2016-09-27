package pl.edu.icm.coansys.disambiguation.auxiliary

import org.apache.spark.{SparkConf, SparkContext}

class LocalClusterContinuations {

  def createClusterSparkContext(): SparkContext = {

    val conf = new SparkConf
    new SparkContext(conf)
  }

  def createLocalSparkContext(): SparkContext = {

    val conf = new SparkConf().setMaster("local[2]").setAppName("test1")
    new SparkContext(conf)
  }
}
