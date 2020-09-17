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

import org.neo4j.driver.v1.{AuthTokens, GraphDatabase}
import org.apache.spark.sql.{Row}
import scala.collection.mutable.ListBuffer

class Neo4j {
  def writeNode(df: org.apache.spark.sql.DataFrame, cluster: String, login: String, password: String, node_label: String, node_keys: List[String], node_nonKeys: List[String], sparkSession: org.apache.spark.sql.SparkSession, batchSize: Int, createNodeKeyConstraint: Boolean, createOrMergeNode: String): Unit = {

    //if (1 == 0 ) {
      //get distinct values
      df.createOrReplaceTempView("dft_" + node_label)

      val commands = new ListBuffer[String]()
      val nQuery = StringBuilder.newBuilder
      val dfQuery = StringBuilder.newBuilder
      dfQuery.append("CONCAT('{xx:\"y\"")

      if (node_label != "" && (!node_keys.isEmpty)) {
        //create a node key for the node
        if (createNodeKeyConstraint){
          commands += "CREATE CONSTRAINT ON (n:" + node_label + ") ASSERT (n." + node_keys.mkString(",n.") + ") IS NODE KEY;"
        }
        val node_allKeys = node_keys ::: node_nonKeys
        node_allKeys.foreach(k => {
          dfQuery.append(",").append(node_label).append("_").append(k).append(":\"',").append("CAST(").append(k).append(" as string) ,'\"','")
        })
        //create the neo query
        if (createOrMergeNode.toUpperCase().equals("CREATE")){
          nQuery.append(" CREATE (m:")
        }
        else {
          nQuery.append(" MERGE (m:")
        }
        nQuery.append(node_label).append("{").append(node_allKeys.map(k => k + ":row." + node_label + "_" + k).mkString(",")).append("}) ")
        nQuery.append(";")
        dfQuery.append("}') as StringData FROM dft_" + node_label)
        val dfQueryString = " SELECT DISTINCT " + dfQuery.toString()

        val driver = GraphDatabase.driver("bolt://" + cluster + "/7687", AuthTokens.basic(login, password))
        val session = driver.session
        for (i <- 0 to commands.length - 1) {
          session.run(commands(i))
        }
        session.close()

        var partitions: Int = df.count().toInt
        if (partitions > batchSize * 10) {
          partitions = partitions / batchSize
        }
        val dftSData = sparkSession.sql(dfQueryString).repartition(partitions)
        dftSData.foreachPartition(
          (partition:Iterator[Row]) => {
            val partitionasList = partition.map(
              record => record(0).toString).toList
            if (!partitionasList.isEmpty) {
              val driver = GraphDatabase.driver("bolt://" + cluster + "/7687", AuthTokens.basic(login, password))
              val session = driver.session
              val query = "UNWIND {rows:[" + partitionasList.mkString(",") + "]}.rows as row" + nQuery.toString()
              session.run(query)
              session.close()
            }
          })
      }
   // }
  }

  def writeRelation(df: org.apache.spark.sql.DataFrame, cluster: String, login: String, password: String, node1_label: String, node1_keys: List[String], node2_label: String, node2_keys: List[String], relation_label: String, sparkSession: org.apache.spark.sql.SparkSession, batchSize: Int, createOrMergeRelation: String): Unit = {
    val commands = new ListBuffer[String]()
    df.createOrReplaceTempView("dft_" + relation_label)
    val dfQuery = StringBuilder.newBuilder
    dfQuery.append("CONCAT('{xx:\"y\"")
    val nQuery = StringBuilder.newBuilder

    node1_keys.foreach(k => {
      dfQuery.append(",").append(node1_label).append("_").append(k).append(":\"',").append("CAST(").append(k).append(" as string) ,'\"','")
    })
    //create the neo query
    nQuery.append(" MATCH(m:").append(node1_label).append("{").append(node1_keys.map(k => k + ":row." + node1_label + "_" + k).mkString(",")).append("}) ")

    node2_keys.foreach(k => {
      dfQuery.append(",").append(node2_label).append("_").append(k).append(":\"',").append("CAST(").append(k).append(" as string) ,'\"','")
    })
    //create the neo query
    nQuery.append(" MATCH(n:").append(node2_label).append("{").append(node2_keys.map(k => k + ":row." + node2_label + "_" + k).mkString(",")).append("}) ")

    if (createOrMergeRelation.toUpperCase().equals("CREATE")){
      nQuery.append(" CREATE (m)-[:")
    }
    else {
      nQuery.append(" MERGE (m)-[:")
    }
    nQuery.append(relation_label).append("]->(n)")

    nQuery.append(";")
    dfQuery.append("}') as StringData FROM dft_" + relation_label)
    val dfQueryString = " SELECT DISTINCT " + dfQuery.toString() // + " ORDER BY 1"

    /*
    var partitions:Int = df.count().toInt
    if (partitions >  batchSize * 10) {
      partitions = partitions / batchSize
    }
    val dftSData = sparkSession.sql(dfQueryString).repartition(partitions)

    import sparkSession.implicits._
    dftSData.foreachPartition(
      partition => {
        val partitionasList = partition.map(
          record => record(0).toString).toList
        if (!partitionasList.isEmpty) {
          val driver = GraphDatabase.driver("bolt://" + cluster + "/7687", AuthTokens.basic(login, password))
          val session = driver.session
          val query = "UNWIND {rows:[" + partitionasList.mkString(",") + "]}.rows as row" + nQuery.toString()
          session.run(query)
          session.close()
        }
      })
      */
    val driver = GraphDatabase.driver("bolt://" + cluster + "/7687", AuthTokens.basic(login, password))
    val session = driver.session
    for (i <- 0 to commands.length - 1) {
      session.run(commands(i))
    }

    val dftSData = sparkSession.sql(dfQueryString)
    var batchCounter: Int = 0;
    val rowList  = ListBuffer.empty[String]
    for (x <- dftSData.collect) {
      rowList.append(x.toString().stripPrefix("[").stripSuffix("]"))
      batchCounter += 1;
      if (batchCounter > batchSize){
        batchCounter = 0;
        val query = "UNWIND {rows:[" + rowList.toList.mkString(",") + "]}.rows as row" + nQuery.toString()
        session.run(query)
        rowList.clear()
      }
    }
    //do final batch
    if (rowList.length > 0)
    {
      val query = "UNWIND {rows:[" + rowList.toList.mkString(",") + "]}.rows as row" + nQuery.toString()
      session.run(query)
    }
    session.close()
  }
}
