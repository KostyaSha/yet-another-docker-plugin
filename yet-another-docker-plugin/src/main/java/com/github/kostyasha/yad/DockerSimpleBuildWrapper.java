package com.github.kostyasha.yad;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
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

import static org.jenkinsci.plugins.cloudstats.CloudStatistics.ProvisioningListener.get;

/**
 * Wrapper that starts node and allows body execute anything in created {@link #getSlaveName()} slave.
 * Body may assign tasks by {@link hudson.model.Label} using Actions or do anything directly on it.
 * By defauld {@link com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy} is used and terminates
 * slave after first execution, set other or override it with custom logic.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerSimpleBuildWrapper extends SimpleBuildWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(DockerSimpleBuildWrapper.class);

    private DockerConnector connector;
    private DockerSlaveConfig config;
    private String slaveName;

    @DataBoundConstructor
    public DockerSimpleBuildWrapper(@Nonnull DockerConnector connector, @Nonnull DockerSlaveConfig config) {
        this.connector = connector;
        this.config = config;
    }

    public DockerConnector getConnector() {
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

    @Override
    public void setUp(Context context,
                      Run<?, ?> run,
                      FilePath workspace,
                      Launcher launcher,
                      TaskListener listener,
                      EnvVars initialEnvironment) throws IOException, InterruptedException {

        final ProvisioningActivity.Id activityId = new ProvisioningActivity.Id(
                run.getDisplayName(),
                getConfig().getDockerContainerLifecycle().getImage()
        );

        try {
            get().onStarted(activityId);
            final String futureName = "yadp" + Integer.toString(activityId.getFingerprint());

            final DockerSlaveSingle slave = new DockerSlaveSingle(futureName,
                    "Slave for " + run.getFullDisplayName(),
                    getConfig(),
                    getConnector(),
                    activityId
            );

            Jenkins.getInstance().addNode(slave);

            final DockerSlaveSingle node = (DockerSlaveSingle) Jenkins.getInstance().getNode(futureName);
            try {
                final DockerComputerSingle computer = (DockerComputerSingle) node.toComputer();
                computer.setRun(run);
                computer.setListener(listener);
                node.setListener(listener);
                listener.getLogger().println("Getting launcher...");
                ((DelegatingComputerLauncher) computer.getLauncher()).getLauncher().launch(computer, listener);
            } catch (IOException | InterruptedException e) {
                LOG.error("fd", e);
                CloudStatistics.ProvisioningListener.get().onFailure(node.getId(), e);
            }
            setSlaveName(futureName);
            context.setDisposer(new DisposerImpl(futureName));

        } catch (Descriptor.FormException | IOException e) {
            get().onFailure(activityId, e);
            throw new AbortException("failed to run slave");
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
            LOG.info("Shutting down slave");
            listener.getLogger().println("Shutting down slave '" + slaveName + "'.");

            final DockerSlaveSingle node = (DockerSlaveSingle) Jenkins.getInstance().getNode(slaveName);
            try {
                node.terminate();
            } catch (IOException | InterruptedException e) {
                LOG.error("Can't terminate node", e);
                CloudStatistics.ProvisioningListener.get().onFailure(node.getId(), e);
            }

        }
    }

    @Symbol("dockerCustomWrapper")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }
}
