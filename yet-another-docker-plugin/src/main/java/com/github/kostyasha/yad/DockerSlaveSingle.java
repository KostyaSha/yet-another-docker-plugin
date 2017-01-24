package com.github.kostyasha.yad;

import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerSlaveSingle extends AbstractCloudSlave implements TrackedItem {

    private ProvisioningActivity.Id activityId;

    public DockerSlaveSingle(String name,
                             String nodeDescription,
                             String remoteFS,
                             String numExecutors,
                             Mode mode,
                             String labelString,
                             ComputerLauncher launcher,
                             RetentionStrategy retentionStrategy,
                             List<? extends NodeProperty<?>> nodeProperties)
        throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    public DockerSlaveSingle(String name,
                             String nodeDescription,
                             String remoteFS,
                             int numExecutors,
                             Mode mode,
                             String labelString,
                             ComputerLauncher launcher,
                             RetentionStrategy retentionStrategy,
                             List<? extends NodeProperty<?>> nodeProperties,
                             ProvisioningActivity.Id activityId)
        throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        this.activityId = activityId;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new DockerComputerSingle(this, activityId);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        CloudStatistics statistics = CloudStatistics.get();
        ProvisioningActivity activity = statistics.getActivityFor(this);
        if (activity != null) {
            activity.enterIfNotAlready(ProvisioningActivity.Phase.COMPLETED);
        }
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return activityId;
    }
}