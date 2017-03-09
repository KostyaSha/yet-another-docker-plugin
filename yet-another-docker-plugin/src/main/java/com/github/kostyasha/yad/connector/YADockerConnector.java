package com.github.kostyasha.yad.connector;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Different connectors to docker.
 * DockerConnector appeared first so can't rename and YAD become parent.
 *
 * @author Kanstantsin Shautsou
 */
public abstract class YADockerConnector extends AbstractDescribableImpl<YADockerConnector> implements ExtensionPoint {

    public abstract DockerClient getClient() throws Exception;

    @Override
    public YADockerConnectorDescriptor getDescriptor() {
        return (YADockerConnectorDescriptor) super.getDescriptor();
    }

    public abstract static class YADockerConnectorDescriptor extends Descriptor<YADockerConnector> {
        public static DescriptorExtensionList<YADockerConnector, YADockerConnectorDescriptor> allDockerConnectorDescriptors() {
            return Jenkins.getInstance().getDescriptorList(YADockerConnector.class);
        }
    }
}
