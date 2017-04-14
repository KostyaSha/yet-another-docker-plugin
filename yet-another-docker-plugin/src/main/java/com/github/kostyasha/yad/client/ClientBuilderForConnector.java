package com.github.kostyasha.yad.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.credentials.DockerDaemonCerts;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.other.VariableSSLConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DefaultDockerClientConfig.Builder;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientImpl;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.KeystoreSSLConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.SSLConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.lang.Validate;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Builds ClientConfig with helper methods that extracts info for plugin routines.
 * After the number of refactoring in docker-java and yad-plugin logic maybe messed.
 */
@SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/docker-java/docker-java/issues/588")
public class ClientBuilderForConnector {
    private static final Logger LOG = LoggerFactory.getLogger(ClientBuilderForConnector.class);

    private DockerCmdExecFactory dockerCmdExecFactory = null;
    private DockerClientConfig clientConfig = null;
    // fallback to builder
    private Builder configBuilder = new Builder();

    private ConnectorType connectorType = null;
    private Integer connectTimeout = null;
    private Integer readTimeout = null;

    private ClientBuilderForConnector() {
    }

    public ClientBuilderForConnector withDockerCmdExecFactory(DockerCmdExecFactory dockerCmdExecFactory) {
        this.dockerCmdExecFactory = dockerCmdExecFactory;
        return this;
    }

    public ClientBuilderForConnector withSslConfig(SSLConfig sslConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (sslConfig == null) {
            configBuilder.withDockerTlsVerify(false);
        } else {
            configBuilder.withCustomSslConfig(sslConfig);
            configBuilder.withDockerTlsVerify(true);
        }

        return this;
    }

    /**
     * Provides ready to use docker client with information from docker connector
     *
     * @param connector docker connector with info about url, version, creds and timeout
     * @return docker-java client
     */
    public ClientBuilderForConnector forConnector(DockerConnector connector)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        LOG.debug("Building connection to docker host '{}'", connector.getServerUrl());
        withCredentialsId(connector.getCredentialsId());
        withConnectorType(connector.getConnectorType());
        withConnectTimeout(connector.getConnectTimeout());

        return forServer(connector.getServerUrl(), connector.getApiVersion());
    }

    public ClientBuilderForConnector withConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
        return this;
    }

    public ClientBuilderForConnector withConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public ClientBuilderForConnector withReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * Method to setup url and docker-api version. Convenient for test-connection purposes and quick requests
     *
     * @param uri     docker server uri
     * @param version docker-api version
     * @return this newClientBuilderForConnector
     */
    public ClientBuilderForConnector forServer(String uri, @Nullable String version) {
        configBuilder.withDockerHost(URI.create(uri).toString())
                .withApiVersion(version);
        return this;
    }

    /**
     * Sets SSLConfig from defined credentials id.
     *
     * @param credentialsId credentials to find in jenkins
     * @return docker-java client
     */
    public ClientBuilderForConnector withCredentialsId(String credentialsId)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (isNotBlank(credentialsId)) {
            withCredentials(lookupSystemCredentials(credentialsId));
        } else {
            withSslConfig(null);
        }

        return this;
    }

    public ClientBuilderForConnector withCredentials(Credentials credentials) throws UnrecoverableKeyException,
        NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (credentials instanceof CertificateCredentials) {
            CertificateCredentials certificateCredentials = (CertificateCredentials) credentials;
            withSslConfig(new KeystoreSSLConfig(
                certificateCredentials.getKeyStore(),
                certificateCredentials.getPassword().getPlainText()
            ));
//            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
//                StandardUsernamePasswordCredentials usernamePasswordCredentials =
//                        ((StandardUsernamePasswordCredentials) credentials);
//
//                dockerClientConfigBuilder.withRegistryUsername(usernamePasswordCredentials.getUsername());
//                dockerClientConfigBuilder.withRegistryPassword(usernamePasswordCredentials.getPassword().getPlainText());
//
        } else if (credentials instanceof DockerServerCredentials) {
            final DockerServerCredentials dockerCreds = (DockerServerCredentials) credentials;

            withSslConfig(new VariableSSLConfig(
                dockerCreds.getClientKey(),
                dockerCreds.getClientCertificate(),
                dockerCreds.getServerCaCertificate()
            ));
        } else if (credentials instanceof DockerDaemonCerts) {
            final DockerDaemonCerts dockerCreds = (DockerDaemonCerts) credentials;

            withSslConfig(new VariableSSLConfig(
                    dockerCreds.getClientKey(),
                    dockerCreds.getClientCertificate(),
                    dockerCreds.getServerCaCertificate()
            ));
        }

        return this;
    }

    public ClientBuilderForConnector withConfigBuilder(Builder configBuilder) {
        this.configBuilder = configBuilder;
        return this;
    }

    public ClientBuilderForConnector withDockerClientConfig(DockerClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        return this;
    }

    public ClientBuilderForConnector withDockerConnector(DockerConnector connector)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        forConnector(connector);

        return this;
    }

    public DockerClient build() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            KeyManagementException {

        if (isNull(dockerCmdExecFactory)) {
            if (connectorType == JERSEY) {
                dockerCmdExecFactory = new JerseyDockerCmdExecFactory();
            } else {
                dockerCmdExecFactory = new NettyDockerCmdExecFactory();
            }
        }

        if (dockerCmdExecFactory instanceof JerseyDockerCmdExecFactory) {
            final JerseyDockerCmdExecFactory jersey = (JerseyDockerCmdExecFactory) dockerCmdExecFactory;
            if (nonNull(connectTimeout)) {
                dockerCmdExecFactory = jersey.withConnectTimeout(connectTimeout);
            }
            if (nonNull(readTimeout)) {
                jersey.withReadTimeout(readTimeout);
            }
        }

        if (isNull(clientConfig)) {
            Validate.notNull(configBuilder, "configBuilder must be set");
            clientConfig = configBuilder.build();
        } else {
            Validate.notNull(clientConfig, "clientConfig must be defined");
        }

        return DockerClientImpl.getInstance(clientConfig)
                .withDockerCmdExecFactory(dockerCmdExecFactory);
    }


    /**
     * Util method to find credential by id in jenkins
     *
     * @param credentialsId credentials to find in jenkins
     * @return {@link CertificateCredentials} or {@link StandardUsernamePasswordCredentials} expected
     */
    public static Credentials lookupSystemCredentials(String credentialsId) {
        return firstOrNull(
                lookupCredentials(
                        Credentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        emptyList()
                ),
                withId(credentialsId)
        );
    }

    /**
     * helper class
     */
    public static ClientBuilderForConnector newClientBuilderForConnector() {
        return new ClientBuilderForConnector();
    }
}
