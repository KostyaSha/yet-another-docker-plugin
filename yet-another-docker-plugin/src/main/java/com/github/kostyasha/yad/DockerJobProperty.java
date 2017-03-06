package com.github.kostyasha.yad;

import com.github.kostyasha.yad.jobconfig.SlaveJobConfig;
import hudson.Extension;
import hudson.model.Job;
import jenkins.model.OptionalJobProperty;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Property for storing docker specific job settings.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerJobProperty extends OptionalJobProperty<Job<?, ?>> {

    /**
     * Any type of configuration for running job in single container.
     */
    private SlaveJobConfig slaveJobConfig;

    @DataBoundConstructor
    public DockerJobProperty() {
    }

    public SlaveJobConfig getSlaveJobConfig() {
        return slaveJobConfig;
    }

    @DataBoundSetter
    public void setSlaveJobConfig(SlaveJobConfig slaveJobConfig) {
        this.slaveJobConfig = slaveJobConfig;
    }

    @Extension
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return Job.class.isAssignableFrom(jobType);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Job Property";
        }
    }
}
