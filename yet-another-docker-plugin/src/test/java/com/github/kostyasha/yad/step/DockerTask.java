package com.github.kostyasha.yad.step;

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.queue.AbstractQueueTask;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerTask extends AbstractQueueTask implements Queue.TransientTask, AccessControlled {
    @Override
    public boolean isBuildBlocked() {
        return false;
    }

    @Override
    public String getWhyBlocked() {
        return null;
    }

    @Override
    public String getName() {
        return "Some name";
    }

    @Override
    public String getFullDisplayName() {
        return "Full display name";
    }

    @Override
    public void checkAbortPermission() {
    }

    @Override
    public boolean hasAbortPermission() {
        return true;
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    @Override
    public String getDisplayName() {
        return "Display name for task";
    }

    @Override
    public Label getAssignedLabel() {
        return null;
    }

    @Override
    public Node getLastBuiltOn() {
        return null;
    }

    @Override
    public long getEstimatedDuration() {
        return -1;
    }

    @Override
    public Queue.Executable createExecutable() throws IOException {
        return new MyExecutable(this);
    }

    @Nonnull
    @Override
    public ACL getACL() {
        return Jenkins.getInstance().getAuthorizationStrategy().getRootACL();
    }

    @Override
    public void checkPermission(@Nonnull Permission permission) throws AccessDeniedException {
        getACL().checkPermission(permission);
    }

    @Override
    public boolean hasPermission(@Nonnull Permission permission) {
        return getACL().hasPermission(permission);
    }
}
