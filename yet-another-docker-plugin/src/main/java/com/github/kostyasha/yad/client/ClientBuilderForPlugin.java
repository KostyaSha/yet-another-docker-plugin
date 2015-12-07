package com.github.kostyasha.yad.client;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientBuilder;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import org.apache.commons.lang.Validate;

/**
 * Builds ClientConfig with helper methods that extracts info for plugin routines.
 */
public class ClientBuilderForPlugin {

    private DockerClientConfig config;
    private DockerCmdExecFactory dockerCmdExecFactory;

    private ClientBuilderForPlugin() {
    }

    private ClientBuilderForPlugin(DockerClientConfig config, DockerCmdExecFactory dockerCmdExecFactory) {
        this.config = config;
        this.dockerCmdExecFactory = dockerCmdExecFactory;
    }

    public ClientBuilderForPlugin withDockerCmdExecFactory(DockerCmdExecFactory dockerCmdExecFactory) {
        this.dockerCmdExecFactory = dockerCmdExecFactory;
        return this;
    }

    public ClientBuilderForPlugin withDockerCmdExecConfig(DockerCmdExecConfig config) {
        this.dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
                .withReadTimeout(config.getReadTimeoutMillis())
                .withConnectTimeout(config.getConnectTimeout());
        return this;
    }

    public static ClientBuilderForPlugin builder() {
        return new ClientBuilderForPlugin();
    }

    public ClientBuilderForPlugin withDockerClientConfig(DockerClientConfig clientConfig) {
        this.config = clientConfig;
        return this;
    }

    public DockerClient build() {
        Validate.notNull(config, "ClientConfig must be set");
        Validate.notNull(dockerCmdExecFactory, "DockerCmdExecFactory must be set");

        return DockerClientBuilder.getInstance(config)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();
    }
}
