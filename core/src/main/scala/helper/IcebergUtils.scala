/* Copyright (c) 2019 Expedia Group.
 * All rights reserved.  http://www.homeaway.com

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *      http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package helper

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object IcebergUtils {

  def configureIcebergCatalog(sparkSession: SparkSession): Unit = {
    sparkSession.conf.set("spark.sql.catalog.iceberg.type", "hive")
    sparkSession.conf.set("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
    sparkSession.conf.set("spark.sql.catalog.iceberg", "org.apache.iceberg.spark.SparkCatalog")
  }

  /**
   * Loads the target Iceberg table as a DataFrame for inspection (schema, row count, partitioning).
   * Returns a tuple of (target DataFrame, row count).
   */
  def loadTargetTable(sparkSession: SparkSession, database: String, table: String): (DataFrame, Long) = {
    configureIcebergCatalog(sparkSession)
    sparkSession.sql(s"use $database")
    val targetDf = sparkSession.sql(s"SELECT * FROM $table")
    val rowCount = targetDf.count()
    (targetDf, rowCount)
  }

  /**
   * Deduplicates an Iceberg table based on the specified key columns.
   * Keeps the latest row per key using the orderByColumn (descending) to determine recency.
   * If orderByColumn is not provided, an arbitrary row per key group is kept.
   */
  def icebergTableDeduplication(sparkSession: SparkSession, database: String, table: String,
                                keyColumns: Seq[String], orderByColumn: Option[String] = None): Unit = {
    configureIcebergCatalog(sparkSession)
    sparkSession.sql(s"use $database")

    val keyColsStr = keyColumns.mkString(", ")
    val orderClause = orderByColumn match {
      case Some(col) => s"ORDER BY $col DESC"
      case None => ""
    }

    val dedupViewName = s"__iceberg_dedup_${table.replaceAll("[^a-zA-Z0-9]", "_")}"

    val dedupQuery =
      s"""SELECT * FROM (
         |  SELECT *, ROW_NUMBER() OVER (PARTITION BY $keyColsStr $orderClause) as __dedup_rn
         |  FROM $table
         |) WHERE __dedup_rn = 1""".stripMargin

    val dedupDf = sparkSession.sql(dedupQuery).drop("__dedup_rn")
    dedupDf.createOrReplaceTempView(dedupViewName)

    // Overwrite the table with deduplicated data
    dedupDf.write
      .format("iceberg")
      .mode("overwrite")
      .insertInto(table)
  }

  /**
   * Processes dynamic partition overwrite for Iceberg tables.
   * Only overwrites partitions that exist in the source DataFrame, leaving other partitions untouched.
   * partitionColumns specifies which columns define the partitioning scheme.
   */
  def processTablePartitionsDynamic(sparkSession: SparkSession, df: DataFrame,
                                    database: String, table: String,
                                    partitionColumns: Seq[String]): Unit = {
    configureIcebergCatalog(sparkSession)
    sparkSession.sql(s"use $database")

    sparkSession.conf.set("spark.sql.sources.partitionOverwriteMode", "dynamic")

    df.write
      .format("iceberg")
      .option("overwrite-mode", "dynamic")
      .mode("overwrite")
      .insertInto(table)
  }
}
