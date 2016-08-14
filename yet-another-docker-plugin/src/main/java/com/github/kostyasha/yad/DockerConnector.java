package com.github.kostyasha.yad;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.exception.DockerException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Version;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.RemoteApiVersion;
import com.github.kostyasha.yad.docker_java.com.google.common.base.Preconditions;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.StringUtils;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.utils.CredentialsListBoxModel;
import com.google.common.base.Throwables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.newClientBuilderForConnector;
import static com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.RemoteApiVersion.VERSION_1_24;
import static com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.RemoteApiVersion.parseConfig;
import static com.github.kostyasha.yad.other.ConnectorType.NETTY;
import static hudson.util.FormValidation.ok;
import static hudson.util.FormValidation.warning;
import static org.apache.commons.lang.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang.builder.ToStringStyle.MULTI_LINE_STYLE;

/**
 * Settings for connecting to docker.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerConnector implements Describable<DockerConnector> {

    @CheckForNull
    private String serverUrl;

    @CheckForNull
    private String apiVersion = "1.23";

    private transient Boolean tlsVerify;

    @CheckForNull
    private String credentialsId = null;

    @CheckForNull
    private transient DockerClient client = null;

    private ConnectorType connectorType = NETTY;

    @DataBoundConstructor
    public DockerConnector(String serverUrl) {
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
    public String getApiVersion() {
        return apiVersion;
    }

    @DataBoundSetter
    public void setApiVersion(String apiVersion) {
        this.apiVersion = StringUtils.trimToNull(apiVersion);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    /**
     * @see #connectorType
     */
    @CheckForNull
    public ConnectorType getConnectorType() {
        return connectorType;
    }

    @DataBoundSetter
    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public DockerClient getClient() {
        if (client == null) {
            try {
                client = newClientBuilderForConnector()
                        .withDockerConnector(this)
                        .build();
            } catch (GeneralSecurityException e) {
                Throwables.propagate(e);
            }
        }

        return client;
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
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DockerConnector that = (DockerConnector) o;

        return new EqualsBuilder()
                .append(serverUrl, that.serverUrl)
                .append(apiVersion, that.apiVersion)
                .append(credentialsId, that.credentialsId)
                .append(tlsVerify, that.tlsVerify)
                .append(connectorType, that.connectorType)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(serverUrl)
                .append(apiVersion)
                .append(credentialsId)
                .append(tlsVerify)
                .append(connectorType)
                .toHashCode();
    }

    @Override
    public Descriptor<DockerConnector> getDescriptor() {
        return (DescriptorImpl) Jenkins.getActiveInstance().getDescriptor(DockerConnector.class);
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<DockerConnector> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            List<StandardCredentials> credentials =
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, context, ACL.SYSTEM,
                            Collections.emptyList());

            return new CredentialsListBoxModel()
                    .withEmptySelection()
                    .withMatching(CredentialsMatchers.always(), credentials);
        }

        @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "docker-java uses runtime exceptions")
        public FormValidation doTestConnection(
                @QueryParameter String serverUrl,
                @QueryParameter String apiVersion,
                @QueryParameter String credentialsId,
                @QueryParameter ConnectorType connectorType
        ) throws IOException, ServletException, DockerException {
            try {
                DefaultDockerClientConfig.Builder configBuilder = new DefaultDockerClientConfig.Builder()
                        .withApiVersion(apiVersion)
                        .withDockerHost(serverUrl);

                final DockerClient testClient = newClientBuilderForConnector()
                        .withConfigBuilder(configBuilder)
                        .withConnectorType(connectorType)
                        .withCredentials(credentialsId)
                        .build();

                Version verResult = testClient.versionCmd().exec();

                return ok(reflectionToString(verResult, MULTI_LINE_STYLE));
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        public FormValidation doCheckApiVersion(@QueryParameter String apiVersion) {
            if (StringUtils.isEmpty(apiVersion)) {
                return ok();
            }
            try {
                final RemoteApiVersion rav = parseConfig(apiVersion);
                if (rav.isGreaterOrEqual(VERSION_1_24)) {
                    return warning("Latest tested version 1.23. Current configuration may not work correctly");
                } else if (!rav.isGreaterOrEqual(RemoteApiVersion.VERSION_1_19)) {
                    return warning("Unknown API version, may not work with plugin!");
                }
            } catch (Exception ex) {
                return FormValidation.error("Can't parse api version.", ex);
            }

            return ok();
        }

        @Override
        public String getDisplayName() {
            return "Docker Connector";
        }
    }
}
