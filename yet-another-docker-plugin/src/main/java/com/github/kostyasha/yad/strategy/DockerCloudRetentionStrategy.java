package com.github.kostyasha.yad.strategy;

import hudson.Extension;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;

import static hudson.util.TimeUnit2.MINUTES;

/**
 * {@link CloudRetentionStrategy} that has Descriptor and UI with description
 */
public class DockerCloudRetentionStrategy extends RetentionStrategy<AbstractCloudComputer> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerCloudRetentionStrategy.class);
    private static final long serialVersionUID = 1L;

    private int idleMinutes = 0;

    @DataBoundConstructor
    public DockerCloudRetentionStrategy(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    /**
     * While x-stream serialisation buggy, copy implementation.
     */
    @Override
    @GuardedBy("hudson.model.Queue.lock")
    public long check(final AbstractCloudComputer c) {
        final AbstractCloudSlave computerNode = c.getNode();
        if (c.isIdle() && computerNode != null) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > MINUTES.toMillis(idleMinutes)) {
                LOG.info("Disconnecting {}, after {} min timeout.", c.getName(), idleMinutes);
                try {
                    computerNode.terminate();
                } catch (InterruptedException | IOException e) {
                    LOG.warn("Failed to terminate {}", c.getName(), e);
                }
            }
        }
        return 1;
    }

    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(AbstractCloudComputer c) {
        c.connect(false);
    }

    @Extension
    public static final class DescriptorImpl extends hudson.model.Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Docker Cloud Retention Strategy";
        }
    }
}
