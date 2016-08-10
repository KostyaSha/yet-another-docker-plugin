package com.github.kostyasha.yad;

import com.github.kostyasha.yad.docker_java.org.apache.http.client.utils.URIBuilder;
import hudson.Extension;
import hudson.model.BuildVariableContributor;
import hudson.model.Executor;
import hudson.model.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * Contribute docker related vars in build.
 * TODO seems this will never satisfy all use cases and dumping inspect json into WS
 * will be more generic solution
 *
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerBuildVariableContributor extends BuildVariableContributor {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildVariableContributor.class);

    @Override
    public void buildVariablesFor(Run build, Map<String, String> variables) {
        final Executor executor = build.getExecutor();
        if (executor != null && executor.getOwner() instanceof DockerComputer) {
            final DockerComputer dockerComputer = (DockerComputer) executor.getOwner();
            variables.put("DOCKER_CONTAINER_ID", dockerComputer.getContainerId());
            variables.put("JENKINS_CLOUD_ID", dockerComputer.getCloudId());
            try {
                //replace http:// and https:// from docker-java to tcp://
                final DockerCloud cloud = dockerComputer.getCloud(); // checkfornull
                if (cloud != null && cloud.getConnector() != null) {
                    final URIBuilder uriBuilder = new URIBuilder(cloud.getConnector().getServerUrl());
                    if (!uriBuilder.getScheme().equals("unix")) {
                        uriBuilder.setScheme("tcp");
                    }
                    variables.put("DOCKER_HOST", uriBuilder.toString());
                }
            } catch (URISyntaxException e) {
                LOG.error("Can't build 'DOCKER_HOST' var: {}", e.getMessage());
            }
        }
    }
}
