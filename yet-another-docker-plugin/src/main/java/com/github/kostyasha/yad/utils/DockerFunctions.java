package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.client.ClientBuilderForPlugin;
import com.github.kostyasha.yad.client.ClientConfigBuilderForPlugin;
import com.github.kostyasha.yad.client.DockerCmdExecConfig;
import com.github.kostyasha.yad.client.DockerCmdExecConfigBuilderForPlugin;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.google.common.collect.Iterables;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher;
import com.github.kostyasha.yad.strategy.DockerCloudRetentionStrategy;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UI helper class.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerFunctions {
    private DockerFunctions() {
    }

    public static List<Descriptor<ComputerLauncher>> getDockerComputerLauncherDescriptors() {
        List<Descriptor<ComputerLauncher>> launchers = new ArrayList<>();

        launchers.add(DockerComputerSSHLauncher.DESCRIPTOR);
        launchers.add(DockerComputerJNLPLauncher.DESCRIPTOR);

        return launchers;
    }

    public static List<Descriptor<RetentionStrategy<?>>> getDockerRetentionStrategyDescriptors() {
        List<Descriptor<RetentionStrategy<?>>> strategies = new ArrayList<>();

        strategies.add(DockerOnceRetentionStrategy.DESCRIPTOR);
        strategies.add(DockerCloudRetentionStrategy.DESCRIPTOR);
        strategies.addAll(RetentionStrategy.all());

        return strategies;
    }


    /**
     * Wrapper around exec config and client config builders.
     * TODO move to MegaBuilder? :D
     */
    public static DockerClient createClient(DockerConnector connector)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        final DockerClientConfig clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                .forConnector(connector)
                .build();

        final DockerCmdExecConfig execConfig = DockerCmdExecConfigBuilderForPlugin.builder()
                .forConnector(connector)
                .build();

        return ClientBuilderForPlugin.builder()
                .withDockerClientConfig(clientConfig)
                .withDockerCmdExecConfig(execConfig)
                .build();
    }

    /**
     * Get the list of Docker servers.
     *
     * @return the list as a LinkedList of DockerCloud
     */
    public static synchronized List<DockerCloud> getServers() {
        return Jenkins.getActiveInstance().clouds.stream()
                .filter(Objects::nonNull)
                .filter(DockerCloud.class::isInstance)
                .map(cloud -> (DockerCloud) cloud)
                .collect(Collectors.toList());
    }

    public static DockerCloud anyCloudForLabel(Label label) {
        return getServers().stream()
                .filter(cloud -> cloud.canProvision(label))
                .findFirst()
                .orElse(null);
    }

    public DockerCloud getServer(final String serverName) {
        return Iterables.find(getServers(), input -> serverName.equals(input.getDisplayName()));
    }
}
