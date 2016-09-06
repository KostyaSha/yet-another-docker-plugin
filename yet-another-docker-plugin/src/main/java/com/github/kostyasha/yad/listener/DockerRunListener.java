package com.github.kostyasha.yad.listener;

import com.github.kostyasha.yad.DockerAction;
import com.github.kostyasha.yad.DockerComputer;
import com.github.kostyasha.yad.DockerSlave;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.docker.commons.fingerprint.DockerFingerprints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;

import static com.github.kostyasha.yad.utils.ContainerRecordUtils.createRecordFor;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerRunListener extends RunListener<Run<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRunListener.class);

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
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
            LOG.error("Can't add fingerprint to run {}", run, e);
        }

        DockerAction dockerAction = new DockerAction();
        DockerSlave dockerSlave = dockerComputer.getNode();
        if (dockerSlave != null) {
            dockerAction.setRemoteFSMapping(dockerSlave.getDockerSlaveTemplate().getRemoteFsMapping());
        }
        run.addAction(dockerAction);
    }
}
