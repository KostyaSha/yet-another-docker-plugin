package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.launcher.DockerComputerIOLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher;
import com.github.kostyasha.yad.strategy.DockerCloudRetentionStrategy;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.Functions;
import hudson.model.Descriptor;
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
    public static List<DockerCloud> getAllDockerClouds() {
        return getInstance().clouds.stream()
                .filter(Objects::nonNull)
                .filter(DockerCloud.class::isInstance)
                .map(cloud -> (DockerCloud) cloud)
                .collect(Collectors.toList());
    }

    /**
     * Count the number of current docker slaves for a given DockerCloud.
     *
     * @param cloud A DockerCloud.
     * @return The number of slaves provisioned by the DockerCloud.
     */
    public static int countCurrentDockerSlaves(DockerCloud cloud) {
        try {
            return cloud.countCurrentDockerSlaves(null);
        } catch (Exception e) {
            //an exception was thrown so return an invalid count for current docker slaves
            return -1;
        }
    }


}
