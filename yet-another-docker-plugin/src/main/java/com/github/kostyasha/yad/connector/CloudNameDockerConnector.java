package com.github.kostyasha.yad.connector;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Version;
import hudson.Extension;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import static hudson.util.FormValidation.error;
import static hudson.util.FormValidation.ok;
import static org.apache.commons.lang.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang.builder.ToStringStyle.MULTI_LINE_STYLE;

/**
 * Should be under {@link com.github.kostyasha.yad.connector} package,
 * but it was first class and can't move.
 * Get {@link DockerClient} from existing {@link DockerCloud}
 *
 * @author Kanstantsin Shautsou
 */
public class CloudNameDockerConnector extends YADockerConnector {
    private static final long serialVersionUID = 1L;

    private String cloudName;

    @DataBoundConstructor
    public CloudNameDockerConnector(String cloudName) {
        this.cloudName = cloudName;
    }

    public String getCloudId() {
        return cloudName;
    }

    @CheckForNull
    @Override
    public DockerClient getClient() {
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud instanceof DockerCloud) {
            final DockerCloud dockerCloud = (DockerCloud) cloud;
            return dockerCloud.getClient();
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends YADockerConnectorDescriptor {
        public FormValidation doCheckCloudName(@QueryParameter String cloudName) {
            try {
                final Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
                if (cloud instanceof DockerCloud) {
                    final DockerCloud dockerCloud = (DockerCloud) cloud;
                    Version verResult = dockerCloud.getConnector().getClient().versionCmd().exec();

                    return ok(reflectionToString(verResult, MULTI_LINE_STYLE));
                } else {
                    return FormValidation.error("cloudId '" + cloudName + "' isn't DockerCloud");
                }
            } catch (Throwable t) {
                return error(t, "error");
            }
        }

        public ListBoxModel doFillCloudNameItems() {
            ListBoxModel items = new ListBoxModel();
            Jenkins.getInstance().clouds.getAll(DockerCloud.class)
                .forEach(dockerCloud ->
                    items.add(dockerCloud.getDisplayName())
                );
            return items;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Cloud by Name";
        }
    }
}
