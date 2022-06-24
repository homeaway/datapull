import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

import java.io._


<!-- https://mvnrepository.com/artifact/com.typesafe/config -->

@main
def mainA(args: String*) = {
  val OVERRIDE_PROPERTIES = Seq("")
  val env = args(0)
  println("Running script for env = " + env)
  val yamlMapper = new ObjectMapper(new YAMLFactory().enable(Feature.MINIMIZE_QUOTES));

  val masterconfigFile= String.format("master_application_config-%s.yml",env)
  val masterConfig = yamlMapper.readTree(new File(masterconfigFile))

  var paths = new java.util.ArrayList[String]()
  findpath(masterConfig, "",  paths);
  val apiConfigFile = String.format("api/src/main/resources/application-%s.yml", env)
  val apiconfig = yamlMapper.readTree(new File(apiConfigFile))
  val coreConfigFile = String.format("core/src/main/resources/application-%s.yml",env)
  val coreconfig = yamlMapper.readTree(new File(coreConfigFile))
  val apiConfigDest="api/src/main/resources/application.yml"
  val coreConfigDest="core/src/main/resources/application.yml"

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
      val writer = new BufferedWriter(new FileWriter(new File(coreConfigDest)))
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
      val writer = new BufferedWriter(new FileWriter(new File(apiConfigDest)))
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