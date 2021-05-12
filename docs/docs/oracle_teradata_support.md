# Support for Oracle and Teradata sources/destinations for DataPull
To use Oracle and Teradata as sources/destinations for DataPull, you need to manually download their driver JARs from their companys' websites and add them as dependencies in the `/core/pom.xml` file of this repo. 

## Steps to download Oracle ojdbc jar
- Go to Oraacle JDBC downloads page (https://www.oracle.com/database/technologies/appdev/jdbc-ucp-21-1-c-downloads.html as of 5/11/2021) 
- Accept the license agreement
    - Oracle will ask you to create an account if you don't have one already
- Download the latest version of the ojdbc JAR (`ojdbc11.jar` as of 5/11/2021) 
  
## Steps to download Teradata jar
- Go to the URL https://downloads.teradata.com/download/connectivity/jdbc-driver
- For downloading Teradata jar, you will need to create an account on the Teradata website
- The download will be available in either .tar or .zip format. Download the latest archive file whose name will be in the format `TeraJDBC__indep_indep_{version}.zip`
    - The archive file will contain a jar file `terajdbc4.jar` that needs to be extracted.

## Steps to include Oracle into the project
- Run this command to include Oracle jar into maven repo. Run this command from folder where Oracle jars are present. Change the value of ```-Dversion=11.2.0.3``` in the command according to downloaded jar version.
- <b>Command</b>  :````docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir  -v $HOME/.m2/:/root/.m2/ -w /workdir maven:alpine mvn install:install-file -Dfile=ojdbc11.jar -DgroupId=com.oracle -DartifactId=ojdbc11 -Dversion=11.2.0.3 -Dpackaging=jar````
- Add below mentioned dependency to pom.xml(core/pom.xml) in DataPull core. Replace {version} with the downloaded jar version.
```
<dependency> 
    <groupId>com.oracle</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>{version}</version>
</dependency>
```  
## Steps to include Teradata into the project
   - Run these commands to include Teradata jars into maven repo. Run this command from folder where Teradata jars are present. Change the value of ```-Dversion=16.20.00.10``` in the command according to downloaded jar version.
   - <b>Command</b>  : ````docker run -e MAVEN_OPTS="-Xmx1024M -Xss128M -XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=1024M -XX:+CMSClassUnloadingEnabled" --rm -v $(pwd):/workdir  -v $HOME/.m2/:/root/.m2/ -w /workdir maven:alpine mvn install:install-file -Dfile=terajdbc4.jar -DgroupId=com.teradata -DartifactId=terajdbc4 -Dversion=16.20.00.10 -Dpackaging=jar````
   
   - Add below mentioned dependency to pom.xml(core/pom.xml) in DataPull core. Replace {version} with the downloaded jar version.
``` 
<dependency>
    <groupId>com.teradata</groupId>
    <artifactId>terajdbc4</artifactId>
    <version>{version}</version>
</dependency>
```