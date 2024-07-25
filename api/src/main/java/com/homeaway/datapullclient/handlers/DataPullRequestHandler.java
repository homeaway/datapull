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

package com.homeaway.datapullclient.handlers;

import com.homeaway.datapullclient.api.DataPullClientApi;
import com.homeaway.datapullclient.data.JobStatus;
import com.homeaway.datapullclient.data.ResponseEntity;
import com.homeaway.datapullclient.data.SimpleResponseEntity;
import com.homeaway.datapullclient.exception.InputException;
import com.homeaway.datapullclient.exception.ProcessingException;
import com.homeaway.datapullclient.service.DataPullClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class DataPullRequestHandler implements DataPullClientApi {

    @Autowired
    private DataPullClientService service;

    @Override
    public ResponseEntity startDataPull(HttpEntity<String> inputJson) {
        if(log.isDebugEnabled())
            log.debug("startDataPull -> inputJson="+inputJson);
        ResponseEntity entity = null;
        try{
            service.runDataPull(inputJson.getBody());
            entity = new ResponseEntity(HttpStatus.ACCEPTED.value(), "Request Succesfully registered : "+inputJson);
        }
        catch(ProcessingException e){
            throw new InputException("DataPull application failed for inputJson : "+inputJson+" \n "+e.getMessage());
        }

        if(log.isDebugEnabled())
            log.debug("startDataPull <- return");

        return entity;
    }

    @Override
    public ResponseEntity healthCheck() {
        ResponseEntity result = new ResponseEntity(HttpStatus.OK.value(), "Health Check Successfull ");
        return result;
    }

    @Override
    public SimpleResponseEntity startSimpleDataPull(String pipelinename, String awsenv) {
        if(log.isDebugEnabled())
            log.debug("startSimpleDataPull -> data="+awsenv);
        SimpleResponseEntity entity = null;
        try{
            service.runSimpleDataPull(awsenv,pipelinename);
            entity = new SimpleResponseEntity(HttpStatus.ACCEPTED.value(), "Request Succesfully registered : "+awsenv
                    ,"Request Succesfully registered : "+pipelinename);
        }
        catch(ProcessingException e){
            throw new InputException("DataPull application failed for data : "+awsenv+" \n "+e.getMessage());
        }

        if(log.isDebugEnabled())
            log.debug("startSimpleDataPull <- return");

        return entity;
    }

    @Override
    public org.springframework.http.ResponseEntity<JobStatus> getDataPullPipelineStatus(String pipelinename) {
        JobStatus jobStatus;
        try {
             jobStatus=service.getDataPullPipelineStatus(pipelinename);


        } catch (ProcessingException   e) {
            throw new RuntimeException(e);
        }

        return org.springframework.http.ResponseEntity.status(HttpStatus.OK).body(jobStatus);
    }

    @Override
    public ResponseEntity terminateCluster(String pipelinename) {
        String clusterId;
        try {
            clusterId=service.terminateCluster(pipelinename);


        } catch (ProcessingException   e) {
            throw new RuntimeException(e);
        }

        return new ResponseEntity(200,"Cluster terminated id : " +clusterId);
    }
}



