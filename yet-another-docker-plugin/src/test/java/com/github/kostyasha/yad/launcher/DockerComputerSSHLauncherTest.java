package com.github.kostyasha.yad.launcher;


import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerSSHConnector;
import com.github.kostyasha.yad_docker_java.com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.google.common.net.HostAndPort;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.slaves.Cloud;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

public class DockerComputerSSHLauncherTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

//    @WithPlugin("ssh-slaves-1.30.0")
    @LocalData
    @Test
    public void migration() {
        Cloud cl = jenkinsRule.getInstance().getCloud("cloudName");
        assertThat(cl, notNullValue());

        DockerCloud cloud = (DockerCloud) cl;
        assertThat(cloud, notNullValue());

        DockerSlaveTemplate template = cloud.getTemplateById("fb5f32a8-bfdd-4f00-8df8-fcf3cf14ab63");
        assertThat(template, notNullValue());

        DockerComputerLauncher l = template.getLauncher();
        assertThat(l, instanceOf(DockerComputerSSHLauncher.class));
        DockerComputerSSHLauncher sshLauncher = (DockerComputerSSHLauncher) l;

        DockerSSHConnector dockerSSHConnector = sshLauncher.getSshConnector();
        assertThat(dockerSSHConnector, notNullValue());

        assertThat(dockerSSHConnector.getSshHostKeyVerificationStrategy(), notNullValue());
        assertThat(dockerSSHConnector.getSshHostKeyVerificationStrategy(), instanceOf(KnownHostsFileKeyVerificationStrategy.class));
        assertThat(dockerSSHConnector.getSuffixStartSlaveCmd(), is("suffixStartSlave"));
        assertThat(dockerSSHConnector.getPrefixStartSlaveCmd(), is("prefixStartSlave"));


    }

    @Test
    public void dualStackBindingsUseDockerHostInEitherOrder() throws Exception {
        String ipv4 = """
                {"HostIp":"0.0.0.0","HostPort":"49163"}
                """;
        String ipv6 = """
                {"HostIp":"::","HostPort":"49164"}
                """;
        assertSshAddress("tcp://192.0.2.1:2376", ipv4, ipv6, "192.0.2.1", 49163);
        assertSshAddress("tcp://192.0.2.1:2376", ipv6, ipv4, "192.0.2.1", 49163);
        assertSshAddress("tcp://[2001:db8::1]:2376", ipv4, ipv6, "2001:db8::1", 49164);
        assertSshAddress("tcp://[2001:db8::1]:2376", ipv6, ipv4, "2001:db8::1", 49164);
    }

    @Test
    public void explicitIpv6BindingKeepsItsAddress() throws Exception {
        String wildcard = """
                {"HostIp":"0.0.0.0","HostPort":"49163"}
                """;
        String explicit = """
                {"HostIp":"2001:db8::2","HostPort":"49164"}
                """;
        assertSshAddress("tcp://192.0.2.1:2376", explicit, wildcard, "2001:db8::2", 49164);
    }

    private void assertSshAddress(String dockerUrl, String first, String second, String host, int port)
            throws Exception {
        String name = "ssh-cloud-" + jenkinsRule.jenkins.clouds.size();
        jenkinsRule.jenkins.clouds.add(new DockerCloud(name, List.of(), 1, new DockerConnector(dockerUrl)));
        InspectContainerResponse inspect = new ObjectMapper().readValue("""
                {"NetworkSettings":{"Ports":{"22/tcp":[%s,%s]}}}
                """.formatted(first, second), InspectContainerResponse.class);
        DockerSSHConnector connector = new DockerSSHConnector(
                22, null, null, null, null, null, null, null, null, null, null, null);
        DockerComputerSSHLauncher launcher = new DockerComputerSSHLauncher(connector);

        assertThat(launcher.getHostAndPort(name, inspect), is(HostAndPort.fromParts(host, port)));
    }
}
