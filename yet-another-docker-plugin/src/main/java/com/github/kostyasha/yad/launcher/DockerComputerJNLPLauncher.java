package com.github.kostyasha.yad.launcher;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.DockerSlave;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.NotFoundException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.kostyasha.yad.docker_java.com.google.common.annotations.Beta;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;

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
@Beta
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DockerComputerJNLPLauncher.class);
    private static final String NL = "\"\n";

    /**
     * Configured from UI
     */
    protected JNLPLauncher jnlpLauncher = new JNLPLauncher();

    protected long launchTimeout = 120; //seconds

    protected String user = "jenkins";

    public DockerComputerJNLPLauncher() {
    }

    @DataBoundConstructor
    public DockerComputerJNLPLauncher(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public String getUser() {
        return user;
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
            throw new IllegalArgumentException("Docker JNLP Launcher accepts only DockerComputer");
        }
        Objects.requireNonNull(dockerComputer);

        final String containerId = dockerComputer.getContainerId();
        final String rootUrl = Jenkins.getActiveInstance().getRootUrl();
//        Objects.requireNonNull(rootUrl, "Jenkins root url is not specified!");
        if (isNull(rootUrl)) {
            throw new NullPointerException("Jenkins root url is not specified!");
        }
        final DockerCloud dockerCloud = dockerComputer.getCloud();
//        Objects.requireNonNull(dockerCloud, "Cloud not found for computer " + computer.getName());
        if (isNull(dockerCloud)) {
            throw new NullPointerException("Cloud not found for computer " + computer.getName());
        }
        final DockerClient connect = dockerCloud.getClient();
        final DockerSlave node = dockerComputer.getNode();
        if (isNull(node)) {
            throw new NullPointerException("Node can't be null");
        }
        final DockerSlaveTemplate dockerSlaveTemplate = node.getDockerSlaveTemplate();

        // exec jnlp connection in running container
        // TODO implement PID 1 replacement
        String startCmd =
                "cat << EOF > /tmp/config.sh.tmp && cd /tmp && mv config.sh.tmp config.sh\n" +
                        "JENKINS_URL=\"" + rootUrl + NL +
                        "JENKINS_USER=\"" + getUser() + NL +
                        "JENKINS_HOME=\"" + dockerSlaveTemplate.getRemoteFs() + NL +
                        "COMPUTER_URL=\"" + dockerComputer.getUrl() + NL +
                        "COMPUTER_SECRET=\"" + dockerComputer.getJnlpMac() + NL +
                        "EOF" + "\n";

        try {
            final ExecCreateCmdResponse response = connect.execCreateCmd(containerId)
                    .withTty()
                    .withAttachStdin(false)
                    .withAttachStderr()
                    .withAttachStdout()
                    .withCmd("/bin/bash", "-cxe", startCmd.replace("$", "\\$"))
                    .exec();

            LOG.info("Starting connection command for {}", containerId);
            logger.println("Starting connection command for " + containerId);

            try (ExecStartResultCallback exec = connect
                    .execStartCmd(response.getId())
                    .withDetach()
                    .withTty()
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

    @Override
    public ComputerLauncher getPreparedLauncher(String cloudId, DockerSlaveTemplate template,
                                                InspectContainerResponse containerInspectResponse) {
        final DockerComputerJNLPLauncher cloneJNLPlauncher = new DockerComputerJNLPLauncher(getJnlpLauncher());

        cloneJNLPlauncher.setLaunchTimeout(getLaunchTimeout());
        cloneJNLPlauncher.setUser(getUser());

        return cloneJNLPlauncher;
    }

    @Override
    public void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate, CreateContainerCmd createContainerCmd)
            throws IOException {
        try (InputStream instream = DockerComputerJNLPLauncher.class.getResourceAsStream("DockerComputerJNLPLauncher/init.sh")) {
            final String initCmd = IOUtils.toString(instream, Charsets.UTF_8);
            if (initCmd == null) {
                throw new IllegalStateException("Resource file 'init.sh' not found");
            }
//            createContainerCmd.withCmd("/bin/sh"); // nop
            // wait for params
            createContainerCmd.withCmd("/bin/bash",
                    "-cxe",
                    "cat << EOF >> /tmp/init.sh && chmod +x /tmp/init.sh && exec /tmp/init.sh\n" +
                            initCmd.replace("$", "\\$") + "\n" +
                            "EOF" + "\n"
            );
        }

//        final String homeDir = dockerSlaveTemplate.getRemoteFs();
//        if (isNotBlank(homeDir)) {
//            createContainerCmd.withWorkingDir(homeDir);
//        }
//
//        final String user = dockerSlaveTemplate.getUser();
//        if (isNotBlank(user)) {
//            createContainerCmd.withUser(user);
//        }

        createContainerCmd.withTty(true);
        createContainerCmd.withStdinOpen(true);
    }

    @Override
    public boolean waitUp(String cloudId, DockerSlaveTemplate dockerSlaveTemplate, InspectContainerResponse ir) {
        return super.waitUp(cloudId, dockerSlaveTemplate, ir);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DockerComputerJNLPLauncher that = (DockerComputerJNLPLauncher) o;

        return new EqualsBuilder()
                .append(launchTimeout, that.launchTimeout)
                .append(jnlpLauncher.tunnel, that.jnlpLauncher.tunnel) // no equals
                .append(jnlpLauncher.vmargs, that.jnlpLauncher.vmargs) // no equals
                .append(user, that.user)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(jnlpLauncher)
                .append(launchTimeout)
                .append(user)
                .toHashCode();
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public Class getJNLPLauncher() {
            return JNLPLauncher.class;
        }

        @Override
        public String getDisplayName() {
            return "Docker JNLP launcher";
        }
    }
}
