package com.github.kostyasha.it.utils;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Ports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DockerUtils.class);

    private DockerUtils() {
    }

    public static void ensureContainerRemoved(DockerClient cli, String containerName) {
        LOG.info("Ensuring container '{}' is removed", containerName);
        try {
            cli.removeContainerCmd(containerName)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            LOG.info("Removed container {}", containerName);
        } catch (NotFoundException ignore) {
        }
    }

    public static int getExposedPort(InspectContainerResponse inspect, int targetPort) {
        final Map<ExposedPort, Ports.Binding[]> bindings = inspect.getNetworkSettings().getPorts().getBindings();
        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : bindings.entrySet()) {
            if (entry.getKey().getPort() == targetPort) {
                return Integer.valueOf(entry.getValue()[0].getHostPortSpec());
            }
        }

        throw new IllegalArgumentException("Can't find exposed port for port: " + targetPort);
    }
}
