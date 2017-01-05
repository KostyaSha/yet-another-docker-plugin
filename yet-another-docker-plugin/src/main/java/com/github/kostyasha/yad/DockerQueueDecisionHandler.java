package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Queue;

import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerQueueDecisionHandler extends Queue.QueueDecisionHandler {
    @Override
    public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
        return true;
    }
}
