package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.StopContainerCmdImpl;
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

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * @author Kanstantsin Shautsou
 * @see StopContainerCmdImpl
 */
@DockerCmd
public class DockerStopContainer extends AbstractDescribableImpl<DockerStopContainer> implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(DockerStopContainer.class);
    private static final long serialVersionUID = 1L;

    private int timeout = 10;

    @CheckForNull
    private YADockerConnector connector; // alternative connector

    @DataBoundConstructor
    public DockerStopContainer() {
    }

    public int getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @CheckForNull
    public YADockerConnector getConnector() {
        return connector;
    }

    @DataBoundSetter
    public void setConnector(@CheckForNull YADockerConnector connector) {
        this.connector = connector;
    }

    public void exec(DockerClient client, @Nonnull String containerId) throws Exception {
        if (nonNull(connector)) {
            try (DockerClient altClient = connector.getClient()) {
                if (nonNull(altClient)) {
                    LOG.debug("Using alternative client {} for {}", client, containerId);
                    execInternal(altClient, containerId);
                    return;
                }
            }
        }

        execInternal(client, containerId);
    }

    private void execInternal(DockerClient client, @Nonnull String containerId) {
        if (isNull(client)) {
            throw new RuntimeException("DockerClient is null.");
        }
        client.stopContainerCmd(containerId)
                .withTimeout(timeout)
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
    public static class DescriptorImpl extends Descriptor<DockerStopContainer> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Stop Container";
        }
    }
}
