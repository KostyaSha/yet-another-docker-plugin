package com.github.kostyasha.yad;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.AbstractQueueTask;
import jenkins.tasks.SimpleBuildWrapper;

import java.io.IOException;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerSimpleBuildWrapper extends SimpleBuildWrapper {
    final private AbstractQueueTask task; //?

    public DockerSimpleBuildWrapper(AbstractQueueTask task) {
        this.task = task;
    }

    @Override
    public void setUp(Context context,
                      Run<?, ?> run,
                      FilePath workspace,
                      Launcher launcher,
                      TaskListener listener,
                      EnvVars initialEnvironment) throws IOException, InterruptedException {
        // launch external docker Slave
        // get task future after scheduling task
        // wait for task end?
    }
}
