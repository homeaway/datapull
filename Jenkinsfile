#!/usr/bin/env groovy
properties ([
        buildDiscarder(logRotator(artifactNumToKeepStr: '5', daysToKeepStr: '15')),
        disableConcurrentBuilds()
])

node( 'dood' ) {
        withCredentials([
            file(credentialsId: "datapull_user_dev_secrets", variable: 'local_secrets_dev'),
            file(credentialsId: "datapull_user_test_secrets", variable: 'local_secrets_test'),
            file(credentialsId: "datapull_user_stage_secrets", variable: 'local_secrets_stage'),
            file(credentialsId: "datapull_user_prod_secrets", variable: 'local_secrets_prod'),
            file(credentialsId: 'jenkins_id_rsa', variable: 'id_rsa'),
            file(credentialsId: 'ssh_config', variable: 'ssh_config')
        ]) {
    stage('Setup') {
        deleteDir()
        checkout scm
    }

    def datapullProps = readProperties file: "${local_secrets_dev}"

    if ("${BRANCH_NAME}" == 'test'){
             datapullProps = readProperties file: "${local_secrets_test}"
        } else if ("${BRANCH_NAME}" == 'stage') {
            datapullProps = readProperties file: "${local_secrets_stage}"
        } else if ("${BRANCH_NAME}" == 'prod') {
            datapullProps = readProperties file: "${local_secrets_prod}"
        }

    stage('Build & Test') {

        echo "Building"

        sh "cd api/src/main/resources/terraform/datapull_task; sh ./ecs_deploy.sh $datapullProps.AWS_ACCESS_KEY_ID $datapullProps.AWS_SECRET_ACCESS_KEY datapull-web-api \"${BRANCH_NAME}\""

           }

}}
