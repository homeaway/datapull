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
  val apiConfigFile = String.format("application.yml", env)
  val apiconfig = yamlMapper.readTree(new File(apiConfigFile))
  var paths = new java.util.ArrayList[String]()
  findpath(apiconfig, "",  paths);

  val bufferedWriter = new PrintWriter(new FileWriter("application.properties"))

  paths.stream().filter(x => apiconfig.at(x).isArray || !apiconfig.at(x).asText("").equals("")).forEach(x => {
    val value = if(apiconfig.at(x).isArray) apiconfig.at(x) else apiconfig.at(x).asText()
    val key = x.substring(1).replaceAll("/", ".")
    bufferedWriter.write(key+"="+value+"\n")
  })
  bufferedWriter.close()
}

def findpath(node: JsonNode, path : String, leafPaths : java.util.List[String]): Unit ={
  val it = node.fields();
  while(it.hasNext){
    val entry = it.next();
    val value = entry.getValue;
    val key = entry.getKey;
    val newPath = path+"/"+key;
    if(value.isValueNode || value.isArray){
      leafPaths.add(newPath)
    }
    findpath(entry.getValue, newPath, leafPaths)
  }
}