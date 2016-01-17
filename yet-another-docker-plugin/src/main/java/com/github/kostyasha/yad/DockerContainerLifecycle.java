package com.github.kostyasha.yad;

import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.commons.DockerPullImage;
import com.github.kostyasha.yad.commons.DockerRemoveContainer;
import com.github.kostyasha.yad.commons.DockerStopContainer;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
    public DockerContainerLifecycle setPullImage(DockerPullImage pullImage) {
        this.pullImage = pullImage;
        return this;
    }

    // createContainer
    public DockerCreateContainer getCreateContainer() {
        return createContainer;
    }

    @DataBoundSetter
    public DockerContainerLifecycle setCreateContainer(DockerCreateContainer createContainer) {
        this.createContainer = createContainer;
        return this;
    }

    // stop container
    public DockerStopContainer getStopContainer() {
        return stopContainer;
    }

    @DataBoundSetter
    public DockerContainerLifecycle setStopContainer(DockerStopContainer stopContainer) {
        this.stopContainer = stopContainer;
        return this;
    }

    // remove container
    public DockerRemoveContainer getRemoveContainer() {
        return removeContainer;
    }

    @DataBoundSetter
    public DockerContainerLifecycle setRemoveContainer(DockerRemoveContainer removeContainer) {
        this.removeContainer = removeContainer;
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerContainerLifecycle> {
        @Override
        public String getDisplayName() {
            return "All Docker Container Settings";
        }
    }
}
