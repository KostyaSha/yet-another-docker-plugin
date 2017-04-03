package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerComputerSingle;
import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.DockerSlaveSingle;
import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.StartContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.kostyasha.yad_docker_java.javax.ws.rs.ProcessingException;
import com.google.common.base.Throwables;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils.isNotEmpty;
import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils.trimToEmpty;
import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang.BooleanUtils.isTrue;

/**
 * Alternative {@link DockerComputerJNLPLauncher}.
 * <p>
 * Used for {@link DockerSlaveSingle} {@link DockerComputerSingle}
 *
 * @author Kanstantsin Shautsou
 */
public class DockerComputerSingleJNLPLauncher extends JNLPLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DockerComputerSingleJNLPLauncher.class);

    private static final String NL = "\"\n";
    public static final long DEFAULT_TIMEOUT = 120L;
    public static final String DEFAULT_USER = "jenkins";

    protected long launchTimeout = DEFAULT_TIMEOUT; //seconds

    protected String user = DEFAULT_USER;

    protected String jvmOpts = "";

    protected String slaveOpts = "";

    protected String jenkinsUrl = "";

    protected boolean noCertificateCheck = false;

    private String containerId;

    @DataBoundSetter
    public void setSlaveOpts(String slaveOpts) {
        this.slaveOpts = trimToEmpty(slaveOpts);
    }

    @Nonnull
    public String getSlaveOpts() {
        return trimToEmpty(slaveOpts);
    }

    @DataBoundSetter
    public void setJvmOpts(String jvmOpts) {
        this.jvmOpts = trimToEmpty(jvmOpts);
    }

    @Nonnull
    public String getJvmOpts() {
        return trimToEmpty(jvmOpts);
    }

    @DataBoundSetter
    public void setNoCertificateCheck(boolean noCertificateCheck) {
        this.noCertificateCheck = noCertificateCheck;
    }

    public boolean isNoCertificateCheck() {
        return noCertificateCheck;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = trimToEmpty(user);
    }

    public String getUser() {
        return trimToEmpty(user);
    }

    public long getLaunchTimeout() {
        return launchTimeout;
    }

    @DataBoundSetter
    public void setLaunchTimeout(long launchTimeout) {
        this.launchTimeout = launchTimeout;
    }

    @Nonnull
    public String getJenkinsUrl(String rootUrl) {
        return isNotEmpty(jenkinsUrl) ? jenkinsUrl : trimToEmpty(rootUrl);
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = trimToEmpty(jenkinsUrl);
    }

    private void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    /**
     * @return not null when container launched.
     */
    @CheckForNull
    public String getContainerId() {
        return containerId;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        listener.getLogger().println("Launching " + computer.getDisplayName());
        try {
            if (!(computer instanceof DockerComputerSingle)) {
                throw new IllegalStateException(computer.getName() + " not instance of DockerComputerSingle");
            }
            provisionWithWait((DockerComputerSingle) computer, listener);
        } catch (Throwable e) {
            LOG.error("Can't launch ", e);
            listener.error("Can't launch " + e.getMessage());
            Throwables.propagate(e);
        }
    }

    /**
     * Provision slave container and wait for it's availability.
     */
    private void provisionWithWait(DockerComputerSingle computer, TaskListener listener)
            throws Exception {
        final PrintStream logger = listener.getLogger();

        final Run run = computer.getRun();
        final DockerSlaveSingle slave = computer.getNode();
        if (isNull(slave)) {
            throw new IllegalStateException("Can't get slave for " + computer.getNode());
        }
        final DockerClient client = slave.getConnector().getClient();
        final DockerContainerLifecycle containerLifecycle = slave.getConfig().getDockerContainerLifecycle();

        final String imageId = containerLifecycle.getImage();

        //pull image
        logger.println("Pulling image " + imageId + "...");
        containerLifecycle.getPullImage().exec(client, imageId);

        logger.println("Trying to run container for " + imageId);
        LOG.info("Trying to run container for {}", imageId);
        final DockerCreateContainer createContainer = containerLifecycle.getCreateContainer();
        CreateContainerCmd containerConfig = client.createContainerCmd(imageId);
        // template specific options
        createContainer.fillContainerConfig(containerConfig);

        // cloud specific options
        appendContainerConfig(containerConfig);

        // create
        CreateContainerResponse createResp = containerConfig.exec();
        String cId = createResp.getId();
        setContainerId(cId);
        logger.println("Created container " + cId + ", for " + run.getDisplayName());
        LOG.debug("Created container {}, for {}", cId, run.getDisplayName());
        // start
        StartContainerCmd startCommand = client.startContainerCmd(cId);
        startCommand.exec();
        logger.println("Started container " + cId);
        LOG.debug("Start container {}, for {}", cId, run.getDisplayName());

        boolean running = false;
        long launchTime = System.currentTimeMillis();
        while (!running &&
                TimeUnit2.SECONDS.toMillis(launchTimeout) > System.currentTimeMillis() - launchTime) {
            try {
                InspectContainerResponse inspectResp = client.inspectContainerCmd(cId).exec();
                if (isTrue(inspectResp.getState().getRunning())) {
                    logger.println("Container is running!");
                    LOG.debug("Container {} is running", cId);
                    running = true;
                } else {
                    logger.println("Container is not running...");
                }
            } catch (ProcessingException ignore) {
            }
            Thread.sleep(1000);
        }

        if (!running) {
            listener.error("Failed to run container for %s, clean-up container", imageId);
            LOG.error("Failed to run container for {}, clean-up container", imageId);
            try {
                client.logContainerCmd(cId)
                        .withStdErr(true)
                        .withStdOut(true)
                        .exec(new LogContainerResultCallback() {
                            @Override
                            public void onNext(Frame item) {
                                listener.getLogger().println(new String(item.getPayload()).trim());
                                super.onNext(item);
                            }
                        })
                        .awaitCompletion();
            } catch (Exception ex) {
                listener.error("Failed to get logs from container " + cId);
                LOG.error("failed to get logs from container {}", cId, ex);
            }
            throw new IllegalStateException("Container is not running!");
        }

        // now real launch
        final String rootUrl = getJenkinsUrl(Jenkins.getInstance().getRootUrl());
//        Objects.requireNonNull(rootUrl, "Jenkins root url is not specified!");
        if (isNull(rootUrl)) {
            listener.fatalError("Jenkins root url is not specified!");
            containerLifecycle.getRemoveContainer().exec(client, cId);
            throw new IllegalStateException("Jenkins root url is not specified!");
        }
        final DockerSlaveSingle node = computer.getNode();
        if (isNull(node)) {
            throw new NullPointerException("Node can't be null for " + computer.getName());
        }

        // exec jnlp connection in running container
        // TODO implement PID 1 replacement
        String startCmd =
                "cat << EOF > /tmp/config.sh.tmp && cd /tmp && mv config.sh.tmp config.sh\n" +
                        "JENKINS_URL=\"" + rootUrl + NL +
                        "JENKINS_USER=\"" + getUser() + NL +
                        "JENKINS_HOME=\"" + node.getRemoteFS() + NL +
                        "COMPUTER_URL=\"" + computer.getUrl() + NL +
                        "COMPUTER_SECRET=\"" + computer.getJnlpMac() + NL +
                        "JAVA_OPTS=\"" + getJvmOpts() + NL +
                        "SLAVE_OPTS=\"" + getSlaveOpts() + NL +
                        "NO_CERTIFICATE_CHECK=\"" + isNoCertificateCheck() + NL +
                        "EOF" + "\n";

        try {
            final ExecCreateCmdResponse createCmdResponse = client.execCreateCmd(cId)
                    .withTty(true)
                    .withAttachStdin(false)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withCmd("/bin/sh", "-cxe", startCmd.replace("$", "\\$"))
                    .exec();

            logger.println("Starting connection command for " + cId);
            LOG.info("Starting connection command for {}", cId);

            try (ExecStartResultCallback exec = client.execStartCmd(createCmdResponse.getId())
                    .withDetach(true)
                    .withTty(true)
                    .exec(new ExecStartResultCallback())
            ) {
                exec.awaitCompletion(10, SECONDS);
            } catch (NotFoundException ex) {
                listener.error("Can't execute command: " + ex.getMessage().trim());
                LOG.error("Can't execute jnlp connection command: '{}'", ex.getMessage().trim());
                throw ex;
            }
        } catch (Throwable ex) {
            listener.error("Can't execute command: " + ex.getMessage().trim());
            LOG.error("Can't execute jnlp connection command: '{}'", ex.getMessage().trim());
            throw ex;
        }

        LOG.info("Successfully executed jnlp connection for '{}'", cId);
        logger.println("Successfully executed jnlp connection for " + cId);

        // TODO better strategy
        launchTime = System.currentTimeMillis();
        while (computer.isReallyOffline() &&
                TimeUnit2.SECONDS.toMillis(launchTimeout) > System.currentTimeMillis() - launchTime) {
            logger.println("Waiting slave connection...");
            Thread.sleep(1000);
        }

        if (computer.isReallyOffline()) {
            LOG.info("Launch timeout, terminating slave based on '{}'", cId);
            logger.println("Launch timeout, terminating slave.");
            throw new IOException("Can't connect slave to jenkins");
        }

        LOG.info("Launched slave '{}' '{}' based on '{}'",
                computer.getSlaveVersion(), computer.getName(), cId);
        logger.println("Launched slave for " + cId);
    }

    public void appendContainerConfig(CreateContainerCmd createContainerCmd)
            throws IOException {
        try (InputStream instream = DockerComputerJNLPLauncher.class.getResourceAsStream("DockerComputerJNLPLauncher/init.sh")) {
            final String initCmd = IOUtils.toString(instream, Charsets.UTF_8);
            if (initCmd == null) {
                throw new IllegalStateException("Resource file 'init.sh' not found");
            }

            // wait for params
            createContainerCmd.withCmd("/bin/sh",
                    "-cxe",
                    "cat << EOF >> /tmp/init.sh && chmod +x /tmp/init.sh && exec /tmp/init.sh\n" +
                            initCmd.replace("$", "\\$") + "\n" +
                            "EOF" + "\n"
            );
        }

        createContainerCmd.withTty(true);
        createContainerCmd.withStdinOpen(true);
    }
}
