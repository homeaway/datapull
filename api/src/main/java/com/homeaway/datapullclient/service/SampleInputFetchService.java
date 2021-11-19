package com.homeaway.datapullclient.service;

import com.homeaway.datapullclient.input.InputConfiguration;

import java.util.Set;

public interface SampleInputFetchService {
    public Set<InputConfiguration> getSampleInputConfs(String sources, String destination);
}
