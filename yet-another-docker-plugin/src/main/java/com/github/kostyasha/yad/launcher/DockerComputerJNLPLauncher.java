package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.OsType;
import com.github.kostyasha.yad.DockerSlave;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.ExecStartResultCallback;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils.isNotEmpty;
import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils.trimToEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * JNLP launcher. Doesn't require open ports on docker host.
 * <p>
 * Steps:
 * - runs container with nop command
 * - as launch action executes jnlp connection to master
 *
 * @author Kanstantsin Shautsou
 */
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DockerComputerJNLPLauncher.class);
    private static final String NL = "\"\n";
    private static final String WNL = "'\n";
    public static final long DEFAULT_TIMEOUT = 120L;
    public static final String DEFAULT_USER = "jenkins";

    protected long launchTimeout = DEFAULT_TIMEOUT; //seconds

    protected String user = "jenkins";

    protected String jvmOpts = "";

    protected String slaveOpts = "";

    protected String jenkinsUrl = "";

    protected boolean noCertificateCheck = false;

    protected boolean reconnect = false;

    @DataBoundConstructor
    public DockerComputerJNLPLauncher() {
        super(null);
    }

    @DataBoundSetter
    public void setSlaveOpts(String slaveOpts) {
        this.slaveOpts = trimToEmpty(slaveOpts);
    }

    @Nonnull
    public String getSlaveOpts() {
        return trimToEmpty(slaveOpts);
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = trimToEmpty(jenkinsUrl);
    }

    @Nonnull
    public String getJenkinsUrl() {
        return trimToEmpty(jenkinsUrl);
    }

    @Nonnull
    public String getJenkinsUrl(String rootUrl) {
        return isNotEmpty(jenkinsUrl) ? jenkinsUrl : trimToEmpty(rootUrl);
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
    public void setNoReconnect(boolean noReconnect) {
        this.reconnect = !noReconnect;
    }

    public boolean isNoReconnect() {
        return !reconnect;
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

    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION")
    @Override
    public void launch(@Nonnull SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        DockerComputer dockerComputer;
        if (computer instanceof DockerComputer) {
            dockerComputer = (DockerComputer) computer;
        } else {
            listener.error("Docker JNLP Launcher accepts only DockerComputer.class");
            throw new IllegalArgumentException("Docker JNLP Launcher accepts only DockerComputer.class");
        }
        Objects.requireNonNull(dockerComputer);

        final String containerId = dockerComputer.getContainerId();
        final DockerCloud dockerCloud = dockerComputer.getCloud();
//        Objects.requireNonNull(dockerCloud, "Cloud not found for computer " + computer.getName());
        if (isNull(dockerCloud)) {
            listener.error("Cloud not found cloud for computer " + computer.getName());
            throw new IllegalStateException("Cloud not found cloud for computer " + computer.getName());
        }
        final DockerClient client = dockerCloud.getClient();
        final DockerSlave node = dockerComputer.getNode();
        if (isNull(node)) {
            listener.error("Failed to run container for %s", computer.getName());
            printLog(client, listener, containerId);
            throw new IllegalStateException("Node can't be null, probably remote node disappeared.");
        }
        final DockerSlaveTemplate dockerSlaveTemplate = node.getDockerSlaveTemplate();
        final DockerComputerJNLPLauncher launcher = (DockerComputerJNLPLauncher) dockerSlaveTemplate.getLauncher();

        final String rootUrl = launcher.getJenkinsUrl(Jenkins.getInstance().getRootUrl());
//        Objects.requireNonNull(rootUrl, "Jenkins root url is not specified!");
        if (isNull(rootUrl)) {
            throw new IllegalStateException("Jenkins root url is not specified!");
        }

        // exec jnlp connection in running container
        // TODO implement PID 1 replacement

        try {
            String startCmd = null;
            ExecCreateCmdResponse response = null;
            logger.println("Creating exec command...");

            if (dockerSlaveTemplate.getOsType() == OsType.WINDOWS) {
                startCmd =
                        "@\"\n" +
                                "`$JENKINS_URL='" + rootUrl + WNL +
                                "`$JENKINS_USER='" + getUser() + WNL +
                                "`$JENKINS_HOME='" + dockerSlaveTemplate.getRemoteFs() + WNL +
                                "`$COMPUTER_URL='" + dockerComputer.getUrl() + WNL +
                                "`$COMPUTER_SECRET='" + dockerComputer.getJnlpMac() + WNL +
                                "`$JAVA_OPTS='" + getJvmOpts() + WNL +
                                "`$SLAVE_OPTS='" + getSlaveOpts() + WNL +
                                "`$NO_CERTIFICATE_CHECK='" + isNoCertificateCheck() + WNL +
                                "`$NO_RECONNECT_SLAVE='" + isNoReconnect() + WNL +
                                "\"@ | Out-File -FilePath c:\\config.ps1";

                response = client.execCreateCmd(containerId)
                        .withTty(true)
                        .withAttachStdin(false)
                        .withAttachStderr(true)
                        .withAttachStdout(true)
                        .withCmd("powershell.exe", startCmd)
                        .exec();

            } else { // default is OsType.LINUX
                startCmd =
                        "cat << EOF > /tmp/config.sh.tmp && cd /tmp && mv config.sh.tmp config.sh\n" +
                                "JENKINS_URL=\"" + rootUrl + NL +
                                "JENKINS_USER=\"" + getUser() + NL +
                                "JENKINS_HOME=\"" + dockerSlaveTemplate.getRemoteFs() + NL +
                                "COMPUTER_URL=\"" + dockerComputer.getUrl() + NL +
                                "COMPUTER_SECRET=\"" + dockerComputer.getJnlpMac() + NL +
                                "JAVA_OPTS=\"" + getJvmOpts() + NL +
                                "SLAVE_OPTS=\"" + getSlaveOpts() + NL +
                                "NO_CERTIFICATE_CHECK=\"" + isNoCertificateCheck() + NL +
                                "NO_RECONNECT_SLAVE=\"" + isNoReconnect() + NL +
                                "EOF" + "\n";

                response = client.execCreateCmd(containerId)
                        .withTty(true)
                        .withAttachStdin(false)
                        .withAttachStderr(true)
                        .withAttachStdout(true)
                        .withCmd("/bin/sh", "-cxe", startCmd.replace("$", "\\$"))
                        .exec();
            }

            LOG.info("Starting connection command for {}...", containerId);
            logger.println("Starting connection command for " + containerId);

            try (ExecStartResultCallback exec = client
                    .execStartCmd(response.getId())
                    .withDetach(true)
                    .withTty(true)
                    .exec(new ExecStartResultCallback(logger, logger))
            ) {
                exec.awaitCompletion();
            } catch (NotFoundException ex) {
                listener.error("Can't execute JNLP command: " + ex.getMessage().trim());
                LOG.error("Can't execute JNLP command: '{}'", ex.getMessage().trim());
                throw ex;
            }
        } catch (Exception ex) {
            listener.error("Can't execute JNLP connection command: " + ex.getMessage().trim());
            LOG.error("Can't execute JNLP connection command: '{}'", ex.getMessage().trim());
            printLog(client, listener, containerId);
            node.terminate();
            throw ex;
        }

        LOG.info("Successfully executed jnlp connection for '{}'", containerId);
        logger.println("Successfully executed jnlp connection for " + containerId);

        // TODO better strategy
        final long launchTime = System.currentTimeMillis();
        while (!dockerComputer.isOnline() &&
                TimeUnit2.SECONDS.toMillis(launchTimeout) > System.currentTimeMillis() - launchTime) {
            logger.println("Waiting slave connection...");
            Thread.sleep(1000);
        }

        InspectContainerResponse inspect = dockerCloud.getClient().inspectContainerCmd(containerId).exec();
        if (nonNull(inspect) && !Boolean.TRUE.equals(inspect.getState().getRunning())) {
            logger.println("Container is not running: " + inspect.getState().getRunning());
        }

        if (!dockerComputer.isOnline()) {
            LOG.info("Launch timeout, terminating slave based on '{}'", containerId);
            logger.println("Launch timeout, terminating slave.");
            printLog(client, listener, containerId);
            node.terminate();
            throw new IOException("Can't connect slave to jenkins");
        }

        LOG.info("Launched slave '{}' '{}' based on '{}'",
                dockerComputer.getSlaveVersion(), dockerComputer.getName(), containerId);
        logger.println("Launched slave for " + containerId);
    }

    private void printLog(DockerClient client, TaskListener listener, String containerId) {
        try {
            client.logContainerCmd(containerId)
                    .withStdErr(true)
                    .withStdOut(true)
                    .exec(new ListenerLogContainerResultCallback(listener))
                    .awaitCompletion();
        } catch (Exception ex) {
            listener.error("Failed to get logs from container " + containerId);
            LOG.error("failed to get logs from container {}", containerId, ex);
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
    }

    /**
     * Clone object.
     */
    @Override
    public DockerComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate template,
                                                InspectContainerResponse containerInspectResponse) {
        final DockerComputerJNLPLauncher cloneJNLPlauncher = new DockerComputerJNLPLauncher();

        cloneJNLPlauncher.setLaunchTimeout(getLaunchTimeout());
        cloneJNLPlauncher.setUser(getUser());
        cloneJNLPlauncher.setJvmOpts(getJvmOpts());
        cloneJNLPlauncher.setSlaveOpts(getSlaveOpts());
        cloneJNLPlauncher.setJenkinsUrl(getJenkinsUrl());
        cloneJNLPlauncher.setNoCertificateCheck(isNoCertificateCheck());
        cloneJNLPlauncher.setNoReconnect(isNoReconnect());

        return cloneJNLPlauncher;
    }

    @Override
    public ComputerLauncher getLauncher() {
        return new JNLPLauncher();
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate, CreateContainerCmd createContainerCmd)
            throws IOException {

        if (dockerSlaveTemplate.getOsType() == OsType.WINDOWS) {
            try (InputStream inStream = DockerComputerJNLPLauncher.class.getResourceAsStream("DockerComputerJNLPLauncher/init.ps1")) {
                final String initCmd = IOUtils.toString(inStream, Charsets.UTF_8);
                if (initCmd == null) {
                    throw new IllegalStateException("Resource file 'init.ps1' not found");
                }

                createContainerCmd.withEntrypoint("powershell.exe")
                        .withCmd("powershell.exe", "-NoLogo", "-ExecutionPolicy", "bypass", "-Command", "{@\"\n" +
                        initCmd.replace("$", "`$") +
                        "\n\"@ | Out-File -FilePath c:\\init.ps1 ; if($?) {c:\\init.ps1}}");
            }
        } else { // default is OsType.LINUX
            try (InputStream inStream = DockerComputerJNLPLauncher.class.getResourceAsStream("DockerComputerJNLPLauncher/init.sh")) {
                final String initCmd = IOUtils.toString(inStream, Charsets.UTF_8);
                if (initCmd == null) {
                    throw new IllegalStateException("Resource file 'init.sh' not found");
                }

                // wait for params
                createContainerCmd.withEntrypoint("/bin/sh",
                        "-cxe",
                        "cat << EOF >> /tmp/init.sh && chmod +x /tmp/init.sh && exec /tmp/init.sh\n" +
                                initCmd.replace("$", "\\$") + "\n" +
                                "EOF" + "\n");
            }
        }

        createContainerCmd.withTty(true);
        createContainerCmd.withStdinOpen(true);
    }

    @Override
    public boolean waitUp(String cloudId, DockerSlaveTemplate dockerSlaveTemplate, InspectContainerResponse ir) {
        return super.waitUp(cloudId, dockerSlaveTemplate, ir);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Extension
    public static class DescriptorImpl extends DelegatingComputerLauncher.DescriptorImpl {
        public Class getJNLPLauncher() {
            return JNLPLauncher.class;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker JNLP launcher";
        }
    }
}
