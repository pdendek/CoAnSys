
package pl.edu.icm.coansys.disambiguation.author.scala

import org.apache.hadoop.io.{BytesWritable, Text}
import org.apache.pig.data.{DataBag, Tuple, TupleFactory}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import pl.edu.icm.coansys.disambiguation.author.pig.extractor.EXTRACT_CONTRIBDATA_GIVENDATA

import scala.collection.JavaConversions._

//import pl.edu.icm.coansys.disambiguation.author.pig.GenUUID
import pl.edu.icm.coansys.disambiguation.auxiliary.SparkContextCreator

object Splitter {

  /**
    * @param rawArgs the command line arguments
    */
  def main(rawArgs: Array[String]): Unit = {

    val args: JobArgs = parseArgs(rawArgs)
    val sc: SparkContext = SparkContextCreator.fromArgs(args)

    val inputData: String = args.inputData()
    val outputData: String = args.outputData()

    val featureInfo: String = args.featureInfo()
    val threshold: Double = args.threshold()
    val exhaustiveLimit: Int = args.exhaustiveLimit()
    val approxLimit: Int = args.approxLimit()
    val lang: String = args.lang()
    val approximateRememberSim: Boolean = args.approximateRememberSim()
    val useExtractorIdInsteadOfName: Boolean = args.useExtractorIdInsteadOfName()
    val calcAddiitonalStatistics: Boolean = args.calcAddiitonalStatistics()
    val skipEmptyFeatures: Boolean = args.skipEmptyFeatures()

    workflow(sc, inputData, outputData, featureInfo, threshold, exhaustiveLimit, approxLimit, lang, approximateRememberSim, useExtractorIdInsteadOfName, calcAddiitonalStatistics, skipEmptyFeatures)

  }

  def workflow(sc: SparkContext, inputData: String, outputData: String,
               featureInfo: String, threshold: Double, exhaustiveLimit: Int, approxLimit: Int,
               lang: String, approximateRememberSim: Boolean, useExtractorIdInsteadOfName: Boolean,
               calcAddiitonalStatistics: Boolean, skipEmptyFeatures: Boolean): Unit = {

    val rawDocs: RDD[(Text, BytesWritable)] = sc.sequenceFile[Text, BytesWritable](inputData);

    val contribInfoRdd: RDD[ContribInfoTuple] = rawDocs
      .flatMap[Tuple] {
      case (t: Text, bw: BytesWritable) =>
        val edgdParams = List("-featureinfo", featureInfo,
          "-lang", lang, "-skipEmptyFeatures", skipEmptyFeatures,
          "-useIdsForExtractors", useExtractorIdInsteadOfName).mkString(" ")
        val results: DataBag = new EXTRACT_CONTRIBDATA_GIVENDATA(edgdParams).exec(TupleFactory.getInstance().newTuple(List(t, bw)))
        results.iterator()
    }
      .map(extractFirstTuple(_))
      .filter(null != _.docKey)

    // save auxiliary information
    contribInfoRdd.map {
      t => (t.docKey, t.contribId, t.surnameStr, t.surnameInt)
    }.saveAsTextFile(outputData + "/docKey_cId_surStr_surInt")

    // divide contribInfoRdd into Correct/Uncorrect classes
    val corUnCorContribInfoRdd = contribInfoRdd.map {
      t =>
        (t.surnameInt != null, t)
    }
    val corCotrinbInfoRdd = corUnCorContribInfoRdd.filter(_._1)
    val unCorCotrinbInfoRdd = corUnCorContribInfoRdd.filter(!_._1)


    //TODO: PROCESSING CONTRIBUTORS WITHOUT SNAME
    //D1A = foreach NOSNAME generate null as sname, {(cId,null,metadata)} as datagroup, 1 as count;

    //TODO: PROCESSING CONTRIBUTORS DISIMILAR TO THEMSELVES
    //

    //TODO: PROCESSING CONTRIBUTORS SIMILAR TO THEMSELVES
    val groupedWithSizes =
      corCotrinbInfoRdd
        .map(_._2)
        .groupBy(_.surnameInt)
        .map { case (sname: Int, tIterable: Iterable[ContribInfoTuple]) =>
          val tList = tIterable.toList
          (sname, tList.length, tList)
        }
    val contribsOne = groupedWithSizes.filter(_._2 == 1)
    val contribsExh = groupedWithSizes.filter {
      case (snameInt: Int, size: Int, contribList: List[ContribInfoTuple]) =>
        (size > 1) && (size <= exhaustiveLimit)
    }
    val contribsApprox = groupedWithSizes.filter {
      case (snameInt: Int, size: Int, contribList: List[ContribInfoTuple]) =>
        (size > exhaustiveLimit) && (size <= approxLimit)
    }
    val contribsAbandon = groupedWithSizes.filter(_._2 > approxLimit)

    contribsOne.saveAsTextFile(outputData + "/splitted/one/")
    contribsExh.saveAsTextFile(outputData + "/splitted/exh/")
    contribsApprox.saveAsTextFile(outputData + "/splitted/approx")
    contribsAbandon.saveAsTextFile(outputData + "/docKey_cId_surStr_surInt")

  }

  def extractFirstTuple(tuple: Tuple): ContribInfoTuple = {
    ContribInfoTuple(
      tuple.get(0).asInstanceOf[String],
      tuple.get(1).asInstanceOf[String],
      tuple.get(2).asInstanceOf[Int],
      tuple.get(3).asInstanceOf[Map[Object, Array[Object]]],
      tuple.get(4).asInstanceOf[String])
  }

  //------------------------ PRIVATE --------------------------
  def parseArgs(rawArgs: Array[String]): JobArgs = {

    val args = new JobArgs(rawArgs)
    args.printHelp
    println(args.summary)
    args
  }

  case class ContribInfoTuple(docKey: String,
                              contribId: String,
                              surnameInt: Int,
                              metadata: Map[Object, Array[Object]],
                              surnameStr: String)

  class JobArgs(args: Seq[String]) extends SparkContextCreator.Args(args) {


    val inputData = opt[String](required = false, descr = "The path to the input data", default = Some("workflows/pl.edu.icm.coansys-disambiguation-author-workflow/results/splitted/approx"))
    val outputData = opt[String](required = false, descr = "The path to the final successfully processed data", default = Some("splitter."))

    val featureInfo = opt[String](required = false, descr = "", default = Some("IntersectionPerMaxval#EX_DOC_AUTHS_SNAMES#1.0#1"))
    val threshold = opt[Double](required = false, descr = "", default = Some(-0.8))

    val exhaustiveLimit = opt[Int](required = false, descr = "", default = Some(6627))
    val approxLimit = opt[Int](required = false, descr = "", default = Some(100000))

    val lang = opt[String](required = false, descr = "", default = Some("all"))
    val approximateRememberSim = opt[Boolean](required = false, descr = "", default = Some(false))
    val useExtractorIdInsteadOfName = opt[Boolean](required = false, descr = "", default = Some(false))
    val calcAddiitonalStatistics = opt[Boolean](required = false, descr = "", default = Some(false))
    val skipEmptyFeatures = opt[Boolean](required = false, descr = "", default = Some(true))

    verify()
  }

}
