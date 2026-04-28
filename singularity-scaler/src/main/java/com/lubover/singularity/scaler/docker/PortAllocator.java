package com.lubover.singularity.scaler.docker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PortAllocator {

    private final DockerContainerInspector containerInspector;

    public int allocatePort(String serviceName, int basePort, int portStep) {
        Set<Integer> usedPorts = new HashSet<>();
        for (DockerContainerInfo info : containerInspector.listContainers()) {
            usedPorts.addAll(info.getHostPorts());
        }
        int index = containerInspector.getMaxIndex(serviceName) + 1;
        for (int i = index; i < 1000; i++) {
            int port = basePort + portStep * i;
            if (!usedPorts.contains(port)) {
                return port;
            }
        }
        throw new IllegalStateException("No free port found for service " + serviceName);
    }
}
