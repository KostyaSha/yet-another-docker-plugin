package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import java.math.BigDecimal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DockerCreateContainerTest {
    @Test
    public void setsQuarterCpuLimit() {
        assertNanoCpus("0.25", 250_000_000L);
    }

    @Test
    public void setsElevenCpuLimitExactly() {
        assertNanoCpus("11", 11_000_000_000L);
    }

    private void assertNanoCpus(String cpus, long expected) {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setCpus(new BigDecimal(cpus));
        HostConfig hostConfig = HostConfig.newHostConfig();
        CreateContainerCmd command = mock(CreateContainerCmd.class);
        when(command.getHostConfig()).thenReturn(hostConfig);

        settings.fillContainerConfig(command, null);

        assertEquals(Long.valueOf(expected), hostConfig.getNanoCPUs());
    }
}
