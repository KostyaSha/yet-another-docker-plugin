package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
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
    public static final long DEFAULT_TIMEOUT = 120L;
    public static final String DEFAULT_USER = "jenkins";

    protected long launchTimeout = DEFAULT_TIMEOUT; //seconds

    protected String user = "jenkins";

    protected String jvmOpts = "";

    protected String slaveOpts = "";

    protected String jenkinsUrl = "";

    protected boolean noCertificateCheck = false;

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
            listener.error("Cloud not found for computer " + computer.getName());
            throw new NullPointerException("Cloud not found for computer " + computer.getName());
        }
        final DockerClient connect = dockerCloud.getClient();
        final DockerSlave node = dockerComputer.getNode();
        if (isNull(node)) {
            throw new NullPointerException("Node can't be null");
        }
        final DockerSlaveTemplate dockerSlaveTemplate = node.getDockerSlaveTemplate();
        final DockerComputerJNLPLauncher launcher = (DockerComputerJNLPLauncher) dockerSlaveTemplate.getLauncher();

        final String rootUrl = launcher.getJenkinsUrl(Jenkins.getActiveInstance().getRootUrl());
//        Objects.requireNonNull(rootUrl, "Jenkins root url is not specified!");
        if (isNull(rootUrl)) {
            throw new NullPointerException("Jenkins root url is not specified!");
        }

        // exec jnlp connection in running container
        // TODO implement PID 1 replacement
        String startCmd =
                "cat << EOF > /tmp/config.sh.tmp && cd /tmp && mv config.sh.tmp config.sh\n" +
                        "JENKINS_URL=\"" + rootUrl + NL +
                        "JENKINS_USER=\"" + getUser() + NL +
                        "JENKINS_HOME=\"" + dockerSlaveTemplate.getRemoteFs() + NL +
                        "COMPUTER_URL=\"" + dockerComputer.getUrl() + NL +
                        "COMPUTER_SECRET=\"" + dockerComputer.getJnlpMac() + NL +
                        "JAVA_OPTS=\"" + getJvmOpts() + NL +
                        "SLAVE_OPTS=\"" + getSlaveOpts() + NL +
                        "NO_CERTIFICATE_CHECK=\"" + isNoCertificateCheck() + NL +
                        "EOF" + "\n";

        try {
            final ExecCreateCmdResponse response = connect.execCreateCmd(containerId)
                    .withTty(true)
                    .withAttachStdin(false)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withCmd("/bin/sh", "-cxe", startCmd.replace("$", "\\$"))
                    .exec();

            LOG.info("Starting connection command for {}", containerId);
            logger.println("Starting connection command for " + containerId);

            try (ExecStartResultCallback exec = connect
                    .execStartCmd(response.getId())
                    .withDetach(true)
                    .withTty(true)
                    .exec(new ExecStartResultCallback())
            ) {
                exec.awaitCompletion();
            } catch (NotFoundException ex) {
                listener.error("Can't execute command: " + ex.getMessage().trim());
                LOG.error("Can't execute jnlp connection command: '{}'", ex.getMessage().trim());
                throw ex;
            }
        } catch (Exception ex) {
            listener.error("Can't execute command: " + ex.getMessage().trim());
            LOG.error("Can't execute jnlp connection command: '{}'", ex.getMessage().trim());
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

        if (!dockerComputer.isOnline()) {
            LOG.info("Launch timeout, termintaing slave based on '{}'", containerId);
            logger.println("Launch timeout, termintaing slave.");
            node.terminate();
            throw new IOException("Can't connect slave to jenkins");
        }

        LOG.info("Launched slave '{}' '{}' based on '{}'",
                dockerComputer.getSlaveVersion(), dockerComputer.getName(), containerId);
        logger.println("Launched slave for " + containerId);
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
    public ComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate template,
                                                InspectContainerResponse containerInspectResponse) {
        final DockerComputerJNLPLauncher cloneJNLPlauncher = new DockerComputerJNLPLauncher();

        cloneJNLPlauncher.setLaunchTimeout(getLaunchTimeout());
        cloneJNLPlauncher.setUser(getUser());
        cloneJNLPlauncher.setJvmOpts(getJvmOpts());
        cloneJNLPlauncher.setSlaveOpts(getSlaveOpts());
        cloneJNLPlauncher.setJenkinsUrl(getJenkinsUrl());
        cloneJNLPlauncher.setNoCertificateCheck(isNoCertificateCheck());

        return cloneJNLPlauncher;
    }

    @Override
    public ComputerLauncher getLauncher() {
        return new JNLPLauncher();
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate, CreateContainerCmd createContainerCmd)
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
