package com.github.kostyasha.yad;

import com.github.kostyasha.yad_docker_java.com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Represents remote (running) container
 */
public class DockerComputer extends AbstractCloudComputer<DockerSlave> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerComputer.class);

    /**
     * remember associated container id
     */
    protected final String containerId;

    protected final String cloudId;

    protected String displayName;

    public DockerComputer(DockerSlave dockerSlave) {
        super(dockerSlave);
        containerId = dockerSlave.getContainerId();
        cloudId = dockerSlave.getCloudId();
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "no null on getters")
    @CheckForNull
    public DockerCloud getCloud() {
        return getNode() != null ? getNode().getCloud() : null;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        if (StringUtils.isNotBlank(displayName)) {
            return displayName;
        }

        return getName();
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOG.debug("Computer {} taskAccepted", this);
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        LOG.debug("Computer {} taskCompleted", this);

        // May take the slave offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        LOG.debug("Computer {} taskCompletedWithProblems", this);
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
    }

    public String getContainerId() {
        return containerId;
    }

    public String getCloudId() {
        return cloudId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", super.getName())
                .add("slave", getNode())
                .toString();
    }
}
