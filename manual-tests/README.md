# DataPull Manual Tests

These tests are intended for a human to run interactively. At some point in the future, these tests should/will be automated and added to the functional tests. 

## How to run the tests?

Each subfolder below is a test. To run the test, please refer to teh README in the test's subfolder. But first, please confirm DataPull can run locally in a dockerised environment, using the steps below

### Confirm DataPull can run locally

#### Pre-requisites

Docker Desktop

#### Steps

1. Open a terminal pointing to the root folder of this repo
1. Please run DataPull locally in a Dockerised environment, by following all the steps defined in the section "Build and execute within a Dockerised Spark environment" of the [README file in the repo's root folder](../../README.md).
1. Confirm the previous step ran successfully, and that you are in the correct folder, by running the following command in the terminal. It should return at least one json file.
    ```shell
    ls target/classes/SampleData_Json/IntField\=1/
    ```
1. Delete the results of the previous local run by running the following commands in terminal
    ```shell
    sudo chown -R $(whoami):$(whoami) .
    rm -rf target/classes/SampleData_Json/
    ```

## How to create a new manual test?

For each new test, please create a subfolder such that
1. the folder name is all lowercase with underscores separating works
1. the folder name describes what the test does, in a nutshell
1. the folder has a README file that describes how to manually run the test, including howto clean up after the test is done. 