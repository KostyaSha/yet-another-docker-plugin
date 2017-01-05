package com.github.kostyasha.yad.jobconfig;

import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.connector.DockerCloudConnectorId;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;


/**
 * @author Kanstantsin Shautsou
 */
public class DockerCloudJobConfig extends SlaveJobConfig {

    private DockerCloudConnectorId connector;

    private DockerSlaveTemplate template;

    @DataBoundConstructor
    public DockerCloudJobConfig() {
    }

    public DockerCloudConnectorId getConnector() {
        return connector;
    }

    @DataBoundSetter
    public void setConnector(DockerCloudConnectorId connector) {
        this.connector = connector;
    }

    public DockerSlaveTemplate getTemplate() {
        return template;
    }

    @DataBoundSetter
    public void setTemplate(DockerSlaveTemplate template) {
        this.template = template;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveJobConfigDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Existing cloud";
        }
    }
}
