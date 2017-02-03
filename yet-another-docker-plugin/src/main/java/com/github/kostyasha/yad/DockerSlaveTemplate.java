package com.github.kostyasha.yad;

import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.launcher.DockerComputerLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import com.github.kostyasha.yad_docker_java.com.google.common.base.MoreObjects;
import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;

/**
 * All configuration (jenkins and docker specific) required for launching slave instances.
 */
public class DockerSlaveTemplate extends DockerSlaveConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DockerSlaveTemplate.class);

    private int maxCapacity = 10;

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    /**
     * Generates new unique ID for new instances.
     */
    public DockerSlaveTemplate() {
        super();
    }

    /**
     * Custom specified ID. When editing existed UI entry, UI sends it back.
     */
    public DockerSlaveTemplate(@Nonnull String id) throws FormException {
        super(id);
        if (isNull(id)) {
            throw new FormException("Hidden id must not be null", "id");
        }
    }

    /**
     * FIXME DescribableList doesn't work with DBS https://gist.github.com/KostyaSha/3414f4f453ea7c7406b4
     */
    @DataBoundConstructor
    public DockerSlaveTemplate(@Nonnull String id, List<? extends NodeProperty<?>> nodePropertiesUI)
        throws FormException {
        this(id);
        setNodeProperties(nodePropertiesUI);
    }


    @DataBoundSetter
    public void setLabelString(String labelString) {
        super.setLabelString(labelString);
        this.labelSet = Label.parse(labelString);
    }

    public int getNumExecutors() {
        if (getRetentionStrategy() instanceof DockerOnceRetentionStrategy) {
            return 1; // works only with one executor!
        }

        return numExecutors;
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

    public int getMaxCapacity() {
        return maxCapacity;
    }

    @DataBoundSetter
    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Nonnull
    public Set<LabelAtom> getLabelSet() {
        return labelSet != null ? labelSet : Collections.emptySet();
    }

    @Override
    public DockerComputerLauncher getLauncher() {
        return (DockerComputerLauncher) super.getLauncher();
    }

    /**
     * Initializes data structure that we don't persist.
     */
    public Object readResolve() {
        // real @Nonnull
        if (mode == null) {
            mode = Node.Mode.NORMAL;
        }

        if (retentionStrategy == null) {
            retentionStrategy = new DockerOnceRetentionStrategy(10);
        }

        try {
            labelSet = Label.parse(getLabelString()); // fails sometimes under debugger
        } catch (Throwable t) {
            LOG.error("Can't parse labels: {}", t);
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DockerSlaveTemplate that = (DockerSlaveTemplate) o;

        return new EqualsBuilder()
            .appendSuper(true)
            .append(maxCapacity, that.maxCapacity)
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends DockerSlaveConfig.DescriptorImpl {

        public FormValidation doCheckNumExecutors(@QueryParameter int numExecutors) {
            if (numExecutors > 1) {
                return FormValidation.warning("Experimental, see help");
            } else if (numExecutors < 1) {
                return FormValidation.error("Must be > 0");
            }
            return FormValidation.ok();
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

        public Class getDockerTemplateBase() {
            return DockerCreateContainer.class;
        }
    }
}
