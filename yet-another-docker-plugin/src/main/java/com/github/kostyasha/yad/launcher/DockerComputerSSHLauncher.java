package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Ports;
import com.github.kostyasha.yad.docker_java.com.google.common.annotations.Beta;
import com.github.kostyasha.yad.docker_java.com.google.common.base.Preconditions;
import com.github.kostyasha.yad.docker_java.com.google.common.net.HostAndPort;
import com.github.kostyasha.yad.utils.HostAndPortChecker;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.util.ListBoxModel;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Configurable SSH launcher that expected ssh port to be exposed from docker container.
 */
@Beta
public class DockerComputerSSHLauncher extends DockerComputerLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DockerComputerSSHLauncher.class);
    // store real UI configuration
    protected final SSHConnector sshConnector;

    @DataBoundConstructor
    public DockerComputerSSHLauncher(SSHConnector sshConnector) {
        this.sshConnector = sshConnector;
    }

    public SSHConnector getSshConnector() {
        return sshConnector;
    }

    public ComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                                                InspectContainerResponse inspect) {
        // don't care, we need only launcher
        final DockerComputerSSHLauncher prepLauncher = new DockerComputerSSHLauncher(null);

        prepLauncher.setLauncher(getSSHLauncher(cloudId, dockerSlaveTemplate, inspect));

        return prepLauncher;
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate, CreateContainerCmd createCmd) {
        final int sshPort = getSshConnector().port;

        createCmd.withPortSpecs(sshPort + "/tcp");

        String[] cmd = dockerSlaveTemplate.getDockerContainerLifecycle().getCreateContainer().getDockerCommandArray();
        if (cmd.length == 0) {
            //default value to preserve compatibility
            createCmd.withCmd("bash", "-c", "/usr/sbin/sshd -D -p " + sshPort);
        }

        createCmd.getPortBindings().add(PortBinding.parse("0.0.0.0::" + sshPort));
    }

    @Override
    public boolean waitUp(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                          InspectContainerResponse containerInspect) {
        super.waitUp(cloudId, dockerSlaveTemplate, containerInspect);

        final HostAndPort hostAndPort = getHostAndPort(cloudId, containerInspect);
        HostAndPortChecker hostAndPortChecker = HostAndPortChecker.create(hostAndPort);
        if (!hostAndPortChecker.withRetries(60).withEveryRetryWaitFor(2, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            hostAndPortChecker.bySshWithEveryRetryWaitFor(2, TimeUnit.SECONDS);
        } catch (IOException ex) {
            LOG.warn("Can't connect to ssh", ex);
            return false;
        }

        return true;
    }

    private SSHLauncher getSSHLauncher(String cloudId, DockerSlaveTemplate template, InspectContainerResponse inspect) {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(inspect);

        try {
            final HostAndPort hostAndPort = getHostAndPort(cloudId, inspect);
            LOG.info("Creating slave SSH launcher for '{}:{}'", hostAndPort.getHostText(), hostAndPort.getPort());
            return new SSHLauncher(hostAndPort.getHostText(), hostAndPort.getPort(), sshConnector.getCredentials(),
                    sshConnector.jvmOptions, sshConnector.javaPath, sshConnector.prefixStartSlaveCmd,
                    sshConnector.suffixStartSlaveCmd, sshConnector.launchTimeoutSeconds);
        } catch (NullPointerException ex) {
            throw new RuntimeException("No mapped port 22 in host for SSL. Config=" + inspect, ex);
        }
    }

    public HostAndPort getHostAndPort(String cloudId, InspectContainerResponse ir) {
        // get exposed port
        ExposedPort sshPort = new ExposedPort(sshConnector.port);
        String host = null;
        Integer port = 22;

        final InspectContainerResponse.NetworkSettings networkSettings = ir.getNetworkSettings();
        final Ports ports = networkSettings.getPorts();
        final Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();
        final Ports.Binding[] sshBindings = bindings.get(sshPort);

        for (Ports.Binding b : sshBindings) {
            port = b.getHostPort();
            host = b.getHostIp();
        }

        //get address, if docker on localhost, then use local?
        if (host == null || host.equals("0.0.0.0")) {
            host = URI.create(DockerCloud.getCloudByName(cloudId).getConnector().getServerUrl()).getHost();
        }

        return HostAndPort.fromParts(host, port);
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<ComputerLauncher> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return DockerCreateContainer.DescriptorImpl.doFillCredentialsIdItems(context);
        }

        public Class getSshConnectorClass() {
            return SSHConnector.class;
        }

        @Override
        public String getDisplayName() {
            return "Docker SSH computer launcher";
        }
    }

}
