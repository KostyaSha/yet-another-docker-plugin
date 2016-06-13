package com.github.kostyasha.yad;

import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.commons.DockerStopContainer;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import static java.util.Objects.nonNull;

/**
 * Wrapper around all operations required during Cloud slave processing
 * The idea to bundle all docker related actions here.
 * - pull
 * - create
 * - start
 * - cleanup?
 *
 * @author Kanstantsin Shautsou
 */
public class DockerContainerLifecycle extends AbstractDescribableImpl<DockerContainerLifecycle> {

    private String image = "";

    private DockerPullImage pullImage = new DockerPullImage();

    private DockerCreateContainer createContainer = new DockerCreateContainer();

    private DockerStopContainer stopContainer = new DockerStopContainer();

    private DockerRemoveContainer removeContainer = new DockerRemoveContainer();

    @DataBoundConstructor
    public DockerContainerLifecycle() {
    }

    // image
    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    // pull image
    public DockerPullImage getPullImage() {
        return pullImage;
    }

    @DataBoundSetter
    public void setPullImage(DockerPullImage pullImage) {
        this.pullImage = pullImage;
    }

    // createContainer
    public DockerCreateContainer getCreateContainer() {
        return createContainer;
    }

    @DataBoundSetter
    public void setCreateContainer(DockerCreateContainer createContainer) {
        this.createContainer = createContainer;
    }

    // stop container
    public DockerStopContainer getStopContainer() {
        return stopContainer;
    }

    @DataBoundSetter
    public void setStopContainer(DockerStopContainer stopContainer) {
        this.stopContainer = stopContainer;
    }

    // remove container
    public DockerRemoveContainer getRemoveContainer() {
        return removeContainer;
    }

    @DataBoundSetter
    public void setRemoveContainer(DockerRemoveContainer removeContainer) {
        this.removeContainer = removeContainer;
    }

    public Object readResolve() {
        if (nonNull(createContainer)) createContainer.readResolve();

        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DockerContainerLifecycle that = (DockerContainerLifecycle) o;

        return new EqualsBuilder()
                .append(image, that.image)
                .append(pullImage, that.pullImage)
                .append(createContainer, that.createContainer)
                .append(stopContainer, that.stopContainer)
                .append(removeContainer, that.removeContainer)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(image)
                .append(pullImage)
                .append(createContainer)
                .append(stopContainer)
                .append(removeContainer)
                .toHashCode();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerContainerLifecycle> {
        @Override
        public String getDisplayName() {
            return "All Docker Container Settings";
        }
    }
}
