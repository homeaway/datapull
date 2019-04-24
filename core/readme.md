# DataPull #

## Wiki ##

If you want to learn how to use DataPull to migrate your data, please navigate to the wiki page(index.md) present in the docs folder in the repo.

If you want to join the coding party and make this tool better, please continue reading below

## How to debug/contribute to this tool? ##

### Pre-requisites ###
* IntelliJ with Scala plugin configured. Check out this [Help page](https://www.jetbrains.com/help/idea/managing-plugins.html) if you don't have this.
* Knowledge of Scala; since this tool is written in it.

### To run a Debug Execution from this git repo... ###
* Clone this repo locally and checkout the master branch (the checkout usualy happens automatically when you clone)
* Open the local repo/folder in IntelliJ IDE
* By default, this code is designed to execute a sample JSON input file :Input_Sample_filesystem-to-filesystem.json in the below path(core/src/main/resources/Input_Sample_filesystem-to-filesystem.json) that moves data from a CSV file [HelloWorld.csv](core/src/main/resources/SampleData/HelloWorld.csv) to a folder of json files named SampleData_Json. 
* Go to File > Project Structure... , and choose 1.8 (java version) as the Project SDK
* Go to Run > Edit Configurations... , and do the following
    * Create an Application configuration (use the + sign on the top left corner of the modal window)
    * Set the Name to Debug
    * Set the Main Class as DataMigrationFramework
    * Use classpath of module DataMigrationFramework
    * Set JRE to 1.8
    * Click Apply and then OK
* Click Run > Debug 'Debug' to start the debug execution
* Open the relative path target/classes/SampleData_Json to find the result of your data migration i.e. the data from target/classes/SampleData/HelloWorld.csv transformed into JSON.