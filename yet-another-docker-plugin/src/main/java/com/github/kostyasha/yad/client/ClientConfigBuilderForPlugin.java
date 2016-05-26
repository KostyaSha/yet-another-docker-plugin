package com.github.kostyasha.yad.client;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class ClientConfigBuilderForPlugin {
    private static final Logger LOG = LoggerFactory.getLogger(ClientConfigBuilderForPlugin.class);

    private DockerClientConfig.DockerClientConfigBuilder config = new DockerClientConfig.DockerClientConfigBuilder();

    private ClientConfigBuilderForPlugin() {
    }

    public static ClientConfigBuilderForPlugin newClientConfigBuilder() {
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
        return forServer(connector.getServerUrl(), connector.getApiVersion());
    }

    /**
     * Method to setup url and docker-api version. Convenient for test-connection purposes and quick requests
     *
     * @param uri     docker server uri
     * @param version docker-api version
     * @return this newClientBuilderForConnector
     */
    public ClientConfigBuilderForPlugin forServer(String uri, @Nullable String version) {
        config.withDockerHost(URI.create(uri).toString())
                .withApiVersion(version);
        return this;
    }


    /**
     * Build the config
     */
    public DockerClientConfig build() {
        return config.build();
    }


    /**
     * For test purposes mostly
     *
     * @return docker config newClientBuilderForConnector
     */
    /* package */ DockerClientConfig.DockerClientConfigBuilder config() {
        return config;
    }

}
