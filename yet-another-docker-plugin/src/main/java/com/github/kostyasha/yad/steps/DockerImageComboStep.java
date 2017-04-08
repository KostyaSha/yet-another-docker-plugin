package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.BooleanUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static com.github.kostyasha.yad.steps.DockerBuildImageStepFileCallable.newDockerBuildImageStepCallable;
import static com.github.kostyasha.yad.steps.DockerImageComboStepFileCallable.newDockerImageComboStepFileCallable;

/**
 * Let's assume that user wants:
 * 1) build image from one Dockerfile possibly with multiple tags
 * 2) tag image with multiple tags i.e. different domains
 * 3) push all this tags
 * 4) cleanup image
 * Cleanup image on failure.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerImageComboStep extends Builder implements SimpleBuildStep {
    private static Logger LOG = LoggerFactory.getLogger(DockerBuildImageStep.class);

    private YADockerConnector connector = null;
    private DockerBuildImage buildImage = new DockerBuildImage();
    private boolean cleanAll = true;
    private boolean pushAll = true;

    @DataBoundConstructor
    public DockerImageComboStep(YADockerConnector connector, DockerBuildImage buildImage) {
        this.connector = connector;
        this.buildImage = buildImage;
    }

    public YADockerConnector getConnector() {
        return connector;
    }

    public DockerBuildImage getBuildImage() {
        return buildImage;
    }

    public boolean isCleanAll() {
        return cleanAll;
    }

    @DataBoundSetter
    public void setCleanAll(boolean cleanAll) {
        this.cleanAll = cleanAll;
    }

    public boolean isPushAll() {
        return pushAll;
    }

    @DataBoundSetter
    public void setPushAll(boolean pushAll) {
        this.pushAll = pushAll;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream llog = listener.getLogger();
        try {
            llog.println("Executing remote combo builder...");
            if (BooleanUtils.isFalse(
                    workspace.act(
                            newDockerImageComboStepFileCallable()
                                    .withBuildImage(buildImage)
                                    .withConnector(connector)
                                    .withTaskListener(listener)
                                    .withPushAll(pushAll)
                                    .withCleanAll(cleanAll)
                    ))) {
                throw new AbortException("Something failed");
            }
        } catch (Exception ex) {
            LOG.error("Can't build image", ex);
            throw ex;
        }
    }

    @Extension
    @Symbol("docker-image-producer")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
