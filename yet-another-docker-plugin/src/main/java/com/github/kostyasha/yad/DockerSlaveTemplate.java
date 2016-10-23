package com.github.kostyasha.yad;

import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad_docker_java.com.google.common.base.MoreObjects;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Strings;
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import com.github.kostyasha.yad.launcher.DockerComputerLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * All configuration (jenkins and docker specific) required for launching slave instances.
 */
public class DockerSlaveTemplate implements Describable<DockerSlaveTemplate> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerSlaveTemplate.class);

    private int configVersion = 1;
    /**
     * Unique id of this template configuration. Required for:
     * - hashcode,
     * - cloud counting
     */
    @Nonnull
    private final String id;

    private String labelString = "docker";

    private transient String remoteFsMapping;

    private DockerComputerLauncher launcher = new DockerComputerJNLPLauncher();

    private String remoteFs = "/home/jenkins";

    private int maxCapacity = 10;

    private Node.Mode mode = Node.Mode.EXCLUSIVE;

    private RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(10);

    private int numExecutors = 1;

    /**
     * Bundle class that contains all docker related actions/configs
     */
    private DockerContainerLifecycle dockerContainerLifecycle = new DockerContainerLifecycle();

    private List<NodeProperty<?>> nodeProperties = null;

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    /**
     * Generates new unique ID for new instances.
     */
    public DockerSlaveTemplate() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Custom specified ID. When editing existed UI entry, UI sends it back.
     */
    public DockerSlaveTemplate(@Nonnull String id) throws FormException {
        if (id == null) {
            throw new FormException("Hidden id must not be null", "id");
        }
        this.id = id;
    }

    /**
     * FIXME DescribableList doesn't work with DBS https://gist.github.com/KostyaSha/3414f4f453ea7c7406b4
     */
    @DataBoundConstructor
    public DockerSlaveTemplate(@Nonnull String id, List<NodeProperty<?>> nodePropertiesUI)
            throws FormException {
        this(id);
        setNodeProperties(nodePropertiesUI);
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
        this.labelSet = Label.parse(labelString);
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
        if (getRetentionStrategy() instanceof DockerOnceRetentionStrategy) {
            return 1; // works only with one executor!
        }

        return numExecutors;
    }

    @DataBoundSetter
    public void setRetentionStrategy(RetentionStrategy retentionStrategy) {
        this.retentionStrategy = retentionStrategy;
    }

    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy;
    }

    /**
     * tmp fix for terminating boolean caching
     */
    public RetentionStrategy getRetentionStrategyCopy() {
        if (retentionStrategy instanceof DockerOnceRetentionStrategy) {
            DockerOnceRetentionStrategy onceRetention = (DockerOnceRetentionStrategy) retentionStrategy;
            return new DockerOnceRetentionStrategy(onceRetention.getIdleMinutes());
        }
        return retentionStrategy;
    }

    @DataBoundSetter
    public void setLauncher(DockerComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public DockerComputerLauncher getLauncher() {
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

    public int getMaxCapacity() {
        return maxCapacity;
    }

    @DataBoundSetter
    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Nonnull
    public Set<LabelAtom> getLabelSet() {
        return labelSet != null ? labelSet : Collections.<LabelAtom>emptySet();
    }

    @Nonnull
    @Restricted(value = NoExternalUse.class) // ancient UI jelly form
    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodePropertiesUI() throws IOException {
        return new DescribableList(Jenkins.getActiveInstance().getNodesObject(), getNodeProperties());
    }

    @Restricted(value = NoExternalUse.class) // ancient UI jelly form
    public void setNodePropertiesUI(DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodePropertiesUI) {
        setNodeProperties(nodePropertiesUI);
    }

    @Nonnull
    public List<? extends NodeProperty<?>> getNodeProperties() {
        return nonNull(nodeProperties) ? unmodifiableList(nodeProperties) : emptyList();
    }

    public void setNodeProperties(List<NodeProperty<?>> nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

    /**
     * Initializes data structure that we don't persist.
     */
    public Object readResolve() {
        if (configVersion < 1) {
            if (isNull(nodeProperties)) nodeProperties = new ArrayList<>();
            nodeProperties.add(new DockerNodeProperty("DOCKER_CONTAINER_ID", "JENKINS_CLOUD_ID", "DOCKER_HOST"));
            configVersion = 1;
        }

        // real @Nonnull
        if (mode == null) {
            mode = Node.Mode.NORMAL;
        }

        if (retentionStrategy == null) {
            retentionStrategy = new DockerOnceRetentionStrategy(10);
        }

        try {
            labelSet = Label.parse(labelString); // fails sometimes under debugger
        } catch (Throwable t) {
            LOG.error("Can't parse labels: {}", t);
        }

        return this;
    }

    /**
     * Id used for counting running slaves
     */
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

        if (o == null || getClass() != o.getClass()) return false;

        DockerSlaveTemplate that = (DockerSlaveTemplate) o;

        return new EqualsBuilder()
                .append(maxCapacity, that.maxCapacity)
                .append(numExecutors, that.numExecutors)
                .append(id, that.id)
                .append(labelString, that.labelString)
                .append(launcher, that.launcher)
                .append(remoteFs, that.remoteFs)
                .append(mode, that.mode)
                .append(retentionStrategy, that.retentionStrategy)
                .append(dockerContainerLifecycle, that.dockerContainerLifecycle)
//                .append(nodeProperties, that.nodeProperties)
                .isEquals();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String getShortDescription() {
        return MoreObjects.toStringHelper(this)
                .add("image", dockerContainerLifecycle.getImage())
                .toString();
    }

    public Descriptor<DockerSlaveTemplate> getDescriptor() {
        return (DescriptorImpl) Jenkins.getActiveInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerSlaveTemplate> {
        public FormValidation doCheckLabelString(@QueryParameter String labelString) {
            if (isNull(labelString)) {
                return FormValidation.warning("Please specify some label");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckNumExecutors(@QueryParameter int numExecutors) {
            if (numExecutors > 1) {
                return FormValidation.warning("Experimental, see help");
            } else if (numExecutors < 1) {
                return FormValidation.error("Must be > 0");
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

        public Class getDockerTemplateBase() {
            return DockerCreateContainer.class;
        }
    }
}
