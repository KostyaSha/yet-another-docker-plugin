package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DockerCreateContainerTest {
    @Test
    public void setsQuarterCpuLimit() {
        assertEquals(Long.valueOf(250_000_000L), nanoCpus("0.25"));
    }

    @Test
    public void setsElevenCpuLimitExactly() {
        assertEquals(Long.valueOf(11_000_000_000L), nanoCpus("11"));
    }

    @Test
    public void leavesCpuLimitUnsetWhenFieldIsEmpty() {
        assertNull(nanoCpus(""));
    }

    private Long nanoCpus(String cpus) {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setCpus(cpus);
        HostConfig hostConfig = HostConfig.newHostConfig();
        CreateContainerCmd command = mock(CreateContainerCmd.class);
        when(command.getHostConfig()).thenReturn(hostConfig);

        settings.fillContainerConfig(command, null);

        return hostConfig.getNanoCPUs();
    }
}
