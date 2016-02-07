package com.github.kostyasha.yad.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.KeystoreSSLConfig;
import com.github.kostyasha.yad.other.VariableSSLConfig;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collections;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class ClientConfigBuilderForPlugin {
    private static final Logger LOG = LoggerFactory.getLogger(ClientConfigBuilderForPlugin.class);

    private DockerClientConfig.DockerClientConfigBuilder config = new DockerClientConfig.DockerClientConfigBuilder();

    private ClientConfigBuilderForPlugin() {
    }

    public static ClientConfigBuilderForPlugin dockerClientConfig() {
        return new ClientConfigBuilderForPlugin();
    }

    /**
     * Provides ready to use docker client with information from docker connector
     *
     * @param connector docker connector with info about url, version, creds and timeout
     * @return docker-java client
     */
    public ClientConfigBuilderForPlugin forConnector(DockerConnector connector)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        LOG.debug("Building connection to docker host '{}'", connector.getServerUrl());

        forServer(connector.getServerUrl(), null);

        return withCredentials(connector.getCredentialsId());
    }

    /**
     * Method to setup url and docker-api version. Convenient for test-connection purposes and quick requests
     *
     * @param uri     docker server uri
     * @param version docker-api version
     * @return this builder
     */
    public ClientConfigBuilderForPlugin forServer(String uri, @Nullable String version) {
        config.withUri(URI.create(uri).toString())
                .withVersion(version);
        return this;
    }

    /**
     * Sets username and password or ssl config by credentials id
     *
     * @param credentialsId credentials to find in jenkins
     * @return docker-java client
     */
    public ClientConfigBuilderForPlugin withCredentials(String credentialsId)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (isNotBlank(credentialsId)) {
            Credentials credentials = lookupSystemCredentials(credentialsId);

            if (credentials instanceof CertificateCredentials) {
                CertificateCredentials certificateCredentials = (CertificateCredentials) credentials;
                config.withSSLConfig(new KeystoreSSLConfig(
                        certificateCredentials.getKeyStore(),
                        certificateCredentials.getPassword().getPlainText()
                ));
            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials usernamePasswordCredentials =
                        ((StandardUsernamePasswordCredentials) credentials);

                config.withUsername(usernamePasswordCredentials.getUsername());
                config.withPassword(usernamePasswordCredentials.getPassword().getPlainText());

            } else if (credentials instanceof DockerServerCredentials) {
                final DockerServerCredentials dockerCreds = (DockerServerCredentials) credentials;
                final VariableSSLConfig sslConfig = new VariableSSLConfig(dockerCreds.getClientKey(),
                        dockerCreds.getClientCertificate(), dockerCreds.getServerCaCertificate());

                config.withSSLConfig(sslConfig);
            }
        }
        return this;
    }


    /**
     * Build the config
     */
    public DockerClientConfig build() {
        return config.build();
    }

    /**
     * Shortcut to build an actual client.
     * <p>
     * Consider if you actually want to do this or alternatively
     * build the config then build the client, as if your activity is on a remote
     * node, the client will fail to serialize.
     */
    public DockerClient buildClient() {
        return ClientBuilderForPlugin.builder().withDockerClientConfig(build()).build();
    }

    /**
     * For test purposes mostly
     *
     * @return docker config builder
     */
    /* package */ DockerClientConfig.DockerClientConfigBuilder config() {
        return config;
    }

    /**
     * Util method to find credential by id in jenkins
     *
     * @param credentialsId credentials to find in jenkins
     * @return {@link CertificateCredentials} or {@link StandardUsernamePasswordCredentials} expected
     */
    private static Credentials lookupSystemCredentials(String credentialsId) {
        return firstOrNull(
                lookupCredentials(
                        Credentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()
                ),
                withId(credentialsId)
        );
    }

}
