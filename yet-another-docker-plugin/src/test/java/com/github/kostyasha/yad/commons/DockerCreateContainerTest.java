package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AccessMode;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Bind;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PropagationMode;
import hudson.util.FormValidation;
import org.junit.Test;

import java.util.List;
import java.util.Map;

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
    public void addsSelectedCapabilitiesWithoutEnablingPrivilegedMode() throws Exception {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setCapAdd(" CAP_SYS_PTRACE\r\n\r\n NET_ADMIN\nSYS_PTRACE ");
        HostConfig config = hostConfig(settings);
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree("[\"SYS_PTRACE\",\"NET_ADMIN\"]"), mapper.valueToTree(config).get("CapAdd"));
        assertNull(config.getPrivileged());
    }

    @Test
    public void omitsCapabilitiesForOldAndClearedConfigurations() {
        DockerCreateContainer settings = new DockerCreateContainer();
        assertNull(hostConfig(settings).getCapAdd());
        settings.setCapAdd("SYS_PTRACE");
        settings.setCapAdd(" \r\n ");
        assertNull(hostConfig(settings).getCapAdd());
    }

    @Test
    public void rejectsUnknownCapability() {
        assertEquals(FormValidation.Kind.ERROR,
                new DockerCreateContainer.DescriptorImpl().doCheckCapAdd("CAP_UNKNOWN").kind);
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

    @Test
    public void passesStorageOptionsToHostConfig() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setStorageOptsString("size=30GB\r\n  \r\n size = 50GB \r\n example=a=b ");
        assertEquals(Map.of("size", "50GB", "example", "a=b"), hostConfig(settings).getStorageOpt());
    }

    @Test
    public void omitsStorageOptionsForOldAndClearedConfigurations() {
        DockerCreateContainer settings = new DockerCreateContainer();
        assertNull(hostConfig(settings).getStorageOpt());
        settings.setStorageOptsString("size=50GB");
        settings.setStorageOptsString("  \r\n");
        assertNull(hostConfig(settings).getStorageOpt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsStorageOptionsWithoutAnEqualsSign() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setStorageOptsString("size=50GB\ninvalid");
        hostConfig(settings);
    }

    @Test
    public void serializesTmpfsMountsForDocker() throws Exception {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setTmpfsMounts("/run\r\n  \r\n /run/lock:size=64m,mode=1777 ");
        ObjectMapper mapper = new ObjectMapper();

        assertEquals(mapper.readTree("{\"/run\":\"\",\"/run/lock\":\"size=64m,mode=1777\"}"),
                mapper.valueToTree(hostConfig(settings)).get("Tmpfs"));
    }

    @Test
    public void omitsTmpfsMountsForOldAndClearedConfigurations() {
        DockerCreateContainer settings = new DockerCreateContainer();
        assertNull(hostConfig(settings).getTmpFs());
        settings.setTmpfsMounts("/run");
        settings.setTmpfsMounts("  \r\n");
        assertNull(hostConfig(settings).getTmpFs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRelativeTmpfsMountPaths() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setTmpfsMounts("run:size=64m");
        hostConfig(settings);
    }

    @Test
    public void serializesSupplementaryGroupsForDocker() throws Exception {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setSupplementaryGroupsString("  997 \r\n  \r\n video ");
        ObjectMapper mapper = new ObjectMapper();

        assertEquals(mapper.readTree("[\"997\",\"video\"]"),
                mapper.valueToTree(hostConfig(settings)).get("GroupAdd"));
    }

    @Test
    public void omitsSupplementaryGroupsForOldAndClearedConfigurations() {
        DockerCreateContainer settings = new DockerCreateContainer();
        assertNull(hostConfig(settings).getGroupAdd());
        settings.setSupplementaryGroupsString("997");
        settings.setSupplementaryGroupsString("  \r\n");
        assertNull(hostConfig(settings).getGroupAdd());
    }

    @Test
    public void readOnlyBindsSurviveTrailingSpacesAndWindowsLineEndings() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setVolumesString("/src1:/dst1:ro\n/src2:/dst2:ro \n/src3:/dst3:ro\r\n/src4:/dst4:rw");

        Bind[] binds = hostConfig(settings).getBinds();
        assertEquals(4, binds.length);
        assertEquals(AccessMode.ro, binds[0].getAccessMode());
        assertEquals(AccessMode.ro, binds[1].getAccessMode());
        assertEquals(AccessMode.ro, binds[2].getAccessMode());
        assertEquals(AccessMode.rw, binds[3].getAccessMode());
    }

    @Test
    public void normalizesFlagsInStoredVolumesWithoutTrimmingPaths() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setVolumes(List.of("/host path :/container path : ro , rshared \r"));

        Bind bind = hostConfig(settings).getBinds()[0];
        assertEquals("/host path ", bind.getPath());
        assertEquals("/container path ", bind.getVolume().getPath());
        assertEquals(AccessMode.ro, bind.getAccessMode());
        assertEquals(PropagationMode.RSHARED, bind.getPropagationMode());
    }

    @Test
    public void supportsForwardSlashesInWindowsVolumePaths() {
        String volumes = "data:c:/data\nc:/jenkins:c:/jenkins: ro\nC:\\host:D:/container/work";
        assertEquals(FormValidation.Kind.OK,
                new DockerCreateContainer.DescriptorImpl().doCheckVolumesString(volumes).kind);

        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setVolumesString(volumes);
        Bind[] binds = hostConfig(settings).getBinds();

        assertEquals(3, binds.length);
        assertEquals("data:c:\\data:rw", binds[0].toString());
        assertEquals("c:\\jenkins:c:\\jenkins:ro", binds[1].toString());
        assertEquals("C:\\host:D:\\container\\work:rw", binds[2].toString());
    }

    @Test
    public void preservesSingleLetterVolumeNamesWithUnixDestinations() {
        DockerCreateContainer settings = new DockerCreateContainer();
        settings.setVolumesString("a:/data\nb:/logs:ro");

        Bind[] binds = hostConfig(settings).getBinds();
        assertEquals(2, binds.length);
        assertEquals("a:/data:rw", binds[0].toString());
        assertEquals("b:/logs:ro", binds[1].toString());
    }

    private HostConfig hostConfig(DockerCreateContainer settings) {
        HostConfig hostConfig = HostConfig.newHostConfig();
        CreateContainerCmd command = mock(CreateContainerCmd.class);
        when(command.getHostConfig()).thenReturn(hostConfig);

        settings.fillContainerConfig(command, null);

        return hostConfig;
    }
}
