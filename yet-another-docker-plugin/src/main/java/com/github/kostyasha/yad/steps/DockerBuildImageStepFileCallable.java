package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.async.ResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.DockerClientException;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static java.util.Objects.nonNull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerBuildImageStepFileCallable extends MasterToSlaveFileCallable<List<String>> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImageStepFileCallable.class);
    private static final long serialVersionUID = 1L;

    private YADockerConnector connector = null;
    private DockerBuildImage buildImage = new DockerBuildImage();
    private TaskListener taskListener;

    private DockerBuildImageStepFileCallable() {
    }

    public static DockerBuildImageStepFileCallable newDockerBuildImageStepCallable() {
        return new DockerBuildImageStepFileCallable();
    }

    public DockerBuildImageStepFileCallable withConnector(YADockerConnector connector) {
        this.connector = connector;
        return this;
    }

    public DockerBuildImageStepFileCallable withBuildImage(DockerBuildImage buildImage) {
        this.buildImage = buildImage;
        return this;
    }

    public DockerBuildImageStepFileCallable withTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
        return this;
    }

    public List<String> invoke(File f, VirtualChannel channel) throws IOException {
        PrintStream llog = taskListener.getLogger();
        llog.println("Creating connection to docker daemon...");
        try (DockerClient client = connector.getClient()) {
            BuildImageCmd buildImageCmd = client.buildImageCmd();
            buildImage.fillSettings(buildImageCmd);
            llog.println("Pulling image ");
            String imageId = buildImageCmd.exec(new MyBuildImageResultCallback(llog))
                    .awaitImageId();
            llog.println(imageId);
            llog.println("Image tagging during build isn't support atm, no tags applied.");
        } catch (Exception ex) {
            LOG.error("Can't get client", ex);
            throw new IOException("Can't get docker client", ex);
        }

        return null;
    }

    private static class MyBuildImageResultCallback extends ResultCallback.Adapter<BuildResponseItem> {
        private final PrintStream llog;
        private String imageId;
        private DockerClientException error;

        MyBuildImageResultCallback(PrintStream llog) {
            this.llog = llog;
        }

        @Override
        public void onNext(BuildResponseItem item) {
            if (item.isBuildSuccessIndicated()) {
                this.imageId = item.getImageId();
            } else if (item.isErrorIndicated()) {
                this.error = new DockerClientException("Could not build image: " + item.getErrorDetail().getMessage());
            }
            String text = item.getStream();
            if (nonNull(text)) {
                llog.print(text);
                LOG.debug(text);
            }
            super.onNext(item);
        }

        public String awaitImageId() {
            try {
                awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DockerClientException("Interrupted while building image", e);
            }
            return getImageId();
        }

        private String getImageId() {
            if (error != null) {
                throw error;
            }
            if (imageId != null) {
                return imageId;
            }
            throw new DockerClientException("Could not build image");
        }
    }
}
