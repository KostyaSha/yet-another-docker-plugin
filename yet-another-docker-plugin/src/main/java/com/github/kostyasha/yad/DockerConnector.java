package com.github.kostyasha.yad;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.kostyasha.yad.client.ClientBuilderForPlugin;
import com.github.kostyasha.yad.client.ClientConfigBuilderForPlugin;
import com.github.kostyasha.yad.client.DockerCmdExecConfig;
import com.github.kostyasha.yad.client.DockerCmdExecConfigBuilderForPlugin;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Version;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.google.common.base.Preconditions;
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

import static com.github.kostyasha.yad.utils.DockerFunctions.createClient;

/**
 * Settings for connecting to docker.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerConnector implements Describable<DockerConnector> {

    @CheckForNull
    private String serverUrl;

    private int connectTimeout;

    private int readTimeout;

    @CheckForNull
    private String credentialsId;

    @CheckForNull
    private transient DockerClient client;

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

    public int getConnectTimeout() {
        return connectTimeout;
    }

    @DataBoundSetter
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }


    public int getReadTimeout() {
        return readTimeout;
    }

    @DataBoundSetter
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public DockerClient getClient() {
        if (client == null) {
            try {
                client = createClient(this);
            } catch (GeneralSecurityException e) {
                Throwables.propagate(e);
            }
        }

        return client;
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
                            Collections.<DomainRequirement>emptyList());

            return new CredentialsListBoxModel().withEmptySelection()
                    .withMatching(CredentialsMatchers.always(), credentials);
        }

        @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "docker-java uses runtime exceptions")
        public FormValidation doTestConnection(
                @QueryParameter String serverUrl,
                @QueryParameter String credentialsId,
                @QueryParameter String version,
                @QueryParameter int readTimeout,
                @QueryParameter int connectTimeout
        ) throws IOException, ServletException, DockerException {
            try {
                final DockerClientConfig clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                        .forServer(serverUrl, version)
                        .withCredentials(credentialsId)
                        .build();

                final DockerCmdExecConfig execConfig = DockerCmdExecConfigBuilderForPlugin.builder()
                        .withReadTimeout(readTimeout)
                        .withConnectTimeout(connectTimeout)
                        .build();

                DockerClient testClient = ClientBuilderForPlugin.builder()
                        .withDockerClientConfig(clientConfig)
                        .withDockerCmdExecConfig(execConfig)
                        .build();

                Version verResult = testClient.versionCmd().exec();

                return FormValidation.ok("Version = " + verResult.getVersion());
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        @Override
        public String getDisplayName() {
            return "Docker client";
        }
    }
}
