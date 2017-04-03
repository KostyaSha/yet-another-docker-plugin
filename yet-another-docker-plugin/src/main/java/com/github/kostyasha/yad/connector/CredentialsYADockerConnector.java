package com.github.kostyasha.yad.connector;

import com.cloudbees.plugins.credentials.Credentials;
import com.github.kostyasha.yad.client.ClientBuilderForConnector;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.SSLConfig;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static com.github.kostyasha.yad.client.ClientBuilderForConnector.newClientBuilderForConnector;
import static com.github.kostyasha.yad.other.ConnectorType.NETTY;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Connector from Credentials.
 *
 * @author Kanstantsin Shautsou
 */
public class CredentialsYADockerConnector extends YADockerConnector {

    @CheckForNull
    private String serverUrl;

    @CheckForNull
    private String apiVersion;

    private transient Boolean tlsVerify;

    @CheckForNull
    private Credentials credentials = null;

    @CheckForNull
    private transient DockerClient client = null;

    private ConnectorType connectorType = NETTY;

    @CheckForNull
    private Integer connectTimeout;

    @CheckForNull
    private Integer readTimeout;

    @CheckForNull
    private SSLConfig sslConfig;

    public CredentialsYADockerConnector() {
    }

    @CheckForNull
    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public CredentialsYADockerConnector withConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
        return this;
    }

    @CheckForNull
    public String getServerUrl() {
        return serverUrl;
    }

    public CredentialsYADockerConnector withServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    @CheckForNull
    public String getApiVersion() {
        return apiVersion;
    }

    public CredentialsYADockerConnector withApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    @CheckForNull
    public Boolean getTlsVerify() {
        return tlsVerify;
    }

    public CredentialsYADockerConnector withTlsVerify(Boolean tlsVerify) {
        this.tlsVerify = tlsVerify;
        return this;
    }

    @CheckForNull
    public Credentials getCredentials() {
        return credentials;
    }

    public CredentialsYADockerConnector withCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public CredentialsYADockerConnector withClient(DockerClient client) {
        this.client = client;
        return this;
    }

    @CheckForNull
    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public CredentialsYADockerConnector withConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @CheckForNull
    public SSLConfig getSslConfig() {
        return sslConfig;
    }

    public CredentialsYADockerConnector withSslConfig(SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
        return this;
    }

    @CheckForNull
    public Integer getReadTimeout() {
        return readTimeout;
    }

    public CredentialsYADockerConnector withReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Nonnull
    @Override
    public DockerClient getClient() throws UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException,
            KeyManagementException {

        if (isNull(client)) {
            DefaultDockerClientConfig.Builder configBuilder = new DefaultDockerClientConfig.Builder()
                    .withApiVersion(apiVersion)
                    .withDockerTlsVerify(tlsVerify)
                    .withDockerHost(serverUrl);

            ClientBuilderForConnector clientBuilder = newClientBuilderForConnector()
                    .withConfigBuilder(configBuilder)
                    .withConnectorType(connectorType);
            if (nonNull(credentials)) {
                clientBuilder.withCredentials(credentials);
            }

            if (nonNull(sslConfig)) {
                clientBuilder.withSslConfig(sslConfig);
            }

            clientBuilder.withConnectTimeout(connectTimeout)
                    .withReadTimeout(readTimeout);

            final DockerClient newClient = clientBuilder.build();

            newClient.versionCmd().exec();
            client = newClient;
        }

        return client;
    }


    public static class DescriptorImpl extends YADockerConnectorDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Connector from Credentials";
        }
    }
}
