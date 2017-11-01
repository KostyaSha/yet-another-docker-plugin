package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerNodeProperty extends NodeProperty<Node> {

    private String containerId = "DOCKER_CONTAINER_ID";
    private boolean containerIdCheck = true;

    private String cloudId = "JENKINS_CLOUD_ID";
    private boolean cloudIdCheck = true;

    private String dockerHost = "DOCKER_HOST";
    private boolean dockerHostCheck = true;


    // Default UI
    public DockerNodeProperty() {
    }

    public DockerNodeProperty(String containerId, String cloudId, String dockerHost) {
        this.containerId = containerId;
        this.containerIdCheck = true;

        this.cloudId = cloudId;
        this.cloudIdCheck = true;

        this.dockerHost = dockerHost;
        this.dockerHostCheck = true;
    }

    // Crazy UI binding
    @DataBoundConstructor
    public DockerNodeProperty(String containerId, boolean containerIdCheck,
                              String cloudId, boolean cloudIdCheck,
                              String dockerHost, boolean dockerHostCheck) {
        this.containerId = containerId;
        this.containerIdCheck = containerIdCheck;

        this.cloudId = cloudId;
        this.cloudIdCheck = cloudIdCheck;

        this.dockerHost = dockerHost;
        this.dockerHostCheck = dockerHostCheck;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public boolean isContainerIdCheck() {
        return containerIdCheck;
    }

    public boolean isCloudIdCheck() {
        return cloudIdCheck;
    }

    public boolean isDockerHostCheck() {
        return dockerHostCheck;
    }

    @Symbol("dockerEnvVars")
    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Node> targetType) {
            return targetType.isAssignableFrom(DockerSlave.class) ||
                    targetType.isAssignableFrom(DockerSlaveSingle.class);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker variables";
        }
    }
}
