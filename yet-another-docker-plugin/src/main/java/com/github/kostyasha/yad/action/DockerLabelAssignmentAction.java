package com.github.kostyasha.yad.action;

import com.github.kostyasha.yad.DockerSlaveConfig;
import com.github.kostyasha.yad.connector.YADockerConnector;
import hudson.model.Label;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration required to provision slave without Cloud.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerLabelAssignmentAction implements LabelAssignmentAction {
    // plugabble connector
    @Nonnull
    private final YADockerConnector connector;

    @Nonnull
    private final DockerSlaveConfig slaveConfig;

    public DockerLabelAssignmentAction(@Nonnull DockerSlaveConfig slaveConfig,
                                       @Nonnull YADockerConnector connector) {
        this.slaveConfig = slaveConfig;
        this.connector = connector;
    }

    public String getAssignedLabel() {
        return slaveConfig.getLabelString();
    }

    @Nonnull
    public YADockerConnector getConnector() {
        return connector;
    }

    @Nonnull
    public DockerSlaveConfig getSlaveConfig() {
        return slaveConfig;
    }

    @Override
    public Label getAssignedLabel(@Nullable SubTask task) {
        return Jenkins.getInstance().getLabelAtom(getAssignedLabel());
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
