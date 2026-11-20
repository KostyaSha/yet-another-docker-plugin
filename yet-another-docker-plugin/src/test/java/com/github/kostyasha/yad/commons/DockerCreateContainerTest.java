package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import org.junit.Test;

import java.util.List;

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
        return hostConfig(settings).getNanoCPUs();
    }

    @Test
    public void passesEachSecurityOptionToHostConfig() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setSecurityOptsString("credentialspec=file://domain_gmsa-group.json\r\n  \r\n no-new-privileges=true ");
        assertEquals(List.of("credentialspec=file://domain_gmsa-group.json", "no-new-privileges=true"),
                hostConfig(settings).getSecurityOpts());
    }

    @Test
    public void omitsSecurityOptionsForOldAndClearedConfigurations() {
        DockerCreateContainer settings = new DockerCreateContainer();
        assertNull(hostConfig(settings).getSecurityOpts());
        settings.setSecurityOptsString("credentialspec=file://domain_gmsa-group.json");
        settings.setSecurityOptsString("  \r\n");
        assertNull(hostConfig(settings).getSecurityOpts());
    }

    private HostConfig hostConfig(DockerCreateContainer settings) {
        HostConfig hostConfig = HostConfig.newHostConfig();
        CreateContainerCmd command = mock(CreateContainerCmd.class);
        when(command.getHostConfig()).thenReturn(hostConfig);

        settings.fillContainerConfig(command, null);

        return hostConfig;
    }
}
