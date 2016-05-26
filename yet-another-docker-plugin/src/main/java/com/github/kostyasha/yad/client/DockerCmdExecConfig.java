package com.github.kostyasha.yad.client;

import com.github.kostyasha.yad.DockerConnector;

import java.io.Serializable;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Serializable object that store options for {@see com.github.kostyasha.yad.docker_java.jaxrs.DockerCmdExecFactoryImpl}
 * Required for building DockerClient on slave side
 * TODO create analogue in docker-java https://github.com/docker-java/docker-java/pull/379
 *
 * @author Kanstantsin Shautsou
 */
public class DockerCmdExecConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer readTimeout; //sec
    private Integer connectTimeout; //sec

    private DockerCmdExecConfig() {
    }

    public static DockerCmdExecConfig newDockerCmdExecConfig() {
        return new DockerCmdExecConfig();
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Helper methods that returns sec in ms for @see com.github.kostyasha.yad.docker_java.jaxrs.DockerCmdExecFactoryImpl
     */
    public Integer getReadTimeoutMillis() {
        if (readTimeout != null) {
            return (int) SECONDS.toMillis(readTimeout);
        } else {
            return null;
        }
    }

    public Integer getConnectTimeoutMillis() {
        if (connectTimeout != null) {
            return (int) SECONDS.toMillis(readTimeout);
        } else {
            return null;
        }
    }

    /**
     * @see #readTimeout
     */
    public DockerCmdExecConfig withReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * @see #connectTimeout
     */
    public DockerCmdExecConfig withConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public DockerCmdExecConfig forConnector(DockerConnector connector) {
        readTimeout = connector.getReadTimeout();
        connectTimeout = connector.getConnectTimeout();
        return this;
    }

}
