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

package com.homeaway.datapullclient.api;

import com.homeaway.datapullclient.data.ResponseEntity;
import com.homeaway.datapullclient.data.SimpleResponseEntity;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RequestMapping("/api/v1")
public interface DataPullClientApi {

    String NOTES_TEXT_HTML = "POST /inputJson<br/>{<br/>&nbsp;&nbsp;&nbsp;&nbsp;\"migrations\": [<br/>&nbsp;&nbsp;&nbsp;&nbsp;{<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"source\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"destination\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"mappings\": [<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;]<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;}<br/>" +
            "],<br/>" +
            "\"cluster\": {<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"emr_security_configuration\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"cronexpression\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"pipelinename\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"awsenv\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"portfolio\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"product\": \"….\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"ec2instanceprofile\": \"…\",<br/>" +
            "&nbsp;&nbsp;&nbsp;&nbsp;\"ComponentInfo\":\"\"<br/>" +
            "}<br/>" +
            "}";

    String SIMPLE_ENDPOINT_NOTES_TEXT_HTML = "Give the inputs of environment name and pipeline name";

    @ApiOperation(value = "Given a JSON input , this creates a Jenkins  pipline that can be scheduled to create an EMR cluster, run a DataPull step and terminates the cluster",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, response = ResponseEntity.class
    , notes = NOTES_TEXT_HTML, nickname = "startDatapull")
    @ResponseStatus(value = HttpStatus.ACCEPTED)
    @RequestMapping(value = "/DataPullPipeline", method = POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "inputJson", value = "Input json", required = true, dataType = "String", paramType = "body")
    })
    ResponseEntity startDataPull(HttpEntity<String> inputJson);

    @ApiOperation(value = "DataPull healthcheck operations", response = ResponseEntity.class, produces = "application/json" , nickname = "healthCheck")
    @RequestMapping(value="/healthCheck", method = GET, produces = "application/json")
    ResponseEntity healthCheck();

    @ApiOperation(value = "Given environment name and pipeline name this api re-runs a datapull from history",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, response = ResponseEntity.class
            , notes = SIMPLE_ENDPOINT_NOTES_TEXT_HTML, nickname = "startDatapull")
    @ResponseStatus(value = HttpStatus.ACCEPTED)
    @RequestMapping(value = "/SimpleDataPullPipeline", method = POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "awsenv", value = "awsenv", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "pipelinename", value = "pipelinename", required = true, dataType = "String", paramType = "query")
    })
    SimpleResponseEntity startSimpleDataPull(@RequestParam("pipelinename") String pipelinename , @RequestParam("awsenv") String  awenv);

    @ApiOperation(value = "Given source or destination platform the api returns the existing ran sample config json.",
            produces = MediaType.APPLICATION_JSON_VALUE, response = ResponseEntity.class
            , notes = "Given either source/s or destination Or both we can fetch the existing json. ", nickname = "sampleInput")
    @ResponseStatus(value = HttpStatus.ACCEPTED)
    @RequestMapping(value = "/getSampleInputJson", method = GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "sources", value = "Specify one or more comma seperated sources for the config being search.", required = false, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "destination", value = "Specify the destination for the config to be searched", required = false, dataType = "String", paramType = "query")
    })
    ResponseEntity getSampleInputJson(@RequestParam("sources") String sources, @RequestParam("destination") String destination);
}
