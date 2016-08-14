package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.LogConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.LogConfig.LoggingType;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.EqualsBuilder;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.HashCodeBuilder;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringBuilder;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringStyle;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.util.Map;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerLogConfig extends AbstractDescribableImpl<DockerLogConfig> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerLogConfig.class);

    @CheckForNull
    private LoggingType loggingType = LoggingType.DEFAULT;

    @CheckForNull
    private Map<String, String> config = null;

    @DataBoundConstructor
    public DockerLogConfig() {
    }


    @CheckForNull
    public LogConfig.LoggingType getLoggingType() {
        return loggingType;
    }

    @DataBoundSetter
    public void setLoggingType(LoggingType loggingType) {
        this.loggingType = loggingType;
    }

    @CheckForNull
    public Map<String, String> getConfig() {
        return config;
    }

    @DataBoundSetter
    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    @CheckForNull
    public LogConfig getLogConfig() {
        return new LogConfig(getLoggingType(), getConfig());
    }


    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
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
    public static class DescriptorImpl extends Descriptor<DockerLogConfig> {

        @Override
        public String getDisplayName() {
            return "Create Container Log Config";
        }
    }
}
