package com.github.kostyasha.yad.strategy;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.OneOffExecutor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.EphemeralNode;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Mix of {@link org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy} (1.3) and {@link CloudRetentionStrategy}
 * that allows configure it parameters and has Descriptor.
 * <p>
 * Retention strategy that allows a cloud slave to run only a single build before disconnecting.
 * A {@link ContinuableExecutable} does not trigger termination.
 */
public class DockerOnceRetentionStrategy extends CloudRetentionStrategy implements ExecutorListener {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DockerOnceRetentionStrategy.class);

    private int idleMinutes = 10;
    private transient boolean terminating;

    /**
     * Creates the retention strategy.
     *
     * @param idleMinutes number of minutes of idleness after which to kill the slave; serves a backup in case the
     *                    strategy fails to detect the end of a task
     */
    @DataBoundConstructor
    public DockerOnceRetentionStrategy(int idleMinutes) {
        super(idleMinutes);
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @Override
    @GuardedBy("hudson.model.Queue.lock")
    public long check(final AbstractCloudComputer acc) {
        // When the slave is idle we should disable accepting tasks and check to see if it is already trying to
        // terminate. If it's not already trying to terminate then lets terminate manually.
        if (acc.isIdle() && !disabled) {
            final long idleMilliseconds = System.currentTimeMillis() - acc.getIdleStartMilliseconds();
            if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(idleMinutes)) {
                LOG.debug("Disconnecting {}", acc.getName());
                done(acc);
            }
        }

        // Return one because we want to check every minute if idle.
        return 1;
    }

    @Override
    public void start(AbstractCloudComputer c) {
        if (c.getNode() instanceof EphemeralNode) {
            throw new IllegalStateException("May not use OnceRetentionStrategy on an EphemeralNode: " + c);
        }
        super.start(c);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor);
    }

    protected void done(Executor executor) {
        final AbstractCloudComputer<?> c = (AbstractCloudComputer) executor.getOwner();
        Queue.Executable exec = executor.getCurrentExecutable();
        if (executor instanceof OneOffExecutor) {
            LOG.debug("Not terminating {} because {} was a flyweight task", c.getName(), exec);
            return;
        }

        if (exec instanceof ContinuableExecutable && ((ContinuableExecutable) exec).willContinue()) {
            LOG.debug("not terminating {} because {} says it will be continued", c.getName(), exec);
            return;
        }

        LOG.debug("terminating {} since {} seems to be finished", c.getName(), exec);
        done(c);
    }

    protected void done(final AbstractCloudComputer<?> c) {
        c.setAcceptingTasks(false); // just in case
        synchronized (this) {
            if (terminating) {
                return;
            }
            terminating = true;
        }

        final Future<?> submit = Computer.threadPoolForRemoting.submit(() -> {
                try {
                    AbstractCloudSlave node = c.getNode();
                    if (node != null) {
                        node.terminate();
                    }
                } catch (InterruptedException | IOException e) {
                    LOG.warn("Failed to terminate " + c.getName(), e);
                    synchronized (DockerOnceRetentionStrategy.this) {
                        terminating = false;
                    }
                }
            }
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DockerOnceRetentionStrategy that = (DockerOnceRetentionStrategy) o;

        return new EqualsBuilder()
                .append(idleMinutes, that.idleMinutes)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(idleMinutes)
                .toHashCode();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Docker Once Retention Strategy";
        }
    }
}
