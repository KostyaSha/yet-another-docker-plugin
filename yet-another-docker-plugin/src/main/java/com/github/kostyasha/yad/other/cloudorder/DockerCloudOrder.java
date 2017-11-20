package com.github.kostyasha.yad.other.cloudorder;

import com.github.kostyasha.yad.DockerCloud;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class DockerCloudOrder extends AbstractDescribableImpl<DockerCloudOrder>
        implements ExtensionPoint {

    /**
     * List of clouds that will be used for provisioning attempt one by one.
     */
    @Nonnull
    public abstract List<DockerCloud> getDockerClouds(Label label);

    @Override
    public DockerCloudOrderDescriptor getDescriptor() {
        return (DockerCloudOrderDescriptor) super.getDescriptor();
    }

    public abstract static class DockerCloudOrderDescriptor extends Descriptor<DockerCloudOrder> {
        public static DescriptorExtensionList<DockerCloudOrder, DockerCloudOrderDescriptor> allDockerCloudOrderDescriptors() {
            return Jenkins.getInstance().getDescriptorList(DockerCloudOrder.class);
        }
    }

}
