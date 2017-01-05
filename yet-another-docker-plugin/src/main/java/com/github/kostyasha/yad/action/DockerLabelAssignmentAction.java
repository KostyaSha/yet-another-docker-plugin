package com.github.kostyasha.yad.action;

import hudson.model.Label;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;

import javax.annotation.Nonnull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerLabelAssignmentAction implements LabelAssignmentAction {

    private Label label;

    public DockerLabelAssignmentAction(Label label) {
        this.label = label;
    }

    public Label getLabel() {
        return label;
    }

    @Override
    public Label getAssignedLabel(@Nonnull SubTask task) {
        return label;
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
