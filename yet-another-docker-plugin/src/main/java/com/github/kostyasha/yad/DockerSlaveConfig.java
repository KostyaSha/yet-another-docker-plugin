package com.github.kostyasha.yad;

import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Strings;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;

/**
 * Some generic config with everything required for container-slave operaition.
 * Without docker connector because one connector may have multiple configs i.e. {@link DockerCloud}
 *
 * @author Kanstantsin Shautsou
 */
public class DockerSlaveConfig extends AbstractDescribableImpl<DockerSlaveConfig> {
    /**
     * Unique id of this template configuration. Required for:
     * - hashcode,
     * - cloud counting
     */
    @Nonnull
    protected final String id;

    private String labelString = "docker";

    protected ComputerLauncher launcher = new DockerComputerJNLPLauncher();

    private String remoteFs = "/home/jenkins";

    protected Node.Mode mode = Node.Mode.EXCLUSIVE;

    protected RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(10);

    protected int numExecutors = 1;

    /**
     * Bundle class that contains all docker related actions/configs
     */
    protected DockerContainerLifecycle dockerContainerLifecycle = new DockerContainerLifecycle();

    private List<? extends NodeProperty<?>> nodeProperties = emptyList();

    public DockerSlaveConfig() {
        this.id = randomUUID().toString();
    }

    /**
     * @param id some unique id to identify this configuration. Use case - count running computers based on this config.
     */
    public DockerSlaveConfig(@Nonnull String id) {
        this.id = id;
    }

    public DockerContainerLifecycle getDockerContainerLifecycle() {
        return dockerContainerLifecycle;
    }

    @DataBoundSetter
    public void setDockerContainerLifecycle(DockerContainerLifecycle dockerContainerLifecycle) {
        this.dockerContainerLifecycle = dockerContainerLifecycle;
    }

    public String getLabelString() {
        return labelString;
    }

    @DataBoundSetter
    public void setLabelString(String labelString) {
        this.labelString = Util.fixNull(labelString);
    }

    @DataBoundSetter
    public void setMode(Node.Mode mode) {
        this.mode = mode;
    }

    public Node.Mode getMode() {
        return mode;
    }

    /**
     * Experimental option allows set number of executors
     */
    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    @DataBoundSetter
    public void setRetentionStrategy(RetentionStrategy retentionStrategy) {
        this.retentionStrategy = retentionStrategy;
    }

    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy;
    }

    @DataBoundSetter
    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }

    @Nonnull
    public String getRemoteFs() {
        return Strings.isNullOrEmpty(remoteFs) ? "/home/jenkins" : remoteFs;
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = remoteFs;
    }

    @Nonnull
    @Restricted(value = NoExternalUse.class) // ancient UI jelly form
    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodePropertiesUI() throws IOException {
        return new DescribableList<>(Jenkins.getActiveInstance().getNodesObject(), getNodeProperties());
    }

    @Restricted(value = NoExternalUse.class) // ancient UI jelly form
    public void setNodePropertiesUI(DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodePropertiesUI) {
        setNodeProperties(nodePropertiesUI);
    }

    @Nonnull
    public List<? extends NodeProperty<?>> getNodeProperties() {
        return nonNull(nodeProperties) ? unmodifiableList(nodeProperties) : emptyList();
    }

    public void setNodeProperties(List<? extends NodeProperty<?>> nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

    /**
     * Id used for counting running slaves
     */
    @Nonnull
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof DockerSlaveConfig)) return false;

        DockerSlaveConfig that = (DockerSlaveConfig) o;

        return new EqualsBuilder()
                .append(numExecutors, that.numExecutors)
                .append(id, that.id)
                .append(labelString, that.labelString)
                .append(launcher, that.launcher)
                .append(remoteFs, that.remoteFs)
                .append(mode, that.mode)
                .append(retentionStrategy, that.retentionStrategy)
                .append(dockerContainerLifecycle, that.dockerContainerLifecycle)
//            .append(nodeProperties, that.nodeProperties)
                .isEquals();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerSlaveConfig> {
        public FormValidation doCheckLabelString(@QueryParameter String labelString) {
            if (isNull(labelString)) {
                return FormValidation.warning("Please specify some label");
            }

            return FormValidation.ok();
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Slave Configuration";
        }
    }
}
