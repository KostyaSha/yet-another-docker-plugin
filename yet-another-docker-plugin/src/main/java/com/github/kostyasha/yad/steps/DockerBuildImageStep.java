package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.BuildImageCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

import static java.util.Objects.nonNull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerBuildImageStep extends Builder implements SimpleBuildStep {
    private static Logger LOG = LoggerFactory.getLogger(DockerBuildImageStep.class);

    private YADockerConnector connector = null;
    private DockerBuildImage buildImage = new DockerBuildImage();

    @DataBoundConstructor
    public DockerBuildImageStep(YADockerConnector connector, DockerBuildImage buildImage) {
        this.connector = connector;
        this.buildImage = buildImage;
    }

    public YADockerConnector getConnector() {
        return connector;
    }

    public DockerBuildImage getBuildImage() {
        return buildImage;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream llog = listener.getLogger();
        try (DockerClient client = connector.getClient()) {
            BuildImageCmd buildImageCmd = client.buildImageCmd();
            buildImage.fillSettings(buildImageCmd);
            buildImageCmd.exec(new BuildImageResultCallback(){
                public void onNext(BuildResponseItem item) {
                    String text = item.getStream();
                    if (nonNull(text)) {
                        llog.println(StringUtils.removeEnd(text, "\n"));
                        LOG.debug(StringUtils.removeEnd(text, "\n"));
                    }
                    super.onNext(item);
                }
            }).awaitImageId();
        } catch (Exception ex) {
            LOG.error("Can't get client", ex);
        }
    }

    @Extension
    @Symbol("docker-image-build")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
