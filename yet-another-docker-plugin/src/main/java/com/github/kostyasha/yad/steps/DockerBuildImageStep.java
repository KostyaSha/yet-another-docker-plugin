package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.commons.DockerBuildImage;
import com.github.kostyasha.yad.connector.YADockerConnector;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerBuildImageStep extends Builder implements SimpleBuildStep {

    YADockerConnector connector = null;
    DockerBuildImage buildImage = new DockerBuildImage();

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {

    }
}
