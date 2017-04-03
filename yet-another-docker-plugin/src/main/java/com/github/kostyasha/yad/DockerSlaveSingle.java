package com.github.kostyasha.yad;

import com.github.kostyasha.yad.action.DockerTerminateCmdAction;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad.launcher.DockerComputerSingleJNLPLauncher;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotModifiedException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;

import static java.util.Objects.nonNull;

/**
 * Alternative to {@link DockerSlave}.
 *
 * @author Kanstantsin Shautsou
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD",
        justification = "Broken serialization https://issues.jenkins-ci.org/browse/JENKINS-31916")
public class DockerSlaveSingle extends AbstractCloudSlave implements TrackedItem {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DockerSlaveSingle.class);

    private final YADockerConnector connector;
    private final ProvisioningActivity.Id activityId;
    private final DockerSlaveConfig config;

    private transient TaskListener listener = null;

    public DockerSlaveSingle(@Nonnull String name,
                             @Nonnull String nodeDescription,
                             @Nonnull DockerSlaveConfig config,
                             @Nonnull YADockerConnector connector,
                             @Nonnull ProvisioningActivity.Id activityId)
            throws IOException, Descriptor.FormException {
        super(name, nodeDescription,
                config.getRemoteFs(), config.getNumExecutors(), config.getMode(),
                "",
                config.getLauncher(), config.getRetentionStrategy(), config.getNodeProperties());
        this.connector = connector;
        this.activityId = activityId;
        this.config = config;

    }

    public YADockerConnector getConnector() {
        return connector;
    }

    public DockerSlaveConfig getConfig() {
        return config;
    }

    @Override
    public DelegatingComputerLauncher getLauncher() {
        return (DelegatingComputerLauncher) super.getLauncher();
    }

    private String getContainerId() {
        return ((DockerComputerSingleJNLPLauncher) getLauncher().getLauncher()).getContainerId();
    }

    @Nonnull
    public TaskListener getListener() {
        return nonNull(listener) ? listener : new StreamTaskListener(System.out, Charset.forName("UTF-8"));
    }

    /**
     * Set listener that will be used for printing out messages instead default listener.
     */
    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new DockerComputerSingle(this, activityId);
    }


    @Override
    public void terminate() throws InterruptedException, IOException {
        try {
            _terminate(getListener());
        } finally {
            try {
                Jenkins.getInstance().removeNode(this);
            } catch (IOException e) {
                LOG.warn("Failed to remove {}", name, e);
                getListener().error("Failed to remove " + name);
            }
        }
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        boolean failure = false;
        final DockerContainerLifecycle dockerContainerLifecycle = config.getDockerContainerLifecycle();
        try {
            LOG.info("Requesting disconnect for computer: '{}'", name);
            final Computer toComputer = toComputer();
            if (toComputer != null) {
                toComputer.disconnect(new DockerOfflineCause("Terminating from _terminate."));
            }
        } catch (Exception e) {
            LOG.error("Can't disconnect computer: '{}'", name, e);
            listener.error("Can't disconnect computer: " + name);
        }

        if (StringUtils.isNotBlank(getContainerId())) {
            try {
                dockerContainerLifecycle.getStopContainer().exec(connector.getClient(), getContainerId());
                LOG.info("Stopped container {}", getContainerId());
                listener.getLogger().println("Stopped container " + getContainerId());
            } catch (NotModifiedException ex) {
                LOG.info("Container '{}' is already stopped.", getContainerId());
            } catch (Exception ex) {
                LOG.error("Failed to stop instance '{}' for slave '{}' due to exception: {}",
                        getContainerId(), name, ex.getMessage());
                failure = true;
            }

            final Computer computer = toComputer();
            if (computer instanceof DockerComputerSingle) {
                final DockerComputerSingle dockerComputer = (DockerComputerSingle) computer;
                for (DockerTerminateCmdAction a : dockerComputer.getActions(DockerTerminateCmdAction.class)) {
                    try {
                        a.exec(connector.getClient(), getContainerId());
                    } catch (Exception e) {
                        LOG.error("Failed execute action {}", a, e);
                        listener.error("Failed execute " + a.getDisplayName());
                    }
                }
            } else {
                LOG.error("Computer '{}' is not DockerComputerSingle", computer);
                listener.error("Computer ' " + computer + "' is not DockerComputerSingle", computer);
            }

            try {
                dockerContainerLifecycle.getRemoveContainer().exec(connector.getClient(), getContainerId());
                LOG.info("Removed container {}", getContainerId());
                listener.getLogger().println("Removed container " + getContainerId());
            } catch (Exception ex) {
                LOG.error("Failed to remove instance '{}' for slave '{}' due to exception: {}",
                        getContainerId(), name, ex.getMessage());
                listener.error("failed to remove " + getContainerId());
            }
        } else {
            LOG.error("ContainerId is absent, no way to remove/stop container");
            listener.error("ContainerId is absent, no way to remove/stop container");
            failure = true;
        }

        ProvisioningActivity activity = CloudStatistics.get().getActivityFor(this);
        if (nonNull(activity)) {
            activity.enterIfNotAlready(ProvisioningActivity.Phase.COMPLETED);
        }
    }

    @Nonnull
    @Override
    public ProvisioningActivity.Id getId() {
        return activityId;
    }
}
