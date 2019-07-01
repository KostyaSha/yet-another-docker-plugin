package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.utils.HostAndPortChecker;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.NetworkSettings;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Ports;
import com.github.kostyasha.yad_docker_java.com.google.common.annotations.Beta;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Preconditions;
import com.github.kostyasha.yad_docker_java.com.google.common.net.HostAndPort;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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
        super(null);
        this.sshConnector = sshConnector;
    }

    public SSHConnector getSshConnector() {
        return sshConnector;
    }

    public DockerComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                                                InspectContainerResponse inspect) {
        // don't care, we need only launcher
        final DockerComputerSSHLauncher prepLauncher = new DockerComputerSSHLauncher(null);

        prepLauncher.setLauncher(getSSHLauncher(cloudId, dockerSlaveTemplate, inspect));

        return prepLauncher;
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate, CreateContainerCmd createCmd) {
        final int sshPort = getSshConnector().port;
        ExposedPort[] exposedPorts = createCmd.getExposedPorts();
        // we want allow exposing not only ssh ports
        if (nonNull(createCmd.getExposedPorts())) {
            ArrayList<ExposedPort> ports = new ArrayList<>(Arrays.asList(exposedPorts));
            ports.add(new ExposedPort(sshPort));
            createCmd.withExposedPorts(ports);
        } else {
            createCmd.withExposedPorts(new ExposedPort(sshPort));
        }

        createCmd.withPortSpecs(sshPort + "/tcp");

        String[] cmd = dockerSlaveTemplate.getDockerContainerLifecycle().getCreateContainer().getDockerCommandArray();
        if (cmd.length == 0) {
            //default value to preserve compatibility
            createCmd.withCmd("/bin/sh", "-c", "/usr/sbin/sshd -D -p " + sshPort);
        }

        createCmd.getHostConfig().getPortBindings().add(PortBinding.parse(Integer.toString(sshPort)));
    }

    @Override
    public boolean waitUp(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                          InspectContainerResponse containerInspect) {
        if (!super.waitUp(cloudId, dockerSlaveTemplate, containerInspect)) {
            return false;
        }

        final HostAndPort hostAndPort = getHostAndPort(cloudId, containerInspect);
        HostAndPortChecker hostAndPortChecker = HostAndPortChecker.create(hostAndPort);
        if (!hostAndPortChecker.withRetries(60).withEveryRetryWaitFor(2, TimeUnit.SECONDS)) {
            LOG.debug("TCP connection attempt failed 60 retries with 2 second interval for {}", hostAndPort);
            return false;
        }
        try {
            hostAndPortChecker.bySshWithEveryRetryWaitFor(2, TimeUnit.SECONDS);
        } catch (IOException ex) {
            LOG.warn("Can't connect to ssh for {}", hostAndPort, ex);
            return false;
        }

        return true;
    }

    private SSHLauncher getSSHLauncher(String cloudId, DockerSlaveTemplate template, InspectContainerResponse inspect) {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(inspect);

        try {
            final HostAndPort hostAndPort = getHostAndPort(cloudId, inspect);
            LOG.info("Creating slave SSH launcher for '{}:{}'. Cloud: '{}'. Template: '{}'",
                    hostAndPort.getHostText(), hostAndPort.getPort(), cloudId,
                    template.getDockerContainerLifecycle().getImage());
            return new SSHLauncher(hostAndPort.getHostText(), hostAndPort.getPort(),
                    sshConnector.getCredentials(),
                    sshConnector.jvmOptions,
                    sshConnector.javaPath,
                    sshConnector.prefixStartSlaveCmd,
                    sshConnector.suffixStartSlaveCmd,
                    sshConnector.launchTimeoutSeconds,
                    sshConnector.maxNumRetries,
                    sshConnector.retryWaitTime);
        } catch (NullPointerException ex) {
            throw new RuntimeException("Error happened. Probably there is no mapped port 22 in host for SSL. Config=" +
                    inspect, ex);
        }
    }

    public HostAndPort getHostAndPort(String cloudId, InspectContainerResponse ir) {
        // get exposed port
        ExposedPort sshPort = new ExposedPort(sshConnector.port);
        String host = null;
        Integer port = 22;

        final NetworkSettings networkSettings = ir.getNetworkSettings();
        final Ports ports = networkSettings.getPorts();
        final Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();
        final Ports.Binding[] sshBindings = bindings.get(sshPort);

        if (isNull(sshBindings)) {
            throw new IllegalStateException("SSH Binding not found for " + ir.getId() +
                    ". Add EXPOSE in image or expose SSH port in container create configuration!");
        }

        for (Ports.Binding b : sshBindings) {
            port = Integer.valueOf(b.getHostPortSpec());
            host = b.getHostIp();
        }

        //get address, if docker on localhost, then use local?
        if (host == null || host.equals("0.0.0.0")) {
            final DockerCloud dockerCloud = DockerCloud.getCloudByName(cloudId);
            Preconditions.checkNotNull(dockerCloud, "Can't get cloud '" + cloudId + "'. Cloud was renamed?");
            host = URI.create(dockerCloud.getConnector().getServerUrl()).getHost();
        }

        return HostAndPort.fromParts(host, port);
    }

    @Extension
    public static final class DescriptorImpl extends DelegatingComputerLauncher.DescriptorImpl {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return DockerCreateContainer.DescriptorImpl.doFillCredentialsIdItems(context);
        }

        public Class getSshConnectorClass() {
            return SSHConnector.class;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker SSH computer launcher";
        }
    }

}
