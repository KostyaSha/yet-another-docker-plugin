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
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static com.github.kostyasha.yad.utils.LogUtils.printResponseItemToListener;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * All actions happening on remote.
 * Variables should be resolved during execution on remote.
 * Client should be instantiated in this remoting exection.
 * All configuration objects should be serializable.
 *
 * @author Kanstantsin Shautsou
 * @see DockerImageComboStep
 */
public class DockerImageComboStepFileCallable extends MasterToSlaveFileCallable<Boolean> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImageStepFileCallable.class);
    private static final long serialVersionUID = 1L;

    private final YADockerConnector connector;
    private final DockerBuildImage buildImage;
    private final boolean cleanup;
    private final boolean push;

    private final TaskListener taskListener;

    private transient String imageId;

    public DockerImageComboStepFileCallable(final YADockerConnector connector,
                                            final DockerBuildImage buildImage,
                                            final boolean cleanup,
                                            final boolean push,
                                            final TaskListener taskListener) {
        this.connector = connector;
        this.buildImage = buildImage;
        this.cleanup = cleanup;
        this.push = push;
        this.taskListener = taskListener;
    }

    public static class Builder {
        private YADockerConnector connector;
        private DockerBuildImage buildImage;
        private boolean cleanup;
        private boolean push;

        private TaskListener taskListener;
        private Run run;

        public Builder() {
        }

        /**
         * Resolve on remoting side during execution.
         * Because node may have some specific node vars.
         */
        private String resolveVar(String var) {
            String resolvedVar = var;
            try {
                final EnvVars envVars = run.getEnvironment(taskListener);
                resolvedVar = envVars.expand(var);
            } catch (IOException | InterruptedException e) {
                LOG.warn("Can't resolve variable {}", var, e);
            }
            return resolvedVar;
        }

        public Builder withConnector(YADockerConnector connector) {
            this.connector = connector;
            return this;
        }

        public Builder withBuildImage(DockerBuildImage that) {
            this.buildImage = new DockerBuildImage(that);
            return this;
        }

        public Builder withTaskListener(TaskListener taskListener) {
            this.taskListener = taskListener;
            return this;
        }

        public Builder withRun(Run run) {
            this.run = run;
            return this;
        }

        public Builder withCleanAll(boolean cleanup) {
            this.cleanup = cleanup;
            return this;
        }

        public Builder withPushAll(boolean push) {
            this.push = push;
            return this;
        }

        public DockerImageComboStepFileCallable build() throws IOException, InterruptedException {
            if (isNull(run) || isNull(taskListener) || isNull(connector) || isNull(buildImage)) {
                throw new IllegalStateException("Specify vars!");
            }
            // if something should be resolved on master side do it here
            final List<String> tags = buildImage.getTags();
            final ArrayList<String> expandedTags = new ArrayList<>(tags.size());
            for (String tag : tags) {
                expandedTags.add(resolveVar(tag));
            }
            buildImage.setTags(expandedTags);

            return new DockerImageComboStepFileCallable(
                    connector,
                    buildImage,
                    cleanup,
                    push,
                    taskListener
            );
        }
    }

    public static Builder newDockerImageComboStepFileCallableBuilder() {
        return new Builder();
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
            if (push) {
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
        if (!cleanup) {
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
