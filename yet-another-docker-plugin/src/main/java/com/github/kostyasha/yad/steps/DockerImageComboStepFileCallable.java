package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.PushImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PushResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PushImageResultCallback;
import com.google.common.base.Throwables;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static com.github.kostyasha.yad.utils.LogUtils.printResponseItemToListener;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerImageComboStepFileCallable extends MasterToSlaveFileCallable<Boolean> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImageStepFileCallable.class);
    private static final long serialVersionUID = 1L;

    private YADockerConnector connector = null;
    private DockerBuildImage buildImage = new DockerBuildImage();
    private TaskListener taskListener;
    private boolean cleanAll;
    private boolean pushAll;

    private transient String imageId;

    private DockerImageComboStepFileCallable() {
    }

    public static DockerImageComboStepFileCallable newDockerImageComboStepFileCallable() {
        return new DockerImageComboStepFileCallable();
    }

    public DockerImageComboStepFileCallable withConnector(YADockerConnector connector) {
        this.connector = connector;
        return this;
    }

    public DockerImageComboStepFileCallable withBuildImage(DockerBuildImage buildImage) {
        this.buildImage = buildImage;
        return this;
    }

    public DockerImageComboStepFileCallable withTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
        return this;
    }

    public DockerImageComboStepFileCallable withCleanAll(boolean cleanAll) {
        this.cleanAll = cleanAll;
        return this;
    }

    public DockerImageComboStepFileCallable withPushAll(boolean pushAll) {
        this.pushAll = pushAll;
        return this;
    }

    public Boolean invoke(File f, VirtualChannel channel) throws IOException {
        PrintStream llog = taskListener.getLogger();
        llog.println("Creating connection to docker daemon...");
        try (DockerClient client = connector.getClient()) {
            invoke(client);
        } catch (Exception ex) {
            Throwables.propagate(ex);
            return false;
        }

        return true;
    }

    /**
     * less indents
     */
    private void invoke(DockerClient client) throws AbortException {
        PrintStream llog = taskListener.getLogger();

        try {
            // build image
            BuildImageCmd buildImageCmd = client.buildImageCmd();
            buildImage.fillSettings(buildImageCmd);
            llog.print("Pulling image... ");

            imageId = buildImageCmd.exec(new BuildImageResultCallback() {
                public void onNext(BuildResponseItem item) {
                    String text = item.getStream();
                    if (nonNull(text)) {
                        llog.println(StringUtils.removeEnd(text, "\n"));
                        LOG.debug(StringUtils.removeEnd(text, "\n"));
                    }
                    super.onNext(item);
                }
            }).awaitImageId();
            llog.println("Pulling done.");

            if (isEmpty(imageId)) {
                throw new AbortException("Built image is empty or null!");
            }

            // re-tag according to buildImage config
            for (String tag : buildImage.getTagsNormalised()) {
                NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tag);
                llog.printf("Adding additional tag '%s:%s'...%n", reposTag.repos, reposTag.tag);
                // no need to remove before
                client.tagImageCmd(imageId, reposTag.repos, reposTag.tag)
                        .exec();
                llog.printf("Added additional tag '%s:%s'.%n", reposTag.repos, reposTag.tag);
            }

            // push
            if (pushAll) {
                llog.println("Pushing all tagged images...");
                for (String tag : buildImage.getTagsNormalised()) {
                    try {
                        llog.println("Pushing '" + tag + "'...");
                        PushImageCmd pushImageCmd = client.pushImageCmd(tag);
                        if (nonNull(buildImage.getAuthConfig())) {
                            pushImageCmd.withAuthConfig(buildImage.getAuthConfig());
                        }
                        pushImageCmd.exec(new PushImageResultCallback() {
                            @Override
                            public void onNext(PushResponseItem item) {
                                printResponseItemToListener(taskListener, item);
                                super.onNext(item);
                            }
                        }).awaitSuccess();
                        llog.println("Pushed '" + tag + "'.");
                    } catch (Exception ex) {
                        taskListener.error("Can't push " + tag + " " + ex.getMessage());
                        throw ex;
                    }
                }
            }
        } finally {
            invokeCleanup(client);
        }
    }


    /**
     * Try to clean as much as we can without throwing errors.
     */
    private void invokeCleanup(DockerClient client) {
        PrintStream llog = taskListener.getLogger();
        if (!cleanAll) {
            llog.println("Skipping cleanup.");
            return;
        } else {
            llog.println("Running cleanup...");
        }

        if (isNotEmpty(imageId)) {
            llog.println("Removing built image " + imageId);
            try {
                client.removeImageCmd(imageId)
                        .withForce(true)
                        .exec();
            } catch (NotFoundException ex) {
                llog.println("Image doesn't exist.");
            } catch (Throwable ex) {
                taskListener.error("Can't remove image" + ex.getMessage());
                //ignore as it cleanup
            }
        }

        for (String tag : buildImage.getTagsNormalised()) {
            try {
                NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tag);
                llog.printf("Removing tagged image '%s:%s'.%n", reposTag.repos, reposTag.tag);
                // no need to remove before
                try {
                    client.removeImageCmd(reposTag.repos + ":" + reposTag.tag)
                            .withForce(true)
                            .exec();
                } catch (NotFoundException ex) {
                    llog.println("Tagged image doesn't exist.");
                } catch (Throwable ex) {
                    taskListener.error("Can't remove tagged image" + ex.getMessage());
                    //ignore as it cleanup
                }
            } catch (Exception ex) {
                taskListener.error("Can't process tag " + tag + " for removal");
            }
        }
    }

}
