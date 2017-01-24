package com.github.kostyasha.yad;

import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.ComputerLauncher;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static java.util.Objects.nonNull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerComputerSingle extends AbstractCloudComputer<DockerSlaveSingle> implements TrackedItem {
    private final ProvisioningActivity.Id activityId;
    private transient TaskListener listener;
    private transient Run run;

    public DockerComputerSingle(DockerSlaveSingle slave, ProvisioningActivity.Id activityId) {
        super(slave);
        this.activityId = activityId;
    }

    @Override
    public TaskListener getListener() {
        return nonNull(listener) ? listener : super.getListener();
    }

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    public Run getRun() {
        return run;
    }

    public void setRun(Run run) {
        this.run = run;
    }

    @Override
    public void setChannel(Channel channel, OutputStream launchLog, Channel.Listener listener) throws IOException, InterruptedException {
        super.setChannel(channel, launchLog, listener);
    }

    public boolean isReallyOffline() {
        return super.isOffline();
    }

    @Override
    public boolean isOffline() {
        // create executors to pick tasks
        return false;
    }

    @Override
    public Charset getDefaultCharset() {
        // either fails
        // java.lang.NullPointerException
        // at hudson.model.Run.execute(Run.java:1702)
        // at hudson.model.FreeStyleBuild.run(FreeStyleBuild.java:43)
        // at hudson.model.ResourceController.execute(ResourceController.java:98)
        // at hudson.model.Executor.run(Executor.java:404)
        return Charset.forName("UTF-8");
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return activityId;
    }
}
