package com.github.kostyasha.yad;

import com.github.kostyasha.yad.action.DockerTerminateCmdAction;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.NotModifiedException;
import com.github.kostyasha.yad.docker_java.com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;


/**
 * Jenkins Slave with yad specific configuration
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD",
        justification = "Broken serialization https://issues.jenkins-ci.org/browse/JENKINS-31916")
public class DockerSlave extends AbstractCloudSlave {
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

    public DockerSlave(String slaveName, String nodeDescription, ComputerLauncher launcher, String containerId,
                       DockerSlaveTemplate dockerSlaveTemplate, String cloudId)
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
        final Cloud cloud = Jenkins.getActiveInstance().getCloud(getCloudId());

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
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final DockerContainerLifecycle dockerContainerLifecycle = dockerSlaveTemplate.getDockerContainerLifecycle();
        try {
            LOG.info("Requesting disconnect for computer: '{}'", name);
            final Computer toComputer = toComputer();
            if (toComputer != null) {
                toComputer.disconnect(new DockerOfflineCause());
            }
        } catch (Exception e) {
            LOG.error("Can't disconnect computer: '{}'", name, e);
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
                LOG.error("Failed to remove instance '{}' for slave '{}' due to exception: {}",
                        getContainerId(), name, ex.getMessage());
            }
        } else {
            LOG.error("ContainerId is absent, no way to remove/stop container");
        }
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
