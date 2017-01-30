package com.github.kostyasha.yad.queue;

import hudson.model.queue.CauseOfBlockage;

/**
 * @author Kanstantsin Shautsou
 */
public class SingleNodeCauseOfBlockage extends CauseOfBlockage {
    private String displayName;

    public SingleNodeCauseOfBlockage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getShortDescription() {
        return "Slave tied to " + getDisplayName();
    }
}
