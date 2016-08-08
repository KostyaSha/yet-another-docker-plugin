package com.github.kostyasha.yad.connector;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.exception.DockerException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Version;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringBuilder;
import com.google.common.base.Throwables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.newClientBuilderForConnector;
import static com.github.kostyasha.yad.docker_java.org.apache.commons.lang.builder.ToStringStyle.MULTI_LINE_STYLE;

/**
 * @author Kanstantsin Shautsou
 */
public class JerseyDockerJavaConnector extends DockerJavaConnector {

    @DataBoundConstructor
    public JerseyDockerJavaConnector(String serverUrl) {
        super(serverUrl);
    }

    @Override
    public DockerClient getClient() {
        if (client == null) {
            try {
                client = newClientBuilderForConnector(new JerseyDockerCmdExecFactory())
                        .withDockerConnector(this)
                        .build();
            } catch (GeneralSecurityException e) {
                Throwables.propagate(e);
            }
        }

        return client;
    }

    @Extension
    public static class DescriptorImpl extends DockerJavaConnectorDescriptor {
        @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "docker-java uses runtime exceptions")
        public FormValidation doTestConnection(
                @QueryParameter String serverUrl,
                @QueryParameter String apiVersion,
                @QueryParameter Boolean tlsVerify,
                @QueryParameter String credentialsId
        ) throws IOException, ServletException, DockerException {
            try {
                DefaultDockerClientConfig.Builder configBuilder = new DefaultDockerClientConfig.Builder()
                        .withApiVersion(apiVersion)
                        .withDockerHost(serverUrl)
                        .withDockerTlsVerify(tlsVerify);

                final DockerClient testClient = newClientBuilderForConnector(new JerseyDockerCmdExecFactory())
                        .withConfigBuilder(configBuilder)
                        .withCredentials(credentialsId)
                        .build();

                Version verResult = testClient.versionCmd().exec();

                return FormValidation.ok(ToStringBuilder.reflectionToString(verResult, MULTI_LINE_STYLE));
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        @Override
        public String getDisplayName() {
            return "Netty Docker Connector";
        }
    }
}
