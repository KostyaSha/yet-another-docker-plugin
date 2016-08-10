package com.github.kostyasha.yad;

import com.github.kostyasha.yad.docker_java.org.apache.http.client.utils.URIBuilder;
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
 * TODO seems this will never satisfy all use cases and dumping inspect json into WS
 * will be more generic solution
 *
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerBuildVariableContributor extends EnvironmentContributor {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildVariableContributor.class);

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        final Executor executor = run.getExecutor();
        if (executor != null && executor.getOwner() instanceof DockerComputer) {
            final DockerComputer dockerComputer = (DockerComputer) executor.getOwner();
            listener.getLogger().println("[YAD-PLUGIN] Injecting DOCKER_CONTAINER_ID variable.");
            envs.put("DOCKER_CONTAINER_ID", dockerComputer.getContainerId());
            listener.getLogger().println("[YAD-PLUGIN] Injecting JENKINS_CLOUD_ID variable.");
            envs.put("JENKINS_CLOUD_ID", dockerComputer.getCloudId());
            try {
                //replace http:// and https:// from docker-java to tcp://
                final DockerCloud cloud = dockerComputer.getCloud(); // checkfornull
                if (cloud != null && cloud.getConnector() != null) {
                    final URIBuilder uriBuilder = new URIBuilder(cloud.getConnector().getServerUrl());
                    if (!uriBuilder.getScheme().equals("unix")) {
                        uriBuilder.setScheme("tcp");
                    }
                    listener.getLogger().println("[YAD-PLUGIN] DOCKER_HOST variable.");
                    envs.put("DOCKER_HOST", uriBuilder.toString());
                }
            } catch (URISyntaxException e) {
                listener.error("Can't build 'DOCKER_HOST' variable: {}", e.getMessage());
                LOG.error("Can't build 'DOCKER_HOST' var: {}", e.getMessage());
            }
        }
    }
}
