package com.homeaway.datapullclient.data;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.http.HttpStatus;

@ApiModel(value = "Delete Cluster Response entity for DataPull")
public class DelClusterResponseEntity {

    @ApiModelProperty("HTTP code for response")
    private int statusCode = HttpStatus.OK.value();

    @ApiModelProperty("Response Message_cluster_id")
    private String message_cluster_id;

    public DelClusterResponseEntity(int status, String cluster_id) {
        this.statusCode = status;
        this.message_cluster_id = cluster_id;
    }

}
