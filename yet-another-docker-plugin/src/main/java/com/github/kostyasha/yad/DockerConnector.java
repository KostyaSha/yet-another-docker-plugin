package com.github.kostyasha.yad;

import com.github.kostyasha.yad.connector.DockerJavaConnector;
import com.github.kostyasha.yad.connector.DockerJavaConnectorDescriptor;
import com.github.kostyasha.yad.connector.NettyDockerJavaConnector;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.common.base.Throwables;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.security.GeneralSecurityException;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.newClientBuilderForConnector;

/**
 * Settings for connecting to docker.
 *
 * @author Kanstantsin Shautsou
 * @deprecated docker-java has different types of connectors with different options.
 */
@Deprecated
public class DockerConnector extends NettyDockerJavaConnector {

    public DockerConnector(String serverUrl) {
        super(serverUrl);
    }

    @Extension
    public static class DescriptorImpl extends DockerJavaConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Docker Deprecated Connector";
        }
    }
}
