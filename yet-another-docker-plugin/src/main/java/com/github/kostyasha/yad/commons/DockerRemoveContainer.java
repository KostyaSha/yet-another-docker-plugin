package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.RemoveContainerCmdImpl;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;

/**
 * @author Kanstantsin Shautsou
 * @see RemoveContainerCmdImpl
 */
public class DockerRemoveContainer extends AbstractDescribableImpl<DockerRemoveContainer> implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean removeVolumes;
    private boolean force;

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

    public void exec(DockerClient client, String containerId) {
        client.removeContainerCmd(containerId)
                .withRemoveVolumes(removeVolumes)
                .withForce(force)
                .exec();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DockerRemoveContainer that = (DockerRemoveContainer) o;

        return new EqualsBuilder()
                .append(removeVolumes, that.removeVolumes)
                .append(force, that.force)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(removeVolumes)
                .append(force)
                .toHashCode();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerRemoveContainer> {
        @Override
        public String getDisplayName() {
            return "Docker Remove Container";
        }
    }
}
