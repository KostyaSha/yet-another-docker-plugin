package com.github.kostyasha.yad.listener;

import com.github.kostyasha.yad.DockerComputerSingle;
import com.github.kostyasha.yad.DockerSlaveSingle;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.slaves.DelegatingComputerLauncher;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.github.kostyasha.yad.utils.ContainerRecordUtils.attachFacet;
import static java.util.Objects.isNull;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerRunListener extends RunListener<Run<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRunListener.class);

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        final DockerLabelAssignmentAction assignmentAction = run.getAction(DockerLabelAssignmentAction.class);
        if (isNull(assignmentAction)) {
            return;
        }

        final DockerSlaveSingle node = (DockerSlaveSingle) Jenkins.getInstance().getNode(assignmentAction.getAssignedLabel());
        try {
            final DockerComputerSingle computer = (DockerComputerSingle) node.toComputer();
            computer.setRun(run);
            computer.setListener(listener);
            ((DelegatingComputerLauncher) computer.getLauncher()).getLauncher().launch(computer, listener);
        } catch (IOException | InterruptedException e) {
            LOG.error("fd", e);
            CloudStatistics.ProvisioningListener.get().onFailure(node.getId(), e);
        }

        attachFacet(run, listener);
    }


}
