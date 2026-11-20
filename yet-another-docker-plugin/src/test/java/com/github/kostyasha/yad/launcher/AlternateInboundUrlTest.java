package com.github.kostyasha.yad.launcher;

import hudson.Functions;
import hudson.TcpSlaveAgentListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class AlternateInboundUrlTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void shellAgentConnectsThroughAlternateUrlAndTunnel() throws Exception {
        assumeFalse(Functions.isWindows());
        connectThroughAlternateUrl(false);
    }

    @Test
    public void powerShellAgentConnectsThroughAlternateUrlAndTunnel() throws Exception {
        assumeTrue("PowerShell is required", Functions.isWindows() || Arrays.stream(System.getenv("PATH").split(File.pathSeparator))
                .anyMatch(dir -> Files.isExecutable(Path.of(dir, "pwsh"))));
        connectThroughAlternateUrl(true);
    }

    private void connectThroughAlternateUrl(boolean powerShell) throws Exception {
        String reachable = j.getURL().toExternalForm();
        JenkinsLocationConfiguration.get().setUrl("http://127.0.0.1:0/unreachable/");
        j.jenkins.setSlaveAgentPort(0);
        DockerComputerJNLPLauncher docker = new DockerComputerJNLPLauncher();
        docker.setJenkinsUrl(reachable);
        docker.setTunnel("127.0.0.1:" + j.jenkins.getTcpSlaveAgentListener().getPort());
        JNLPLauncher inbound = docker.getLauncher();

        Path root = temp.newFolder("agent with spaces").toPath();
        DumbSlave agent = new DumbSlave("alternate url agent", root.toString(), inbound);
        j.jenkins.addNode(agent);
        SlaveComputer computer = (SlaveComputer) agent.toComputer();
        assertNotNull(computer);
        assertEquals("", InboundAgentArguments.forAlternateUrl("", computer, inbound));

        Map<String, String> config = new LinkedHashMap<>();
        config.put("JENKINS_URL", docker.getJenkinsUrl(j.jenkins.getRootUrl()));
        config.put("INBOUND_ARGS_B64", InboundAgentArguments.forAlternateUrl(docker.getJenkinsUrl(), computer, inbound));
        config.put("COMPUTER_URL", computer.getUrl());
        config.put("COMPUTER_SECRET", computer.getJnlpMac());
        config.put("JENKINS_HOME", root.toString());
        config.put("JENKINS_USER", System.getProperty("user.name"));
        config.put("JAVA_OPTS", "");
        config.put("SLAVE_OPTS", "");
        config.put("NO_RECONNECT_SLAVE", "true");
        config.put("NO_CERTIFICATE_CHECK", "false");

        // Discovery advertises an unusable TCP port. Only the configured tunnel can connect.
        Integer originalPort = TcpSlaveAgentListener.CLI_PORT;
        TcpSlaveAgentListener.CLI_PORT = 1;
        try {
            Process process = startBootstrap(config, powerShell);
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                while (computer.isOffline() && process.isAlive() && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                }
                assertTrue("Remoting did not connect through the alternate URL and tunnel", computer.isOnline());
                assertTrue("Remoting did not use the agent work directory", Files.isDirectory(root.resolve("remoting")));
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
        } finally {
            TcpSlaveAgentListener.CLI_PORT = originalPort;
        }
    }

    private Process startBootstrap(Map<String, String> values, boolean powerShell) throws Exception {
        String extension = powerShell ? ".ps1" : ".sh";
        Path script = temp.getRoot().toPath().resolve("init" + extension);
        try (InputStream resource = DockerComputerJNLPLauncher.class.getResourceAsStream("DockerComputerJNLPLauncher/init" + extension)) {
            assertNotNull(resource);
            Files.copy(resource, script);
        }
        StringBuilder config = new StringBuilder();
        values.forEach((key, value) -> {
            if (powerShell) {
                config.append('$');
            }
            config.append(key).append("='").append(value.replace("'", powerShell ? "''" : "'\\''")).append("'\n");
        });
        Files.writeString(script.resolveSibling("config" + extension), config);
        ProcessBuilder builder = powerShell
                ? new ProcessBuilder(Functions.isWindows() ? "powershell.exe" : "pwsh", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(), "10")
                : new ProcessBuilder("sh", script.toString(), "10");
        return builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }
}
