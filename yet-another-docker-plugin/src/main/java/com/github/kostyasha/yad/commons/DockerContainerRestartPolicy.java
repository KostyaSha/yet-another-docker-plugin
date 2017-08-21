package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.RestartPolicy;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import java.util.Locale;

import static java.util.Objects.isNull;
import static org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerContainerRestartPolicy extends AbstractDescribableImpl<DockerContainerRestartPolicy> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerRestartPolicy.class);

    @CheckForNull
    private DockerContainerRestartPolicyName policyName = DockerContainerRestartPolicyName.NO;

    private Integer maximumRetryCount = 0;

    @DataBoundConstructor
    public DockerContainerRestartPolicy(DockerContainerRestartPolicyName policyName, Integer maximumRetryCount) {
        this.policyName = policyName;
        this.maximumRetryCount = maximumRetryCount;
    }

    @CheckForNull
    public DockerContainerRestartPolicyName getPolicyName() {
        return policyName;
    }

    public void setPolicyName(DockerContainerRestartPolicyName policyName) {
        this.policyName = policyName;
    }

    @CheckForNull
    public Integer getMaximumRetryCount() {
        return maximumRetryCount;
    }

    public void setMaximumRetryCount(Integer maximumRetryCount) {
        this.maximumRetryCount = maximumRetryCount;
    }

    /**
     * runtime ABI dependency on docker-java
     */
    @CheckForNull
    public RestartPolicy getRestartPolicy() {
        if (isNull(policyName)) return null;

        return RestartPolicy.parse(String.format("%s:%d",
                policyName.toString().toLowerCase(Locale.ENGLISH).replace("_", "-"), getMaximumRetryCount()));
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerContainerRestartPolicy> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Container Restart Policy";
        }
    }
}
