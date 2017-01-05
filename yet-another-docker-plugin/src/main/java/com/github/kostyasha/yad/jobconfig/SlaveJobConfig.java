package com.github.kostyasha.yad.jobconfig;

import hudson.DescriptorExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Different container template settings for job.
 *
 * @author Kanstantsin Shautsou
 */
public abstract class SlaveJobConfig extends AbstractDescribableImpl<SlaveJobConfig> {
    @Override
    public SlaveJobConfigDescriptor getDescriptor() {
        return (SlaveJobConfigDescriptor) super.getDescriptor();
    }

    public abstract static class SlaveJobConfigDescriptor extends Descriptor<SlaveJobConfig> {
        public static DescriptorExtensionList<SlaveJobConfig, SlaveJobConfigDescriptor> getAllSlaveJobConfigurationDescriptors() {
            return Jenkins.getInstance().getDescriptorList(SlaveJobConfig.class);
        }
    }
}

