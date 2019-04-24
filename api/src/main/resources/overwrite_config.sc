import ammonite.ops._
import $ivy.`com.typesafe:config:1.3.1`
import $ivy.`com.fasterxml.jackson.core:jackson-databind:2.9.4`
import $ivy.`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.8`
import $ivy.`org.apache.commons:commons-lang3:3.4`

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions, ConfigValueFactory}
import java.io._
import java.util.Properties
import java.util.stream.Collectors
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind
import com.fasterxml.jackson.databind.node.{JsonNodeFactory, ObjectNode}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature
import scala.collection.mutable.ListBuffer


<!-- https://mvnrepository.com/artifact/com.typesafe/config -->

@main
def mainA(args: String*) = {
  val OVERRIDE_PROPERTIES = Seq("")
  val env  = args(0)
  println("Running script for env = "+env)
  val yamlMapper = new ObjectMapper(new YAMLFactory().enable(Feature.MINIMIZE_QUOTES));
  val masterConfig = yamlMapper.readTree(new File("master_application_config.yml"))

  var paths = new java.util.ArrayList[String]()
  findpath(masterConfig, "",  paths);
  val apiConfigFile = String.format("api/src/main/resources/application-%s.yml", env)
  val apiconfig = yamlMapper.readTree(new File(apiConfigFile))
  val coreConfigFile = "core/src/main/resources/application.yml"
  val coreconfig = yamlMapper.readTree(new File(coreConfigFile))

  paths.stream().filter(x => !masterConfig.at(x).asText("").equals("")).forEach(x => {
    val coreValue = coreconfig.at(x).asText("")
    val apiValue = apiconfig.at(x).asText("")
    val masterConfigValue =  masterConfig.at(x).asText("")
    if(coreValue.equals("")){
      val pathTillLastNode = x.substring(0, x.lastIndexOf("/"))
      val field = x.substring(x.lastIndexOf("/")+1, x.length)
      val nodeToBeModified = coreconfig.at(pathTillLastNode)
      if(nodeToBeModified.isMissingNode){
        addNodesToJson(coreconfig, x.substring(1).split("/"), field, masterConfigValue);
      }
      else{
        nodeToBeModified.asInstanceOf[ObjectNode].put(field, masterConfigValue)
      }

      var coreYaml = yamlMapper.writeValueAsString(coreconfig);
      coreYaml = coreYaml.replaceAll("null|---", "").trim
      val writer = new BufferedWriter(new FileWriter(new File(coreConfigFile)))
      writer.write(coreYaml)
      writer.close()
    }
    if(apiValue.equals("")){
      val pathTillLastNode = x.substring(0, x.lastIndexOf("/"))
      val field = x.substring(x.lastIndexOf("/")+1, x.length)
      val nodeToBeModified = apiconfig.at(pathTillLastNode)
      if(nodeToBeModified.isMissingNode){
        addNodesToJson(apiconfig, x.substring(1).split("/"), field, masterConfigValue);
      }
      else{
        nodeToBeModified.asInstanceOf[ObjectNode].put(field, masterConfigValue)
      }
      var apiYaml = yamlMapper.writeValueAsString(apiconfig);
      apiYaml = apiYaml.replaceAll("null|---", "").trim
      val writer = new BufferedWriter(new FileWriter(new File(apiConfigFile)))
      writer.write(apiYaml)
      writer.close()
    }
  })
}

def findpath(node: JsonNode, path : String, leafPaths : java.util.List[String]): Unit ={
  val it = node.fields();
  while(it.hasNext){
    val entry = it.next();
    val value = entry.getValue;
    val key = entry.getKey;
    val newPath = path+"/"+key;
    if(value.isValueNode){
      leafPaths.add(newPath)
    }
    findpath(entry.getValue, newPath, leafPaths)
  }
}

def addNodesToJson(root : JsonNode, nodes : Array[String], field : String, masterConfigValue : String) = {
  var parent  = root.asInstanceOf[ObjectNode]
  nodes.foreach(x => {
    println("addNodesToJson => node name = "+x)
    val nodeToBeModified = parent.at("/"+x)
    if(nodeToBeModified.isMissingNode){
      parent  = if(x.equals(field)) parent.put(x, masterConfigValue) else parent.putObject(x)
      println("addNodesToJson -Added missing node = "+x)
    }
    else{
      parent = nodeToBeModified.asInstanceOf[ObjectNode];
      println("addNodesToJson - Moved to next node = "+parent)
    }
  })

}