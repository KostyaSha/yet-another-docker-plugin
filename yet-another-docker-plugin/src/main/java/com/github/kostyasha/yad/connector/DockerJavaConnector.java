package com.github.kostyasha.yad.connector;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.google.common.base.Preconditions;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.StringUtils;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringBuilder;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringStyle;
import hudson.model.AbstractDescribableImpl;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;

import static com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * @author Kanstantsin Shautsou
 */
public abstract class DockerJavaConnector extends AbstractDescribableImpl<DockerJavaConnector> {

    @CheckForNull
    protected String serverUrl;

    @CheckForNull
    protected String apiVersion = null;

    protected Boolean tlsVerify = true;

    @CheckForNull
    protected String credentialsId = null;

    @CheckForNull
    protected transient DockerClient client = null;

    @DataBoundConstructor
    public DockerJavaConnector(String serverUrl) {
        setServerUrl(serverUrl);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        Preconditions.checkNotNull(serverUrl);
        this.serverUrl = serverUrl;
    }

    @CheckForNull
    public abstract DockerClient getClient();

    @CheckForNull
    public String getApiVersion() {
        return apiVersion;
    }

    @DataBoundSetter
    public void setApiVersion(String apiVersion) {
        this.apiVersion = StringUtils.trimToNull(apiVersion);
    }

    public Boolean getTlsVerify() {
        return tlsVerify;
    }

    @DataBoundSetter
    public void setTlsVerify(Boolean tlsVerify) {
        this.tlsVerify = tlsVerify;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public void testConnection() {
        getClient().versionCmd().exec();
    }

    public Object readResolve() {
        if (serverUrl != null) {
            if (serverUrl.startsWith("http")) {
                serverUrl = serverUrl.replace("http", "tcp");
            } else if (serverUrl.startsWith("https")) {
                serverUrl = serverUrl.replace("https", "tcp");
                tlsVerify = true;
            }
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    @Override
    public DockerJavaConnectorDescriptor getDescriptor() {
        return (DockerJavaConnectorDescriptor) super.getDescriptor();
    }

}
