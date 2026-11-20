package com.github.kostyasha.yad;

import com.github.kostyasha.yad.commons.AbstractCloud;
import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.launcher.DockerComputerLauncher;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectImageResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.DockerException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Container;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.async.ResultCallback;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Throwables;
import com.github.kostyasha.yad_docker_java.javax.ws.rs.ProcessingException;
import org.apache.commons.lang3.StringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.isNull;
import static org.jenkinsci.plugins.cloudstats.CloudStatistics.ProvisioningListener.get;

/**
 * Docker Jenkins Cloud configuration. Contains connection configuration,
 * {@link DockerSlaveTemplate} contains configuration for running docker image.
 */
@SuppressFBWarnings(value = {"SE_BAD_FIELD", "SE_NO_SUITABLE_CONSTRUCTOR"})
public class DockerCloud extends AbstractCloud implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DockerCloud.class);

    private static final int START_FAILURE_LOG_TIMEOUT_SECONDS = 5;

    private static final String DOCKER_CLOUD_LABEL = DockerCloud.class.getName();
    private static final String DOCKER_TEMPLATE_LABEL = DockerSlave.class.getName();

    private DockerConnector connector;

    @DataBoundConstructor
    public DockerCloud(String name,
                       List<DockerSlaveTemplate> templates,
                       int containerCap,
                       @Nonnull DockerConnector connector) {
        super(name);
        setConnector(connector);
        setTemplates(templates);
        setContainerCap(containerCap);
    }

    //    @CheckForNull
    public DockerConnector getConnector() {
        return connector;
    }

    public DockerCloud setConnector(DockerConnector connector) {
        this.connector = connector;
        return this;
    }

    /**
     * Connects to Docker.
     *
     * @return Docker client.
     */
    public synchronized DockerClient getClient() {
        return getConnector().getClient();
    }

    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "docker-java uses runtime exceptions")
    @Nonnull
    @Override
    public synchronized Collection<PlannedNode> provision(CloudState state, int excessWorkload) {
        final Label label = state.getLabel();
        LOG.info("Asked to provision load: '{}', for: '{}' label", excessWorkload, label);

        List<PlannedNode> r = new ArrayList<>(excessWorkload);
        final List<DockerSlaveTemplate> tryTemplates = getTemplates(label);

        while (excessWorkload > 0 && !tryTemplates.isEmpty()) {
            final DockerSlaveTemplate t = tryTemplates.get(0); // get first

            LOG.info("Will provision '{}', for label: '{}', in cloud: '{}'",
                    t.getDockerContainerLifecycle().getImage(), label, getDisplayName());

            try {
                if (!addProvisionedSlave(t)) {
                    tryTemplates.remove(t);
                    continue;
                }
            } catch (Exception e) {
                LOG.warn("Bad template '{}' in cloud '{}': '{}'. Trying next template...",
                        t.getDockerContainerLifecycle().getImage(), getDisplayName(), e.getMessage(), e);
                tryTemplates.remove(t);

                continue;
            }

            final ProvisioningActivity.Id id = new ProvisioningActivity.Id(getDisplayName(),
                    t.getDockerContainerLifecycle().getImage());

            r.add(new TrackedPlannedNode(
                            id,
                            t.getNumExecutors(),
                            Computer.threadPoolForRemoting.submit(() -> {
                                get().onStarted(id);
                                try {
                                    final DockerSlave dockerSlave = provisionWithWait(t, id);
                                    get().onComplete(id, dockerSlave); //rename
                                    return dockerSlave;
                                } catch (Exception ex) {
                                    LOG.error("Error in provisioning; template='{}' for cloud='{}'",
                                            t, getDisplayName(), ex);
                                    get().onFailure(id, ex);
                                    throw Throwables.propagate(ex);
                                } finally {
                                    decrementAmiSlaveProvision(t);
                                }
                            })
                    )
            );
            excessWorkload -= t.getNumExecutors();
        }

        return r;
    }


    /**
     * Run docker container for given template
     *
     * @return container id
     */
    public String runContainer(DockerSlaveTemplate slaveTemplate) throws DockerException, IOException {
        DockerContainerLifecycle lifecycle = slaveTemplate.getDockerContainerLifecycle();
        DockerCreateContainer dockerCreateContainer = lifecycle.getCreateContainer();
        DockerClient client = getClient();

        CreateContainerCmd containerConfig = client.createContainerCmd(lifecycle.getImage());

        // template specific options
        dockerCreateContainer.fillContainerConfig(containerConfig, null);

        // launcher specific options
        slaveTemplate.getLauncher().appendContainerConfig(slaveTemplate, containerConfig);

        // cloud specific options
        appendContainerConfig(slaveTemplate, containerConfig);

        // create
        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();
        LOG.debug("Created container {}, for {}", containerId, getDisplayName());

        try {
            slaveTemplate.getLauncher().afterContainerCreate(client, containerId);
            client.startContainerCmd(containerId).exec();
            LOG.debug("Running container {}, for {}", containerId, getDisplayName());
        } catch (Exception failure) {
            cleanUpFailedStart(client, lifecycle, containerId, failure);
            throw failure;
        }

        return containerId;
    }

    private void cleanUpFailedStart(DockerClient client, DockerContainerLifecycle lifecycle,
                                   String containerId, Exception failure) {
        boolean interrupted = false;
        try {
            readFailedStartLogs(client, containerId);
        } catch (TimeoutException ex) {
            LOG.warn("Timed out reading logs after failed start of container {}", containerId);
        } catch (ExecutionException ex) {
            LOG.warn("Can't get logs for failed container {}", containerId, ex.getCause());
            failure.addSuppressed(ex.getCause());
        } catch (InterruptedException ex) {
            interrupted = true;
            failure.addSuppressed(ex);
        } catch (Exception ex) {
            LOG.warn("Can't get logs for failed container {}", containerId, ex);
            failure.addSuppressed(ex);
        } finally {
            try {
                lifecycle.getRemoveContainer().exec(client, containerId);
            } catch (InterruptedException ex) {
                interrupted = true;
                failure.addSuppressed(ex);
            } catch (Exception ex) {
                LOG.warn("Can't remove container {} after failed start", containerId, ex);
                failure.addSuppressed(ex);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void readFailedStartLogs(DockerClient client, String containerId)
            throws InterruptedException, ExecutionException, TimeoutException {
        // Include opening the stream in the deadline, since some transports connect synchronously.
        Future<?> logs = Computer.threadPoolForRemoting.submit(() -> {
            try (MyLogContainerResultCallback callback = new MyLogContainerResultCallback()) {
                client.logContainerCmd(containerId)
                        .withStdErr(true)
                        .withStdOut(true)
                        .exec(callback);
                callback.awaitCompletion();
            }
            return null;
        });
        try {
            logs.get(START_FAILURE_LOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            logs.cancel(true);
        }
    }

    /**
     * Cloud specific container config options
     */

    private void appendContainerConfig(DockerSlaveTemplate slaveTemplate, CreateContainerCmd containerConfig) {
        Map<String, String> labels = containerConfig.getLabels();
        if (labels == null) labels = new HashMap<>();

        labels.put(DOCKER_CLOUD_LABEL, getDisplayName());
        labels.put(DOCKER_TEMPLATE_LABEL, slaveTemplate.getId());

        containerConfig.withLabels(labels);
    }

    /**
     * Provision slave container and wait for it's availability.
     */
    private DockerSlave provisionWithWait(DockerSlaveTemplate template, ProvisioningActivity.Id id)
            throws Exception {
        final DockerContainerLifecycle dockerContainerLifecycle = template.getDockerContainerLifecycle();
        final String imageId = dockerContainerLifecycle.getImage();
        final DockerComputerLauncher computerLauncher = template.getLauncher();

        //pull image
        dockerContainerLifecycle.getPullImage().exec(getClient(), imageId, TaskListener.NULL);

        // set the operating system if it's not already cached
        if (template.getOsType() == null) {
            template.setOsType(determineOsType(imageId));
        }

        LOG.debug("Trying to run container for {}, operating system {}", imageId, template.getOsType());
        final String containerId = runContainer(template);

        InspectContainerResponse ir;
        try {
            ir = getClient().inspectContainerCmd(containerId).exec();
        } catch (ProcessingException ex) {
            LOG.error("Failed to run container for {}, removing container", imageId);
            dockerContainerLifecycle.getRemoveContainer().exec(getClient(), containerId);
            throw ex;
        }

        String nodeDescription = String.format("Docker Node [%s on %s]", imageId, getDisplayName());

        String slaveName = String.format("%s-%s", getDisplayName(), containerId.substring(0, 12));

        try {
            if (computerLauncher.waitUp(getDisplayName(), template, ir)) {
                LOG.debug("Container {} is ready for slave connection", containerId);
            } else {
                LOG.error("Container {} is not ready for slave connection.", containerId);
                throw new IllegalStateException("Failed to run container.");
            }
        } catch (RuntimeException ex) {
            LOG.error("Removing {} container.", containerId);
            dockerContainerLifecycle.getRemoveContainer().exec(getClient(), containerId);
            throw ex;
        }

        final DockerComputerLauncher launcher = computerLauncher.getPreparedLauncher(getDisplayName(), template, ir);

        return getDockerSlaveWithRetry(template, id, containerId, nodeDescription, slaveName, launcher);
    }

    private DockerSlave getDockerSlaveWithRetry(DockerSlaveTemplate template, ProvisioningActivity.Id id,
                                                String containerId, String nodeDescription, String slaveName,
                                                DockerComputerLauncher launcher)
            throws IOException, Descriptor.FormException, InterruptedException {
        int retries = 6;
        while (retries >= 0) {
            try {
                return new DockerSlave(slaveName, nodeDescription, launcher, containerId, template, getDisplayName(), id);
            } catch (IOException t) {
                if (retries <= 0) {
                    throw t;
                }
                LOG.trace("Failed to create DockerSlaveSingle, retrying...", t);
                Thread.sleep(1000);
            } finally {
                retries--;
            }
        }

        throw new IllegalStateException("Max creation retries");
    }

    /**
     * Determine the operating system associated with an image.
     */
    private OsType determineOsType(String imageId) {
        InspectImageResponse ir = getClient().inspectImageCmd(imageId).exec();
        if ("linux".equalsIgnoreCase(ir.getOs())) {
            LOG.trace("Detected LINUX operating system for image: {}", imageId);
            return OsType.LINUX;
        } else if ("windows".equalsIgnoreCase(ir.getOs())) {
            LOG.trace("Detected WINDOWS operating system for image: {}", imageId);
            return OsType.WINDOWS;
        } else {
            LOG.trace("Detected OTHER operating system ({}) for image: {}", ir.getOs(), imageId);
            return OsType.OTHER;
        }
    }

    /**
     * Counts the number of instances in Docker currently running that are using the specified template.
     *
     * @param template If null, then all instances are counted.
     */
    public int countCurrentDockerSlaves(final DockerSlaveTemplate template) throws Exception {
        int count = 0;
        List<Container> containers = getClient().listContainersCmd().exec();

        for (Container container : containers) {
            final Map<String, String> labels = container.getLabels();
            // broken container in daemon may return null labels
            if (labels != null && labels.containsKey(DOCKER_CLOUD_LABEL) && labels.get(DOCKER_CLOUD_LABEL).equals(getDisplayName())) {
                if (template == null) {
                    // count only total cloud capacity
                    count++;
                } else if (labels.containsKey(DOCKER_TEMPLATE_LABEL) &&
                        labels.get(DOCKER_TEMPLATE_LABEL).equals(template.getId())) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Check not too many already running.
     */
    private synchronized boolean addProvisionedSlave(DockerSlaveTemplate template) throws Exception {
        final DockerContainerLifecycle dockerCreateContainer = template.getDockerContainerLifecycle();
        String dockerImageName = dockerCreateContainer.getImage();
        int templateCapacity = template.getMaxCapacity();

        int estimatedTotalSlaves = countCurrentDockerSlaves(null);
        int estimatedAmiSlaves = countCurrentDockerSlaves(template);

        synchronized (provisionedImages) {
            int currentProvisioning = 0;
            if (provisionedImages.containsKey(template)) {
                currentProvisioning = provisionedImages.get(template);
            }

            for (int amiCount : provisionedImages.values()) {
                estimatedTotalSlaves += amiCount;
            }

            estimatedAmiSlaves += currentProvisioning;

            if (estimatedTotalSlaves >= getContainerCap()) {
                LOG.info("Not Provisioning '{}'; Server '{}' full with '{}' container(s)",
                        dockerImageName, name, getContainerCap());
                return false;      // maxed out
            }

            if (templateCapacity != 0 && estimatedAmiSlaves >= templateCapacity) {
                LOG.info("Not Provisioning '{}'. Instance limit of '{}' reached on server '{}'",
                        dockerImageName, templateCapacity, name);
                return false;      // maxed out
            }

            LOG.info("Provisioning '{}' number '{}' on '{}'; Total containers: '{}'",
                    dockerImageName, estimatedAmiSlaves, name, estimatedTotalSlaves);

            provisionedImages.put(template, currentProvisioning + 1);
            return true;
        }
    }

    //    @CheckForNull
    public static DockerCloud getCloudByName(String name) {
        final Cloud cloud = Jenkins.get().getCloud(name);
        if (cloud instanceof DockerCloud) {
            return (DockerCloud) cloud;
        }

        if (isNull(cloud)) {
            throw new RuntimeException("Cloud " + name + "not found");
        }

        return null;
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Xstream can unject nulls")
    public Object readResolve() {
        // real @Nonnull
        if (isNull(templates)) {
            templates = Collections.emptyList();
        }
        //Xstream is not calling readResolve() for nested Describable's
        for (DockerSlaveTemplate template : getTemplates()) {
            template.readResolve();
        }
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerCloud that = (DockerCloud) o;
        if (!name.equals(that.name)) return false;
        if (containerCap != that.containerCap) return false;
        if (!templates.equals(that.templates)) return false;
        return !(connector != null ? !connector.equals(that.connector) : that.connector != null);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        public FormValidation doCheckName(@QueryParameter String name) {
            if (StringUtils.isEmpty(name)) {
                return FormValidation.error("Provide a name for this Cloud.");
            }

            return FormValidation.ok();
        }

        public List<Descriptor> getTemplatesDescriptors() {
            return Arrays.asList(jenkins.model.Jenkins.get().getDescriptor(DockerSlaveTemplate.class));
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Yet Another Docker";
        }
    }

    private static class MyLogContainerResultCallback extends ResultCallback.Adapter<Frame> {
        @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING")
        @Override
        public void onNext(Frame item) {
            final String line = new String(item.getPayload()).trim();
            LOG.debug("Log container: {}", line);
            super.onNext(item);
        }
    }
}
