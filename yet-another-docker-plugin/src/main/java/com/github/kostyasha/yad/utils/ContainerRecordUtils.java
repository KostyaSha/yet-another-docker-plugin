package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad_docker_java.com.fasterxml.jackson.databind.util.StdDateFormat;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * @author Kanstantsin Shautsou
 */
public class ContainerRecordUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerRecordUtils.class);

    private ContainerRecordUtils() {
    }

    public static ContainerRecord createRecordFor(DockerComputer computer) throws ParseException, IOException {

        final DockerCloud cloud = computer.getCloud();

        if (isNull(cloud)) {
            throw new IOException("Cloud must be not null");
        }

        final String containerId = computer.getContainerId();

        final String host = cloud.getConnector().getServerUrl();

        final DockerClient client = cloud.getClient();
        final InspectContainerResponse containerInspect = client.inspectContainerCmd(containerId).exec();

        final Date date = new StdDateFormat().parse(containerInspect.getCreated());
        final long created = date.getTime();
        final String containerName = containerInspect.getName();
        final String imageId = containerInspect.getImageId();

        final Map<String, String> tags = Collections.emptyMap();

        return new ContainerRecord(host, containerId, imageId, containerName, created, tags);
    }

    public static void attachFacet(Run<?, ?> run, TaskListener listener) {
        final Executor executor = run.getExecutor();
        if (executor == null) {
            return;
        }

        final Computer owner = executor.getOwner();
        DockerComputer dockerComputer;
        if (owner instanceof DockerComputer) {
            dockerComputer = (DockerComputer) owner;
            try {
                DockerFingerprints.addRunFacet(
                        createRecordFor(dockerComputer),
                        run
                );
            } catch (IOException | ParseException e) {
                listener.error("Can't add Docker fingerprint to run.");
                LOG.error("Can't add fingerprint to run {}", run, e);
            }
        }


    }
}
