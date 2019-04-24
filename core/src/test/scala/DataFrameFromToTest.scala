
import config.AppConfig
import core.DataFrameFromTo
import org.apache.spark.sql.SparkSession
import org.scalatest.{BeforeAndAfter, FunSuite}

class DataFrameFromToTest extends FunSuite with BeforeAndAfter {

  var DataFrameFromToInstance: DataFrameFromTo = _

  before {
    var appConfig : AppConfig = null;
    DataFrameFromToInstance = new DataFrameFromTo(appConfig, "DataPullTests")
  }


  test("Check Null Input") {
    val thrown = intercept[Exception] {

      val awsEnv = null
      val clustername = null
      val database = null
      val measurementname = null
      val login = null
      val password = null
      val vaultEnv = null
      val secretStore = null
      val sparkSession: org.apache.spark.sql.SparkSession = null

      DataFrameFromToInstance.InfluxdbToDataframe(awsEnv, clustername, database, measurementname, login, password, vaultEnv, secretStore, sparkSession)
    }
    assert(thrown.getMessage === "Platform cannot have null values")
  }

  test("Check Empty Input") {

    val thrown = intercept[Exception] {
      val sparkSession: SparkSession = null
      val awsEnv = ""
      val clustername = ""
      val database = ""
      val measurementname = ""
      val login = ""
      val password = ""
      val vaultEnv = ""
      val secretStore = ""


      DataFrameFromToInstance.InfluxdbToDataframe(awsEnv, clustername, database, measurementname, login, password, vaultEnv, secretStore, sparkSession)

    }
    assert(thrown.getMessage === "Platform cannot have empty values")
  }


}