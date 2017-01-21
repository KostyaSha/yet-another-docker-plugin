package com.github.kostyasha.yad;

import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import com.github.kostyasha.yad.jobconfig.DockerCloudJobConfig;
import com.github.kostyasha.yad.jobconfig.SlaveJobConfig;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import java.util.UUID;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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
