package com.github.kostyasha.yad.listener;

import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.DockerComputerSingle;
import com.github.kostyasha.yad.DockerSlave;
import com.github.kostyasha.yad.DockerSlaveSingle;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.tasks.BuildWrapper;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.ParseException;

import static com.github.kostyasha.yad.utils.ContainerRecordUtils.createRecordFor;
import static java.util.Objects.isNull;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerRunListener extends RunListener<Run<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRunListener.class);


//    @Override
//    public void onInitialize(Run<?, ?> run) {
//        final DockerLabelAssignmentAction assignmentAction = run.getAction(DockerLabelAssignmentAction.class);
//        if (isNull(assignmentAction)) {
//            return;
//        }
//        try {
//            final StreamBuildListener listener = createBuildListener(run);
//            final DockerClient client = assignmentAction.getConnector().getClient();
//            final Node node = Jenkins.getInstance().getNode(assignmentAction.getAssignedLabel());
//            final DockerSlaveSingle dockerSlave = (DockerSlaveSingle) node;
//            final DockerComputerSingle computer = (DockerComputerSingle) dockerSlave.toComputer();
//
//            computer.getLauncher().launch(computer, (TaskListener) listener);
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//        }
//
//    }

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        final DockerLabelAssignmentAction assignmentAction = run.getAction(DockerLabelAssignmentAction.class);
        if (isNull(assignmentAction)) {
            return;
        }
        try {
            final Node node = Jenkins.getInstance().getNode(assignmentAction.getAssignedLabel());
            final DockerSlaveSingle dockerSlave = (DockerSlaveSingle) node;
            final DockerComputerSingle computer = (DockerComputerSingle) dockerSlave.toComputer();
            computer.setRun(run);
            computer.setListener(listener);
            ((DelegatingComputerLauncher) computer.getLauncher()).getLauncher().launch(computer, listener);
        } catch (IOException | InterruptedException e) {
            LOG.error("fd", e);
        }

        attachFacet(run, listener);
    }

    private void attachFacet(Run<?, ?> run, TaskListener listener) {
        final Executor executor = run.getExecutor();
        if (executor == null) {
            return;
        }

        final Computer owner = executor.getOwner();
        DockerComputer dockerComputer;
        if (owner instanceof DockerComputer) {
            dockerComputer = (DockerComputer) owner;
        } else {
            return;
        }

        try {
            DockerFingerprints.addRunFacet(
                    createRecordFor(dockerComputer),
                    run
            );
        } catch (IOException | ParseException e) {
            listener.error("Can't add Docker fingerprint to run.");
            LOG.error("Can't add fingerprint to run {}", run, e);
        }
    }

    private StreamBuildListener createBuildListener(@Nonnull Run run) throws IOException, InterruptedException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        OutputStream logger = new FileOutputStream(run.getLogFile(), true);
        Job job = run.getParent();

        // Global log filters
        for (ConsoleLogFilter filter : ConsoleLogFilter.all()) {
            logger = filter.decorateLogger(run, logger);
        }

        // Project specific log filters
        if (job instanceof BuildableItemWithBuildWrappers && run instanceof AbstractBuild) {
            BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) job;
            for (BuildWrapper bw : biwbw.getBuildWrappersList()) {
                logger = bw.decorateLogger((AbstractBuild) run, logger);
            }
        }

        return new StreamBuildListener(logger, run.getCharset());
    }
}
