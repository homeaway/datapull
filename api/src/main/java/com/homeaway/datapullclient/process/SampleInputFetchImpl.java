package com.homeaway.datapullclient.process;

import com.google.common.base.Strings;
import com.homeaway.datapullclient.config.StoredInputProviderConfig;
import com.homeaway.datapullclient.exception.InputException;
import com.homeaway.datapullclient.input.InputConfiguration;
import com.homeaway.datapullclient.service.SampleInputFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SampleInputFetchImpl implements SampleInputFetchService {
    @Autowired
    private StoredInputProviderConfig config;

    @Override
    public Set<InputConfiguration> getSampleInputConfs(String sources, String destination) {
        log.info(" Source : {} && Destination : {}", sources, destination);
        if ((sources == null || sources.isEmpty()) && Strings.isNullOrEmpty(destination))
            throw new InputException("Either of source and destination must be present.");
        Set<String> srcs = sources != null && !sources.isEmpty()?Arrays.stream(sources.split(",")).map(s -> s.trim()).collect(Collectors.toSet()):null;
        return getKeyTypeNKey(srcs, destination).stream().map(p -> config.getSample(p.getFirst(), p.getSecond())).flatMap(c -> c.stream()).collect(Collectors.toSet());
    }

    private Set<Pair<String, String>> getKeyTypeNKey(Set<String> sources, String destination) {
        String keyType = sources != null && !sources.isEmpty() && destination != null ? "srcdest" : sources != null && !sources.isEmpty() ? "src" : "dest";
        Set<Pair<String, String>> searchSet = null;
        if (keyType.equalsIgnoreCase("srcdest") || keyType.equals("src"))
            searchSet = sources.stream().map(src -> Pair.of(keyType, src.concat(Strings.isNullOrEmpty(destination) ? "" : destination))).collect(Collectors.toSet());
        else {
            searchSet = new HashSet<>();
            searchSet.add(Pair.of(keyType, destination));
        }

        return searchSet;
    }

}
