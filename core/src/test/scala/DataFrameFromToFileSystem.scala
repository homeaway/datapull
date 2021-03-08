
import config.AppConfig
import core.DataFrameFromTo
import org.scalatest.{BeforeAndAfter, FunSuite}

class DataFrameFromToFileSystem extends FunSuite with BeforeAndAfter {

  var DataFrameFromToInstance: DataFrameFromTo = _

  before {
    var appConfig : AppConfig = null;
    DataFrameFromToInstance = new DataFrameFromTo(appConfig, "DataPullTests")
  }


  test("Check Null Input") {
    val thrown = intercept[Exception] {


      val filePath = null
      val fileFormat = null
      val delimiter = null
      val charset = null
      val mergeSchema = null
      val sparkSession: org.apache.spark.sql.SparkSession = null
      val login = null
      val host = null
      val password= null
      val awsEnv = null
      val vaultEnv = null


      DataFrameFromToInstance.fileToDataFrame(filePath,fileFormat,delimiter,charset,mergeSchema,sparkSession,false,"",false,login,host,password,"", awsEnv,vaultEnv)
    }
    assert(thrown.getMessage === "Platform cannot have null values")
  }


}

