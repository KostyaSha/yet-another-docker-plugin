package com.github.kostyasha.yad;

import com.github.kostyasha.yad.connector.YADockerConnector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

import static java.util.Objects.isNull;
import static org.jenkinsci.plugins.cloudstats.CloudStatistics.ProvisioningListener.get;

/**
 * Wrapper that starts node and allows body execute anything in created {@link #getSlaveName()} slave.
 * Body may assign tasks by {@link hudson.model.Label} using Actions or do anything directly on it.
 * By default {@link com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy} is used and terminates
 * slave after first execution, set other or override it with custom logic.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerSimpleBuildWrapper extends SimpleBuildWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(DockerSimpleBuildWrapper.class);

    private YADockerConnector connector; // default connector
    private DockerSlaveConfig config;
    private String slaveName;

    @DataBoundConstructor
    public DockerSimpleBuildWrapper(@Nonnull YADockerConnector connector, @Nonnull DockerSlaveConfig config) {
        this.connector = connector;
        this.config = config;
    }

    public YADockerConnector getConnector() {
        return connector;
    }

    public DockerSlaveConfig getConfig() {
        return config;
    }

    @CheckForNull
    public String getSlaveName() {
        return slaveName;
    }

    protected void setSlaveName(String slaveName) {
        this.slaveName = slaveName;
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @Override
    public void setUp(Context context,
                      Run<?, ?> run,
                      FilePath workspace,
                      Launcher launcher,
                      TaskListener listener,
                      EnvVars initialEnvironment) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println("Trying to setup env...");

        final ProvisioningActivity.Id activityId = new ProvisioningActivity.Id(
                run.getDisplayName(),
                getConfig().getDockerContainerLifecycle().getImage()
        );

        try {
            get().onStarted(activityId);
            final String futureName = "yadp" + activityId.getFingerprint();

            final DockerSlaveSingle slave = new DockerSlaveSingle(futureName,
                    "Slave for " + run.getFullDisplayName(),
                    getConfig(),
                    getConnector(),
                    activityId
            );

            Jenkins.getInstance().addNode(slave);
            final Node futureNode = Jenkins.getInstance().getNode(futureName);
            if (!(futureNode instanceof DockerSlaveSingle)) {
                logger.println("Can't get node" + futureName);
                throw new IllegalStateException("Can't get Node " + futureName);
            }

            final DockerSlaveSingle node = (DockerSlaveSingle) futureNode;
            try {
                final Computer toComputer = node.toComputer();
                if (!(toComputer instanceof DockerComputerSingle)) {
                    logger.println("Can't get computer for " + node.getNodeName());
                    throw new IllegalStateException("Can't get computer for " + node.getNodeName());
                }

                final DockerComputerSingle computer = (DockerComputerSingle) toComputer;
                computer.setRun(run);
                computer.setListener(listener);
                node.setListener(listener);
                logger.println("Getting launcher...");
                ((DelegatingComputerLauncher) computer.getLauncher()).getLauncher().launch(computer, listener);
            } catch (Throwable e) {
                logger.println("Failed to provision");
                e.printStackTrace(logger);
                LOG.error("fd", e);
                CloudStatistics.ProvisioningListener.get().onFailure(node.getId(), e);
                //terminate will do container cleanups and remove node from jenkins silently
                slave.terminate();
                throw e;
            }

            setSlaveName(futureName);
            context.setDisposer(new DisposerImpl(futureName));

        } catch (Descriptor.FormException | IOException e) {
            logger.println("Failed to run slave");
            e.printStackTrace(logger);
            get().onFailure(activityId, e);
            throw new IOException("failed to run slave", e);
        }
    }

    /**
     * Terminates slave for specified slaveName. Works only with {@link DockerSlaveSingle}.
     */
    public static class DisposerImpl extends Disposer {
        private static final long serialVersionUID = 1;
        private final String slaveName;

        public DisposerImpl(@Nonnull final String slaveName) {
            this.slaveName = slaveName;
        }

        @Override
        public void tearDown(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException {
            PrintStream logger = listener.getLogger();

            LOG.info("Shutting down slave");
            logger.println("Shutting down slave '" + slaveName + "'.");
            final Node slaveNode = Jenkins.getInstance().getNode(slaveName);
            if (isNull(slaveNode)) {
                throw new IllegalStateException("Can't get node " + slaveName);
            }
            final DockerSlaveSingle node = (DockerSlaveSingle) slaveNode;
            try {
                node.terminate();
            } catch (Throwable e) {
                logger.println("Can't terminate node");
                e.printStackTrace(logger);
                LOG.error("Can't terminate node", e);
                CloudStatistics.ProvisioningListener.get().onFailure(node.getId(), e);
            }

        }
    }

    @Symbol("yadockerWrapper")
//    @Extension hide from users because it used programmatically
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }
}
