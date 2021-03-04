package dq

import com.amazon.deequ.profiles.ColumnProfilerRunner
import com.amazon.deequ.repository.{MetricsRepository, ResultKey}
import com.amazon.deequ.repository.fs.FileSystemMetricsRepository
import com.amazon.deequ.repository.memory.InMemoryMetricsRepository
import config.AppConfig
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.codehaus.jettison.json.JSONObject
import core.DataPull.jsonObjectPropertiesToMap
import com.amazon.deequ.analyzers.runners.AnalyzerContext.successMetricsAsDataFrame
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.functions.expr


class Metrics(config: AppConfig, spark: SparkSession, dqJson: JSONObject) {

  val metricsFilePath: Option[String] = (if (dqJson.has("metricsfilepath")) Some(dqJson.getString("metricsfilepath")) else None)
  val metricsKeyTime: Long = (if (dqJson.has("metricskeytime")) dqJson.getLong("metricskeytime") else System.currentTimeMillis())
  val metricsKeyTags: Map[String, String] = mapToEvaluatedMap(if (dqJson.has("metricskeytags")) jsonObjectPropertiesToMap(dqJson.getJSONObject("metricskeytags")) else Map.empty[String, String])
  val metricsHistogramBinsMax: Int = (if (dqJson.has("metricshistogrambinsmax")) dqJson.getInt("metricshistogrambinsmax") else 1000)

  def dataframeToMetricsJsonFile(df: DataFrame): Unit = {
    //create the measures/metrics/profile storage (will be re-used if already exists)
    val metricsStorage: MetricsRepository =
      FileSystemMetricsRepository(spark, metricsFilePath.getOrElse("/mnt/metrics.json"))
    val resultKey = ResultKey(metricsKeyTime, metricsKeyTags)
    val result = ColumnProfilerRunner()
      .onData(df)
      .withLowCardinalityHistogramThreshold(metricsHistogramBinsMax)
      .useRepository(metricsStorage)
      .saveOrAppendResult(resultKey)
      .run()
  }

  def dataframeToMetricsDataFrame(df: DataFrame): DataFrame = {
    //create the measures/metrics/profile storage (will be re-used if already exists)
    val metricsStorage: MetricsRepository = {
      (if (metricsFilePath.isEmpty) (new InMemoryMetricsRepository()) else FileSystemMetricsRepository(spark, metricsFilePath.get))
    }
    val resultKey = ResultKey(metricsKeyTime, metricsKeyTags)
    val result = ColumnProfilerRunner()
      .onData(df)
      .withLowCardinalityHistogramThreshold(metricsHistogramBinsMax)
      .useRepository(metricsStorage)
      .saveOrAppendResult(resultKey)
      .run()

    var analyserContext = metricsStorage
      .loadByKey(resultKey)
      .get

    val DATASET_DATE_FIELD = "dataset_date"

    var analyzerContextDF = {
      //TODO: need to override successMetricsAsDataFrame since it doesn't distinguish betwen the bins of the metric and datatype histograme. This will also allow us to avoid the need for the rlike computed column hack to differentiate the histograms
      successMetricsAsDataFrame(sparkSession = spark, analyzerContext = analyserContext)
        .withColumn("metric", expr("case when name rlike '^Histogram\\.((abs)|(ratio))\\.((Boolean)|(Fractional)|(Integral)|(String)|(Unknown))$' then 'HistogramDataType' else split(name, '[\\.]')[1] end"))
        .withColumn("metric_type", expr("split(name, '[\\.]')[2]"))
        .withColumn("metric_key", expr("replace(name, substring_index(name, '.', 2) + '.', '')"))
        .withColumn(DATASET_DATE_FIELD, lit(resultKey.dataSetDate))
    }

    resultKey.tags
      .map { case (tagName, tagValue) =>
        formatTagColumnNameInDataFrame(tagName, analyzerContextDF) -> tagValue
      }
      .foreach {
        case (key, value) => analyzerContextDF = analyzerContextDF.withColumn(key, lit(value))
      }

    analyzerContextDF
  }

  private[this] def formatTagColumnNameInDataFrame(
                                                    tagName: String,
                                                    dataFrame: DataFrame)
  : String = {

    var tagColumnName = tagName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase
    if (dataFrame.columns.contains(tagColumnName)) {
      tagColumnName = tagColumnName + "_2"
    }
    tagColumnName
  }

  private[this]  def mapToEvaluatedMap(initMap: Map[String, String]): Map[String, String] = {
    var returnMap = Map.empty[String, String]
    initMap.foreach(p => returnMap = returnMap ++ Map(p._1 -> spark.sql("SELECT " + p._2).head().getString(0)))
    returnMap
  }
}
