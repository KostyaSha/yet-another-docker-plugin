package com.github.kostyasha.yad.client;

import com.github.kostyasha.yad.DockerConnector;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerCmdExecConfigBuilderForPlugin {

    private Integer readTimeout;
    private Integer connectTimeout;

    private DockerCmdExecConfigBuilderForPlugin() {
    }

    public static DockerCmdExecConfigBuilderForPlugin builder() {
        return new DockerCmdExecConfigBuilderForPlugin();
    }

    public DockerCmdExecConfigBuilderForPlugin forConnector(DockerConnector connector) {
        readTimeout = connector.getReadTimeout();
        connectTimeout = connector.getConnectTimeout();
        return this;
    }

    public DockerCmdExecConfigBuilderForPlugin withReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public DockerCmdExecConfigBuilderForPlugin withConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public DockerCmdExecConfig build() {
        return new DockerCmdExecConfig(readTimeout, connectTimeout);
    }
}
