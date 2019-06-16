package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

import static com.github.kostyasha.yad.steps.DockerImageComboStepFileCallable.newDockerImageComboStepFileCallableBuilder;
import static java.util.Objects.isNull;
import static org.apache.commons.lang.StringUtils.trimToEmpty;

/**
 * Let's assume that user wants:
 * 1) build image from one 'Dockerfile' possibly with multiple tags
 * 2) tag image with multiple tags i.e. different domains. Tag name may have variable.
 * 3) push all this tags
 * 4) cleanup images after success or failure.
 *
 * @author Kanstantsin Shautsou
 */
@SuppressFBWarnings(value = "REC_CATCH_EXCEPTION")
public class DockerImageComboStep extends Builder implements SimpleBuildStep {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildImageStep.class);

    @Nonnull
    private YADockerConnector connector;
    private DockerBuildImage buildImage = null;
    private boolean clean = true;
    private boolean cleanupDangling = true;
    private boolean push = true;

    /**
     * For programmatic usage.
     */
    @CheckForNull
    private transient DockerImageComboStepResponse response;

    @DataBoundConstructor
    public DockerImageComboStep(@Nonnull YADockerConnector connector, @Nonnull DockerBuildImage buildImage) {
        this.connector = connector;
        this.buildImage = buildImage;
    }

    @Nonnull
    public YADockerConnector getConnector() {
        return connector;
    }

    public DockerBuildImage getBuildImage() {
        return buildImage;
    }

    public boolean isClean() {
        return clean;
    }

    @DataBoundSetter
    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public boolean isCleanupDangling() {
        return cleanupDangling;
    }

    @DataBoundSetter
    public void setCleanupDangling(boolean cleanupDangling) {
        this.cleanupDangling = cleanupDangling;
    }

    public boolean isPush() {
        return push;
    }

    @DataBoundSetter
    public void setPush(boolean push) {
        this.push = push;
    }

    @CheckForNull
    public DockerImageComboStepResponse getResponse() {
        return response;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream llog = listener.getLogger();
        final DockerImageComboStepFileCallable comboCallable = newDockerImageComboStepFileCallableBuilder()
                .withTaskListener(listener)
                .withRun(run)
                .withBuildImage(buildImage)
                .withConnector(connector)
                .withPushAll(push)
                .withCleanAll(clean)
                .withCleanupDangling(cleanupDangling)
                .build();
        try {
            llog.println("Executing remote combo builder...");

            response = workspace.act(comboCallable);

            if (!response.isSuccess()) {
                throw new IOException(trimToEmpty(response.getErrorMessage()),
                        new Exception(trimToEmpty(response.getErrorTrace())));
            }

            if (isNull(response)) {
                throw new AbortException("Something failed.");

            }
        } catch (Exception ex) {
            LOG.error("Can't build image", ex);
            throw ex;
        }
    }

    //    @Extension
    @Symbol("docker-image-producer")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
