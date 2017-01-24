package com.github.kostyasha.yad.action;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.connector.YADockerConnector;
import hudson.model.Label;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerLabelAssignmentAction implements LabelAssignmentAction {

    private final String assignedLabel;

    private YADockerConnector connector = null;
    private DockerSlaveTemplate slaveTemplate = null;

    public DockerLabelAssignmentAction(String assignedLabel) {
        this.assignedLabel = assignedLabel;
    }

    public String getAssignedLabel() {
        return assignedLabel;
    }

    @CheckForNull
    public YADockerConnector getConnector() {
        return connector;
    }

    public void setConnector(YADockerConnector connector) {
        this.connector = connector;
    }

    @CheckForNull
    public DockerSlaveTemplate getSlaveTemplate() {
        return slaveTemplate;
    }

    public void setSlaveTemplate(DockerSlaveTemplate slaveTemplate) {
        this.slaveTemplate = slaveTemplate;
    }

    @Override
    public Label getAssignedLabel(@Nullable SubTask task) {
        return Jenkins.getInstance().getLabelAtom(assignedLabel);
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
