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

package com.homeaway.datapullclient.data;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.http.HttpStatus;

@ApiModel(value = "Response entity for DataPull")
public class ResponseEntity<T> {

    @ApiModelProperty("HTTP code for response")
    private int statusCode = HttpStatus.OK.value();

    @ApiModelProperty("Response Body")
    private T t;


    public ResponseEntity(int status, T t) {
        this.statusCode = status;
        this.t = t;
    }

    public int getStatus() {
        return statusCode;
    }

    public T getMessage() {
        return t;
    }
}