package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad_docker_java.com.google.common.collect.Iterables;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher;
import com.github.kostyasha.yad.strategy.DockerCloudRetentionStrategy;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
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
     * Get the list of Docker servers.
     *
     * @return the list as a LinkedList of DockerCloud
     */
    @Nonnull
    public static synchronized List<DockerCloud> getDockerClouds() {
        return Jenkins.getActiveInstance().clouds.stream()
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
