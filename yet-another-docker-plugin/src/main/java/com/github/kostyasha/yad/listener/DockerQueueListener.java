package com.github.kostyasha.yad.listener;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerJobProperty;
import com.github.kostyasha.yad.DockerSlaveSingle;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import com.github.kostyasha.yad.jobconfig.DockerCloudJobConfig;
import com.github.kostyasha.yad.jobconfig.SlaveJobConfig;
import com.github.kostyasha.yad.launcher.DockerComputerSingleJNLPLauncher;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;

import java.io.IOException;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.jenkinsci.plugins.cloudstats.CloudStatistics.ProvisioningListener.get;

/**
 * @author Kanstantsin Shautsou
 */
@Extension(ordinal = 100)
public class DockerQueueListener extends QueueListener {
    /**
     * 1. Assign unique label
     * 2. If DockerCloud, then add template.
     */
    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        if (!processItemWithAction(wi)) {
            processJobWithProperty(wi);
        }
    }

    /**
     * If it Job and has JobProperty, then spin with DockerCloud
     */
    private boolean processJobWithProperty(Queue.WaitingItem wi) {
        if (!(wi.task instanceof Job)) {
            // looks like not correct Job type check
            return false;
        }

        final Job job = (Job) wi.task;
        final DockerJobProperty dockerProp = (DockerJobProperty) job.getProperty(DockerJobProperty.class);
        if (isNull(dockerProp)) {
            return false;
        }

        final UUID uuid = UUID.randomUUID();
//        final LabelAtom label = Jenkins.getInstance().getLabelAtom(uuid.toString());

        final DockerLabelAssignmentAction labelAssignmentAction = new DockerLabelAssignmentAction(uuid.toString());
        wi.addAction(labelAssignmentAction);


        final SlaveJobConfig slaveJobConfig = dockerProp.getSlaveJobConfig();
        if (slaveJobConfig instanceof DockerCloudJobConfig) {
            final DockerCloudJobConfig cloudJobConfig = (DockerCloudJobConfig) slaveJobConfig;
            final DockerSlaveTemplate template = cloudJobConfig.getTemplate();
            template.setLabelString(uuid.toString());

            final DockerCloud cloud = (DockerCloud) Jenkins.getInstance().getCloud(cloudJobConfig.getConnector().getCloudId());
            cloud.addTransientTemplate(template);
        }

        labelAssignmentAction.getAssignedLabel(null).nodeProvisioner.suggestReviewNow();

        return true;
    }

    /**
     * Process with special Action
     */
    private boolean processItemWithAction(Queue.WaitingItem wi) {
        final DockerLabelAssignmentAction action = wi.getAction(DockerLabelAssignmentAction.class);
        if (nonNull(action)) {
            try {
                final ProvisioningActivity.Id activityId = new ProvisioningActivity.Id(wi.getDisplayName(),
                    "fake-image");
                get().onStarted(activityId);

                final DockerSlaveSingle slave = new DockerSlaveSingle("docker-slave",
                        "description",
                        "/home/jenkins",
                        1,
                        Node.Mode.EXCLUSIVE,
                        "", // label string
                        new DelegatingComputerLauncher(new DockerComputerSingleJNLPLauncher()) {
                            @Override
                            public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
                                // no launch
                            }
                        },
                    new DockerOnceRetentionStrategy(10),
                    emptyList(),
                    activityId
                );
                Jenkins.getInstance().addNode(slave);
                return true;
            } catch (Descriptor.FormException | IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Remove template it it was cloud.
     */
    @Override
    public void onLeft(Queue.LeftItem li) {
    }
}
