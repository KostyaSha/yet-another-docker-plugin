package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.RemoveContainerCmdImpl;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * @author Kanstantsin Shautsou
 * @see RemoveContainerCmdImpl
 */
@DockerCmd
public class DockerRemoveContainer extends AbstractDescribableImpl<DockerRemoveContainer> implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRemoveContainer.class);
    private static final long serialVersionUID = 1L;

    private boolean removeVolumes = true;
    private boolean force;

    @CheckForNull
    private YADockerConnector connector;

    @DataBoundConstructor
    public DockerRemoveContainer() {
    }

    public boolean isRemoveVolumes() {
        return removeVolumes;
    }

    @DataBoundSetter
    public void setRemoveVolumes(boolean removeVolumes) {
        this.removeVolumes = removeVolumes;
    }

    public boolean isForce() {
        return force;
    }

    @DataBoundSetter
    public void setForce(boolean force) {
        this.force = force;
    }

    @CheckForNull
    public YADockerConnector getConnector() {
        return connector;
    }

    public void setConnector(YADockerConnector connector) {
        this.connector = connector;
    }

    public void exec(DockerClient client, String containerId) throws Exception {
        if (nonNull(connector)) {
            try (DockerClient altClient = connector.getClient()) {
                if (nonNull(altClient)) {
                    LOG.debug("Using alternative client {}, for '{}'.", altClient, containerId);
                    execInternal(altClient, containerId);
                    return;
                }
            }
        }
        execInternal(client, containerId);
    }

    private void execInternal(DockerClient client, String containerId) {
        client.removeContainerCmd(containerId)
                .withRemoveVolumes(removeVolumes)
                .withForce(force)
                .exec();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<DockerRemoveContainer> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Remove Container";
        }
    }
}
