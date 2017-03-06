package com.github.kostyasha.yad.step;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Kanstantsin Shautsou
 */
public class TaskBuildStep extends Builder implements SimpleBuildStep {

    @DataBoundConstructor
    public TaskBuildStep() {
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher,
                        @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        taskListener.getLogger().println("Entering task producer");
        Queue.getInstance().schedule(new DockerTask(), 0);
        taskListener.getLogger().println("Task scheduled? sleep");
        Thread.sleep(MINUTES.toMillis(10));
        taskListener.getLogger().println("end of sleep");
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Test Task Producer";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
