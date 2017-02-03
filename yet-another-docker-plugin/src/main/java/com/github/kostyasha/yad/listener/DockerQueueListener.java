package com.github.kostyasha.yad.listener;

import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.DockerSlaveConfig;
import com.github.kostyasha.yad.DockerSlaveSingle;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;

import java.io.IOException;

import static java.util.Collections.emptyList;
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
        }
    }

//    /**
//     * If it Job and has JobProperty, then spin with DockerCloud
//     */
//    private boolean processJobWithProperty(Queue.WaitingItem wi) throws Descriptor.FormException {
//        if (!(wi.task instanceof Job)) {
//            // looks like not correct Job type check
//            return false;
//        }
//
//        final Job job = (Job) wi.task;
//        final DockerJobProperty dockerProp = (DockerJobProperty) job.getProperty(DockerJobProperty.class);
//        if (isNull(dockerProp)) {
//            return false;
//        }
//
//        final UUID uuid = UUID.randomUUID();
////        final LabelAtom label = Jenkins.getInstance().getLabelAtom(uuid.toString());
//
//        final DockerSlaveTemplate template = new DockerSlaveTemplate(Long.toString(wi.getId()));
//        template.setLabelString(uuid.toString());
//
//        final DockerLabelAssignmentAction labelAssignmentAction = new DockerLabelAssignmentAction(template, );
//        wi.addAction(labelAssignmentAction);
//
//
//        final SlaveJobConfig slaveJobConfig = dockerProp.getSlaveJobConfig();
//        if (slaveJobConfig instanceof DockerCloudJobConfig) {
//            final DockerCloudJobConfig cloudJobConfig = (DockerCloudJobConfig) slaveJobConfig;
//            final DockerSlaveTemplate template = cloudJobConfig.getConfig();
//            template.setLabelString(uuid.toString());
//
//            final DockerCloud cloud = (DockerCloud) Jenkins.getInstance().getCloud(cloudJobConfig.getConnector().getCloudId());
//            cloud.addTransientTemplate(template);
//        }
//
//        labelAssignmentAction.getAssignedLabel(null).nodeProvisioner.suggestReviewNow();
//
//        return true;
//    }

    /**
     * Process with special Action
     */
    private boolean processItemWithAction(Queue.WaitingItem wi) {
        final DockerLabelAssignmentAction action = wi.getAction(DockerLabelAssignmentAction.class);
        if (nonNull(action)) {
            final DockerSlaveConfig template = action.getSlaveConfig();
            final DockerContainerLifecycle lifecycle = template.getDockerContainerLifecycle();

            final ProvisioningActivity.Id activityId = new ProvisioningActivity.Id(
                    wi.getDisplayName(),
                    lifecycle.getImage()
            );

            try {
                get().onStarted(activityId);

                final DockerSlaveSingle slave = new DockerSlaveSingle("docker-slave",
                        "Slave for " + wi.getDisplayName(),
                        action.getSlaveConfig(),
                        action.getConnector(),
                        activityId
                );
                Jenkins.getInstance().addNode(slave);
                return true;
            } catch (Descriptor.FormException | IOException e) {
                CloudStatistics.ProvisioningListener.get().onFailure(activityId, e);
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
