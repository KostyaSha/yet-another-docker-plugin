package com.github.kostyasha.yad.queue;

import com.github.kostyasha.yad.DockerSlaveSingle;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        if (node instanceof DockerSlaveSingle) {
            if (!node.getNodeName().equals(Long.toString(item.getId()))) {
                return new SingleNodeCauseOfBlockage(item.getDisplayName());
            }
        }

        return null;
    }
}
