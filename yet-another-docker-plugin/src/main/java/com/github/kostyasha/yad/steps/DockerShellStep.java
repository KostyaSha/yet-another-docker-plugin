package com.github.kostyasha.yad.steps;

import com.github.kostyasha.yad.DockerContainerLifecycle;
import com.github.kostyasha.yad.commons.DockerCreateContainer;
import com.github.kostyasha.yad.connector.YADockerConnector;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.StartContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.AttachContainerResultCallback;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerShellStep extends Builder implements SimpleBuildStep {
    private static final Logger LOG = LoggerFactory.getLogger(DockerShellStep.class);

    private YADockerConnector connector = null;
    private DockerContainerLifecycle containerLifecycle = new DockerContainerLifecycle();

    @DataBoundConstructor
    public DockerShellStep() {
    }

    public YADockerConnector getConnector() {
        return connector;
    }

    @DataBoundSetter
    public void setConnector(YADockerConnector connector) {
        this.connector = connector;
    }

    public DockerContainerLifecycle getContainerLifecycle() {
        return containerLifecycle;
    }

    @DataBoundSetter
    public void setContainerLifecycle(DockerContainerLifecycle containerLifecycle) {
        this.containerLifecycle = containerLifecycle;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream llog = listener.getLogger();
        final String imageId = containerLifecycle.getImage();

        try (DockerClient client = connector.getClient()) {
            //pull image
            llog.println("Pulling image " + imageId + "...");
            containerLifecycle.getPullImage().exec(client, imageId, listener);

            llog.println("Trying to create container for " + imageId);
            LOG.info("Trying to create container for {}", imageId);
            final DockerCreateContainer createContainer = containerLifecycle.getCreateContainer();
            CreateContainerCmd containerConfig = client.createContainerCmd(imageId);
            // template specific options
            createContainer.fillContainerConfig(containerConfig);

            // mark specific options
            appendContainerConfig(containerConfig, connector);

            containerConfig.withAttachStdout(true)
                    .withAttachStderr(true)
                    .withStdinOpen(true)
                    .withTty(true);

            // create
            CreateContainerResponse createResp = containerConfig.exec();
            String cId = createResp.getId();

            llog.println("Created container " + cId + ", for " + run.getDisplayName());
            LOG.debug("Created container {}, for {}", cId, run.getDisplayName());

            try {
                // start
                StartContainerCmd startCommand = client.startContainerCmd(cId);
                startCommand.exec();
                llog.println("Started container " + cId);
                LOG.debug("Start container {}, for {}", cId, run.getDisplayName());

                try (AttachContainerResultCallback callback = new AttachContainerResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        super.onNext(frame);
                        llog.print(frame.toString());
                    }
                }) {
                    client.attachContainerCmd(cId)
                            .withStdErr(true)
                            .withStdOut(true)
                            .withFollowStream(true)
                            .exec(callback)
                            .awaitCompletion();
                }
            } catch (Exception ex) {
                llog.println("failed to start cmd");
                throw ex;
            } finally {
                llog.println("Removing container " + cId);
                LOG.debug("Removing container {}, for {}", cId, run.getDisplayName());
                try {
                    containerLifecycle.getRemoveContainer().exec(client, cId);
                    llog.println("Container is removed.");
                } catch (NotFoundException ex) {
                    llog.println("Container is already removed.");
                } catch (Exception ex) {
                    //ignore ex
                }
            }
        } catch (Exception ex) {
            LOG.error("", ex);
            llog.println("failed to start cmd");
            throw new IOException(ex);
        }
    }

    /**
     * Append some tags to identify who create this container.
     */
    protected void appendContainerConfig(CreateContainerCmd containerConfig, YADockerConnector connector) {
        // replace shell
        // add tags

    }

//    @Override
//    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
//                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
//        PrintStream llog = listener.getLogger();
//        final DockerShellStepFileCallable comboCallable = newDockerShellStepFileCallableBuilder()
//                .withTaskListener(listener)
//                .withRun(run)
//                .withConnector(connector)
//                .withShellScript(shellScript)
//                .withDockerImage(dockerImage)
//                .build();
//        try {
//            llog.println("Executing remote combo builder...");
//            if (BooleanUtils.isFalse(
//                    workspace.act(
//                            comboCallable
//                    ))) {
//                throw new AbortException("Something failed");
//            }
//        } catch (Exception ex) {
//            LOG.error("Can't build image", ex);
//            throw ex;
//        }
//    }

    @Extension
    @Symbol("dockerShell")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
