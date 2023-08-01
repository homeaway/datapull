package com.homeaway.datapullclient.data;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.http.HttpStatus;

@ApiModel(value = "Response entity for DataPull")
public class DeleteResponseEntity {

    @ApiModelProperty("HTTP code for response")
    private int statusCode = HttpStatus.OK.value();

    @ApiModelProperty("Response Message_cluster_id")
    private String message_cluster_id;


    public DeleteResponseEntity(int status, String cluster_id) {
        this.statusCode = status;
        this.message_cluster_id = cluster_id;
    }

}
