package com.homeaway.utils.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClientBuilder;
import com.amazonaws.services.rds.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.homeaway.constants.Environment;
import com.homeaway.dto.RDSSpecification;
import com.homeaway.utils.Wait;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class RDSInstance {
    public RDSSpecification rdsSpec;
    public String rdsEndPoint;

    private AmazonRDS client;

    public RDSInstance(Engine rdsEngine){
        this(rdsEngine,true);
    }

    public RDSInstance(Engine rdsEngine,boolean waitForAvailability) {
        File rdsSpecFile = null;

        JavaPropsMapper mapper = new JavaPropsMapper();
        if (rdsEngine.equals(Engine.Oracle)) {
            rdsSpecFile = new File("src/main/resources/aws_rds/Oracle.properties");
        } else if (rdsEngine == Engine.PostgreSQL) {
            rdsSpecFile = new File("src/main/resources/aws_rds/PostgreSQL.properties");
        } else if(rdsEngine == Engine.MySQL) {
            rdsSpecFile = new File("src/main/resources/aws_rds/MySQL.properties");
        } else if(rdsEngine == Engine.MSSQLServer){
            rdsSpecFile = new File("src/main/resources/aws_rds/MsSQLServer.properties");
        }

        try {
            rdsSpec = mapper.readValue(rdsSpecFile, RDSSpecification.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        client = AmazonRDSClientBuilder.standard().withRegion(Environment.awsRegion)
                .withCredentials(new AWSStaticCredentialsProvider(Environment.awsCredentials)).build();
        createDbInstance();
        if(waitForAvailability)
           waitTillRDSAvailability();
    }

    private void createDbInstance() {
        if(getStatus().equals("Not-Available")) {
            CreateDBInstanceRequest request = new CreateDBInstanceRequest().
                    withDBInstanceIdentifier(rdsSpec.identifier).
                    withDBInstanceClass(rdsSpec.instanceClass).
                    withEngine(rdsSpec.engine).
                    withDBName(rdsSpec.dbName).
                    withBackupRetentionPeriod(0).
                    withLicenseModel(rdsSpec.licenseModel).
                    withDBSubnetGroupName(rdsSpec.dbSubnetGroupName).
                    withAllocatedStorage(rdsSpec.allocatedStorage).
                    withVpcSecurityGroupIds(rdsSpec.vpcSecurityGroupId).
                    withMasterUsername(rdsSpec.username).
                    withMasterUserPassword(rdsSpec.password).
                    withTags(getTagList());
            client.createDBInstance(request);
            log.info("Creation of {} RDS instance is submitted.", rdsSpec.engine);
        }
    }

    private String getEndPoint() {
        DescribeDBInstancesRequest request = new DescribeDBInstancesRequest();
        request.setDBInstanceIdentifier(rdsSpec.identifier);
        DescribeDBInstancesResult result = client.describeDBInstances(request);
        return result.getDBInstances().get(0).getEndpoint().getAddress();

    }

    private String getStatus() {
        DescribeDBInstancesResult result = null;
        try{
            DescribeDBInstancesRequest request = new DescribeDBInstancesRequest();
            request.setDBInstanceIdentifier(rdsSpec.identifier);
            result = client.describeDBInstances(request);
        }catch (DBInstanceNotFoundException ex){
            return "Not-Available";
        }
        return result.getDBInstances().get(0).getDBInstanceStatus();

    }

    public void waitTillRDSAvailability(){
        int maxCount = 20;
        String status = getStatus();
        for(int i=0; i<maxCount && BooleanUtils.isFalse(status.equalsIgnoreCase("available")); i++){
            log.info("RDS instance {} is still not available. Waiting for 1 more minute.", rdsSpec.engine);
            Wait.inMinutes(1);
            status = getStatus();
        }
        if(status.equalsIgnoreCase("available")){
            rdsEndPoint = getEndPoint();
            log.info("RDS instance is created with endpoint - {}", rdsEndPoint);

        }
    }

    public void deleteRDS() {
        DeleteDBInstanceRequest request = new DeleteDBInstanceRequest().withDBInstanceIdentifier(rdsSpec.identifier).withSkipFinalSnapshot(true);
        client.deleteDBInstance(request);
        log.info("Deleting {} RDS instance", rdsSpec.engine);
    }

    private List<Tag> getTagList() {
        List<Tag> tagList = new ArrayList<>();
        File tagsJsonFile = new File("src/main/resources/AWSTags.json");
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> tagsMap = null;
        try {
            tagsMap = mapper.readValue(tagsJsonFile, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Object> entry : tagsMap.entrySet()) {
            Tag tag = new Tag();
            tag.setKey(entry.getKey());
            if(entry.getKey().equals("creator")){
                tag.setValue(Environment.userEmailAddress) ;
            } else{
                tag.setValue(entry.getValue().toString());
            }

            tagList.add(tag);
        }
        return tagList;

    }

    public enum Engine {
        Oracle, PostgreSQL, MySQL, MSSQLServer
    }
}
