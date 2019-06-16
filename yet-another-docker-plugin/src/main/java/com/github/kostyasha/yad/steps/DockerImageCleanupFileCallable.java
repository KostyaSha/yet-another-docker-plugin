package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.google.common.base.Throwables;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class DockerImageCleanupFileCallable extends MasterToSlaveFileCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerImageCleanupFileCallable.class);
    private static final long serialVersionUID = 1L;

    private final YADockerConnector connector;
    private final TaskListener taskListener;
    private List<String> builtImages;


    public DockerImageCleanupFileCallable(YADockerConnector connector, TaskListener taskListener,
                                          List<String> builtImages) {
        this.connector = connector;
        this.taskListener = taskListener;
        this.builtImages = builtImages;
    }

    @Override
    public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        PrintStream llog = taskListener.getLogger();
        llog.println("Creating connection to docker daemon...");
        try (DockerClient client = connector.getClient()) {
            return invoke(client);
        } catch (Exception ex) {
            Throwables.propagate(ex);
        }

        return null;
    }

    private Void invoke(DockerClient client) {
        PrintStream llog = taskListener.getLogger();
        for (String image : builtImages) {
            if (isNotEmpty(image)) {
                llog.println("Removing built image " + image);
                try {
                    client.removeImageCmd(image)
                            .exec();
                } catch (NotFoundException ex) {
                    LOG.trace("Image '{}' already doesn't exist.", image);
                } catch (Throwable ex) {
                    taskListener.error("Can't remove image" + ex.getMessage());
                    //ignore as it cleanup
                }
            }
        }

//        for (String containerId : containers) {
//            try {
//                client.removeImageCmd(containerId)
//                        .exec();
//                llog.printf("Removed dangling layer image %s.%n", containerId);
//                LOG.debug("Removed dangling layer image '{}'", containerId);
//            } catch (NotFoundException | ConflictException ex) {
//                // ignore
//            } catch (Throwable ex) {
//                taskListener.error("Can't remove dangling layer image " + containerId + ".");
//                LOG.error("Can't remove dangling layer image " + containerId, ex);
//            }
//        }
        return null;
    }
}
