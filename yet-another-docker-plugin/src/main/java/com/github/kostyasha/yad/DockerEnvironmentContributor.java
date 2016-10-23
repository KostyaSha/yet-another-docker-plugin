package com.github.kostyasha.yad;

import com.github.kostyasha.yad_docker_java.org.apache.http.client.utils.URIBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Contribute docker related vars in build.
 * will be more generic solution
 *
 * @author Kanstantsin Shautsou
 * @see DockerNodeProperty
 */
@Extension
public class DockerEnvironmentContributor extends EnvironmentContributor {
    private static final Logger LOG = LoggerFactory.getLogger(DockerEnvironmentContributor.class);

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        final Executor executor = run.getExecutor();
        if (executor != null && executor.getOwner() instanceof DockerComputer) {
            final DockerComputer dockerComputer = (DockerComputer) executor.getOwner();

            final DockerNodeProperty dProp = dockerComputer.getNode().getNodeProperties().get(DockerNodeProperty.class);
            if (dProp == null) {
                return;
            }

            if (dProp.isContainerIdCheck()) {
                listener.getLogger().println("[YAD-PLUGIN] Injecting variable: " + dProp.getContainerId());
                envs.put(dProp.getContainerId(), dockerComputer.getContainerId());
            }

            if (dProp.isCloudIdCheck()) {
                listener.getLogger().println("[YAD-PLUGIN] Injecting variable: " + dProp.getCloudId());
                envs.put(dProp.getCloudId(), dockerComputer.getCloudId());
            }

            if (dProp.isDockerHostCheck()) {
                try {
                    //replace http:// and https:// from docker-java to tcp://
                    final DockerCloud cloud = dockerComputer.getCloud(); // checkfornull
                    if (cloud != null && cloud.getConnector() != null) {
                        final URIBuilder uriBuilder = new URIBuilder(cloud.getConnector().getServerUrl());
                        if (!uriBuilder.getScheme().equals("unix")) {
                            uriBuilder.setScheme("tcp");
                        }

                        listener.getLogger().println("[YAD-PLUGIN] Injecting variable: " + dProp.getDockerHost());
                        envs.put(dProp.getDockerHost(), uriBuilder.toString());
                    }
                } catch (URISyntaxException e) {
                    listener.error("Can't make variable: %s", dProp.getDockerHost(), e);
                    LOG.error("Can't make '{}' variable: {}", dProp.getDockerHost(), e);
                }
            }
        }
    }
}
