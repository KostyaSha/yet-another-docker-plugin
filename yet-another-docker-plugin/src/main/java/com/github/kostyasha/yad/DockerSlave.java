package com.github.kostyasha.yad;

import com.github.kostyasha.yad.action.DockerTerminateCmdAction;
import com.github.kostyasha.yad.queue.FlyweightCauseOfBlockage;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotModifiedException;
import com.github.kostyasha.yad_docker_java.com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

import static java.util.Objects.nonNull;

/**
 * Jenkins Slave with yad specific configuration.
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD",
        justification = "Broken serialization https://issues.jenkins-ci.org/browse/JENKINS-31916")
public class DockerSlave extends AbstractCloudSlave implements TrackedItem {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DockerSlave.class);

    /**
     * copy of container setting
     */
    protected DockerSlaveTemplate dockerSlaveTemplate;

    /**
     * Remember container id
     */
    @CheckForNull
    private final String containerId;

    /**
     * remember cloud name
     */
    @CheckForNull
    private final String cloudId;

    private String displayName;

    protected ProvisioningActivity.Id provisioningId;

    public DockerSlave(String slaveName, String nodeDescription, ComputerLauncher launcher, String containerId,
                       DockerSlaveTemplate dockerSlaveTemplate, String cloudId, ProvisioningActivity.Id provisioningId)
            throws IOException, Descriptor.FormException {
        super(slaveName,
                nodeDescription, //description
                dockerSlaveTemplate.getRemoteFs(),
                dockerSlaveTemplate.getNumExecutors(),
                dockerSlaveTemplate.getMode(),
                dockerSlaveTemplate.getLabelString(),
                launcher,
                dockerSlaveTemplate.getRetentionStrategyCopy(),
                dockerSlaveTemplate.getNodeProperties()
        );
        this.displayName = slaveName; // initial value
        this.containerId = containerId;
        this.cloudId = cloudId;
        setDockerSlaveTemplate(dockerSlaveTemplate);
        this.provisioningId = provisioningId;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public DockerSlaveTemplate getDockerSlaveTemplate() {
        return dockerSlaveTemplate;
    }

    public void setDockerSlaveTemplate(DockerSlaveTemplate dockerSlaveTemplate) {
        this.dockerSlaveTemplate = dockerSlaveTemplate;
    }

    @Nonnull
    public DockerCloud getCloud() {
        final Cloud cloud = Jenkins.getInstance().getCloud(getCloudId());

        if (cloud == null) {
            throw new RuntimeException("Docker template " + dockerSlaveTemplate + " has no assigned Cloud.");
        }

        if (!cloud.getClass().isAssignableFrom(DockerCloud.class)) {
            throw new RuntimeException("Assigned cloud is not DockerCloud");
        }

        return (DockerCloud) cloud;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    @Override
    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        // hack for some core issue
        if (item.task instanceof Queue.FlyweightTask) {
            return new FlyweightCauseOfBlockage();
        }
        return super.canTake(item);
    }

    public boolean containerExistsInCloud() {
        try {
            DockerClient client = getClient();
            client.inspectContainerCmd(containerId).exec();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Node reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return null;
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final DockerContainerLifecycle dockerContainerLifecycle = dockerSlaveTemplate.getDockerContainerLifecycle();
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

        if (StringUtils.isNotBlank(containerId)) {
            try {
                dockerContainerLifecycle.getStopContainer().exec(getClient(), containerId);
                LOG.info("Stopped container {}", getContainerId());
            } catch (NotModifiedException ex) {
                LOG.info("Container '{}' is already stopped.", getContainerId());
            } catch (Exception ex) {
                LOG.error("Failed to stop instance '{}' for slave '{}' due to exception: {}",
                        getContainerId(), name, ex.getMessage());
            }

            final Computer computer = toComputer();
            if (computer instanceof DockerComputer) {
                final DockerComputer dockerComputer = (DockerComputer) computer;
                for (DockerTerminateCmdAction a : dockerComputer.getActions(DockerTerminateCmdAction.class)) {
                    a.exec(getClient(), containerId);
                }
            } else {
                LOG.error("Computer '{}' is not DockerComputer", computer);
            }

            try {
                dockerContainerLifecycle.getRemoveContainer().exec(getClient(), containerId);
                LOG.info("Removed container {}", getContainerId());
            } catch (Exception ex) {
                String inProgress = "removal of container " + getContainerId() + " is already in progress";
                if (ex.getMessage().contains(inProgress)) {
                    LOG.debug("Removing", ex);
                    listener.getLogger().println(inProgress);
                } else {
                    LOG.error("Failed to remove instance '{}' for slave '{}' due to exception: {}",
                            getContainerId(), name, ex.getMessage());
                }
            }
        } else {
            LOG.error("ContainerId is absent, no way to remove/stop container");
        }

        ProvisioningActivity activity = CloudStatistics.get().getActivityFor(this);
        if (nonNull(activity)) {
            activity.enterIfNotAlready(ProvisioningActivity.Phase.COMPLETED);
        }
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return provisioningId;
    }

    public DockerClient getClient() {
        return getCloud().getClient();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("containerId", containerId)
                .add("template", dockerSlaveTemplate)
                .toString();
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Docker Slave";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
}
