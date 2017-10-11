package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.launcher.DockerComputerIOLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher;
import com.github.kostyasha.yad.strategy.DockerCloudRetentionStrategy;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import com.github.kostyasha.yad_docker_java.com.google.common.collect.Iterables;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static jenkins.model.Jenkins.getInstance;

/**
 * UI helper class.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerFunctions {
    private DockerFunctions() {
    }

    @SuppressWarnings("rawtypes")
    public static List<NodePropertyDescriptor> getNodePropertyDescriptors(Class<? extends Node> clazz) {
        List<NodePropertyDescriptor> result = new ArrayList<>();
        Collection<NodePropertyDescriptor> list = (Collection) Jenkins.getInstance().getDescriptorList(NodeProperty.class);
        for (NodePropertyDescriptor npd : list) {
            if (npd.isApplicable(clazz)) {
                result.add(npd);
            }
        }
        return result;
    }

    /**
     * Because {@link Functions#getRetentionStrategyDescriptors()} is restricted.
     */
    public static List<Descriptor<RetentionStrategy<?>>> getRetentionStrategyDescriptors() {
        return RetentionStrategy.all();
    }

    /**
     * Only this plugin specific launchers.
     */
    public static List<Descriptor<ComputerLauncher>> getDockerComputerLauncherDescriptors() {
        List<Descriptor<ComputerLauncher>> launchers = new ArrayList<>();

        launchers.add(getInstance().getDescriptor(DockerComputerSSHLauncher.class));
        launchers.add(getInstance().getDescriptor(DockerComputerJNLPLauncher.class));
        launchers.add(getInstance().getDescriptor(DockerComputerIOLauncher.class));

        return launchers;
    }

    /**
     * Only this plugin specific strategies.
     */
    public static List<Descriptor<RetentionStrategy<?>>> getDockerRetentionStrategyDescriptors() {
        List<Descriptor<RetentionStrategy<?>>> strategies = new ArrayList<>();

        strategies.add(getInstance().getDescriptor(DockerOnceRetentionStrategy.class));
        strategies.add(getInstance().getDescriptor(DockerCloudRetentionStrategy.class));
        strategies.add(getInstance().getDescriptor(DockerComputerIOLauncher.class));

        return strategies;
    }


    /**
     * Get the list of Docker servers.
     *
     * @return the list as a LinkedList of DockerCloud
     */
    @Nonnull
    public static synchronized List<DockerCloud> getDockerClouds() {
        return getInstance().clouds.stream()
                .filter(Objects::nonNull)
                .filter(DockerCloud.class::isInstance)
                .map(cloud -> (DockerCloud) cloud)
                .collect(Collectors.toList());
    }

    public static DockerCloud anyCloudForLabel(Label label) {
        return getDockerClouds().stream()
                .filter(cloud -> cloud.canProvision(label))
                .findFirst()
                .orElse(null);
    }

    public static DockerCloud firstDockerCloudByName(final String serverName) {
        return Iterables.find(getDockerClouds(), input -> serverName.equals(input.getDisplayName()));
    }
}
