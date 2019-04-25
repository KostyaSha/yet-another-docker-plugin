package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad.utils.VariableUtils;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.PushImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.ConflictException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfigurations;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PushResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PushImageResultCallback;
import com.google.common.base.Throwables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.kostyasha.yad.utils.DockerJavaUtils.getAuthConfig;
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
public class DockerImageComboStepFileCallable extends MasterToSlaveFileCallable<DockerImageComboStepResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImageStepFileCallable.class);
    private static final long serialVersionUID = 1L;

    private final YADockerConnector connector;
    private DockerBuildImage buildImage;
    private final boolean cleanup;
    private final boolean cleanupDangling;
    private final boolean push;

    private final TaskListener taskListener;


    public DockerImageComboStepFileCallable(final YADockerConnector connector,
                                            final DockerBuildImage buildImage,
                                            final boolean cleanup,
                                            final boolean cleanupDangling,
                                            final boolean push,
                                            final TaskListener taskListener) {
        this.connector = connector;
        this.buildImage = buildImage;
        this.cleanup = cleanup;
        this.cleanupDangling = cleanupDangling;
        this.push = push;
        this.taskListener = taskListener;
    }

    @SuppressFBWarnings(value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    public static class Builder {
        private YADockerConnector connector;
        private DockerBuildImage buildImage;
        private boolean cleanup;
        private boolean cleanupDangling;
        private boolean push;

        private TaskListener taskListener;
        private Run run;

        public Builder() {
        }

        public Builder withConnector(@Nonnull YADockerConnector connector) {
            this.connector = connector;
            return this;
        }

        public Builder withBuildImage(@Nonnull DockerBuildImage that) {
            this.buildImage = new DockerBuildImage(that);
            return this;
        }

        public Builder withTaskListener(@Nonnull TaskListener taskListener) {
            this.taskListener = taskListener;
            return this;
        }

        public Builder withRun(@Nonnull Run run) {
            this.run = run;
            return this;
        }

        public Builder withCleanAll(boolean cleanup) {
            this.cleanup = cleanup;
            return this;
        }

        public Builder withCleanupDangling(boolean cleanupDangling) {
            this.cleanupDangling = cleanupDangling;
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
                expandedTags.add(VariableUtils.resolveVar(tag, run, taskListener));
            }
            buildImage.setTags(expandedTags);

            if (isNull(buildImage.getAuthConfigurations())) {
                buildImage.resolveCreds();
            }

            return new DockerImageComboStepFileCallable(
                    connector,
                    buildImage,
                    cleanup,
                    cleanupDangling,
                    push,
                    taskListener
            );
        }
    }

    public static Builder newDockerImageComboStepFileCallableBuilder() {
        return new Builder();
    }

    public DockerImageComboStepResponse invoke(File f, VirtualChannel channel) throws IOException {
        PrintStream llog = taskListener.getLogger();
        llog.println("Creating connection to docker daemon...");
        try (DockerClient client = connector.getClient()) {
            return invoke(client);
        } catch (Exception ex) {
            Throwables.propagate(ex);
        }

        return null;
    }

    /**
     * less indents
     */
    private DockerImageComboStepResponse invoke(DockerClient client) throws AbortException, InterruptedException {
        PrintStream llog = taskListener.getLogger();
        DockerImageComboStepResponse response = new DockerImageComboStepResponse();
        String imageId = null;
        MyBuildImageResultCallback imageResultCallback = new MyBuildImageResultCallback(llog);
        List<String> builtImages = new ArrayList<>();
        try {
            // build image
            BuildImageCmd buildImageCmd = client.buildImageCmd();
            buildImage.fillSettings(buildImageCmd);
            llog.println("Building image... ");
            imageId = buildImageCmd.exec(imageResultCallback).awaitImageId();
            llog.println("Build done.");

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

                builtImages.add(String.format("%s:%s", reposTag.repos, reposTag.tag));
            }


            // push
            if (push) {
                llog.println("Pushing all tagged images...");
                for (String tag : buildImage.getTagsNormalised()) {
                    try {
                        llog.println("Pushing '" + tag + "'...");
                        PushImageCmd pushImageCmd = client.pushImageCmd(tag);
                        final AuthConfigurations autConfigs = buildImage.getAuthConfigurations();
                        if (nonNull(autConfigs)) {
                            AuthConfig authConfig = getAuthConfig(tag, autConfigs);
                            if (nonNull(authConfig)) {
                                pushImageCmd.withAuthConfig(authConfig);
                            }
                        }

                        pushImageCmd.exec(new MyPushImageResultCallback())
                                .awaitCompletion();
                        llog.println("Pushed '" + tag + "'.");
                    } catch (Exception ex) {
                        taskListener.error("Can't push " + tag + " " + ex.getMessage());
                        throw ex;
                    }
                }
            }
            response.setSuccess(true);
        } catch (Throwable t) {
            response.setSuccess(false);
        } finally {
            builtImages.add(imageId);
            response.setImages(builtImages);
            response.setContainers(imageResultCallback.getContainers());

            invokeCleanup(client, builtImages, imageResultCallback.getContainers());
        }

        return response;
    }


    /**
     * Try to clean as much as we can without throwing errors.
     */
    private void invokeCleanup(DockerClient client, List<String> builtImages, @Nonnull Set<String> containers) {
        PrintStream llog = taskListener.getLogger();

        if (cleanupDangling) {
            for (String containerId : containers) {
                try {
                    client.removeImageCmd(containerId)
                            .exec();
                    llog.printf("Removed dangling layer image %s.%n", containerId);
                    LOG.debug("Removed dangling layer image '{}'", containerId);
                } catch (NotFoundException | ConflictException ex) {
                    // ignore
                } catch (Throwable ex) {
                    taskListener.error("Can't remove dangling layer image " + containerId + ".");
                    LOG.error("Can't remove dangling layer image " + containerId, ex);
                }
            }
        }

        if (!cleanup) {
            llog.println("Skipping cleanup.");
            return;
        } else {
            llog.println("Running cleanup...");
        }

        for (String image : builtImages) {
            if (isNotEmpty(image)) {
                llog.println("Removing built image " + image);
                try {
                    client.removeImageCmd(image)
                            .exec();
                } catch (NotFoundException ex) {
                    llog.println("Image doesn't exist.");
                } catch (Throwable ex) {
                    taskListener.error("Can't remove image" + ex.getMessage());
                    //ignore as it cleanup
                }
            }
        }
    }

    private static class MyBuildImageResultCallback extends BuildImageResultCallback {
        private static final String RUNNING_IN = "---> Running in";
        private static final String IN = "--->";
        private static final String REMOVING = "Removing intermediate container";
        private static final String BUILT = "Successfully built";

        private final PrintStream llog;
        private final Set<String> containers = new HashSet<>();

        MyBuildImageResultCallback(PrintStream llog) {
            this.llog = llog;
        }

        public Set<String> getContainers() {
            return containers;
        }

        public void onNext(BuildResponseItem item) {
            String text = item.getStream();
            if (nonNull(text)) {
                String s = StringUtils.removeEnd(text, "\n");
                checkContainer(s);
                llog.println(s);
                LOG.debug(s);
            }
            super.onNext(item);
        }

        /**
         * Docker can't cleanup dangling images https://github.com/moby/moby/issues/37311
         * Track all containers from input stream.
         */
        private void checkContainer(String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith(RUNNING_IN)) {
                String container = trimmed.replace(RUNNING_IN, "").trim();
                containers.add(container);
            } else if (trimmed.startsWith(IN)) {
                String container = trimmed.replace(IN, "").trim();
                containers.add(container);
            } else if (trimmed.contains(REMOVING)) {
                String container = trimmed.replace(REMOVING, "").trim();
                containers.remove(container);
            } else if (trimmed.contains(BUILT)) {
                String container = trimmed.replace(BUILT, "").trim();
                containers.remove(container);
            }

        }
    }

    private class MyPushImageResultCallback extends PushImageResultCallback {
        @Override
        public void onNext(PushResponseItem item) {
            printResponseItemToListener(taskListener, item);
            super.onNext(item);
        }
    }
}
