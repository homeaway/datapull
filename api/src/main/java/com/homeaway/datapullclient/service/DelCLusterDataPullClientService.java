package com.homeaway.datapullclient.service;

import com.homeaway.datapullclient.exception.ProcessingException;

public interface DelCLusterDataPullClientService {

    void runSimpleDataPull(final String cluster_id) throws ProcessingException;
}
