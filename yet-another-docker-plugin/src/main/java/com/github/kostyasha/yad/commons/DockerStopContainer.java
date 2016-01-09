package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.StopContainerCmdImpl;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;

/**
 * @author Kanstantsin Shautsou
 * @see StopContainerCmdImpl
 */
public class DockerStopContainer extends AbstractDescribableImpl<DockerStopContainer> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int timeout = 10;

    @DataBoundConstructor
    public DockerStopContainer() {
    }

    public int getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public DockerStopContainer setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public void exec(DockerClient client, String containerId) {
        client.stopContainerCmd(containerId)
                .withTimeout(timeout)
                .exec();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerStopContainer> {
        @Override
        public String getDisplayName() {
            return "Docker Stop Container";
        }
    }
}
