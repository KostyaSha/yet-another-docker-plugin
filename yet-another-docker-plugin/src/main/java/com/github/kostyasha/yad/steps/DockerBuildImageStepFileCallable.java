package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang.StringUtils;
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
            String imageId = buildImageCmd.exec(
                    new BuildImageResultCallback() {
                        public void onNext(BuildResponseItem item) {
                            String text = item.getStream();
                            if (nonNull(text)) {
                                llog.println(StringUtils.removeEnd(text, "\n"));
                                LOG.debug(StringUtils.removeEnd(text, "\n"));
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();
            llog.println("Image tagging during build isn't support atm, no tags applied.");
        } catch (Exception ex) {
            LOG.error("Can't get client", ex);
            throw new IOException("Can't get docker client", ex);
        }

        return null;
    }
}
