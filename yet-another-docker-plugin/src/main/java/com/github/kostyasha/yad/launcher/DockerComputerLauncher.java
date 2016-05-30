package com.github.kostyasha.yad.launcher;


import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.google.common.annotations.Beta;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;

import static com.github.kostyasha.yad.docker_java.org.apache.commons.lang.BooleanUtils.isFalse;


/**
 * Crappy wrapper... On one hand we need store UI configuration,
 * on other have valid configured launcher that different for host/port/etc for any slave.
 * <p>
 * like {@link DelegatingComputerLauncher}
 */
@Beta
public abstract class DockerComputerLauncher extends ComputerLauncher {

    protected ComputerLauncher launcher;

    /**
     * Return valid configured launcher that will be used for launching slave
     */
    public abstract ComputerLauncher getPreparedLauncher(String cloudId,
                                                         DockerSlaveTemplate dockerSlaveTemplate,
                                                         InspectContainerResponse ir);

    /**
     * Contribute container parameters needed for launcher.
     * i.e. port for exposing, command to run, etc.
     */
    public abstract void appendContainerConfig(DockerSlaveTemplate dockerSlaveTemplate,
                                               CreateContainerCmd createContainerCmd) throws IOException;

    /**
     * Wait until slave is up and ready for connection.
     */
    public boolean waitUp(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                          InspectContainerResponse containerInspect) {
        if (isFalse(containerInspect.getState().getRunning())) {
            throw new IllegalStateException("Container '" + containerInspect.getId() + "' is not running!");
        }

        return true;
    }

    public ComputerLauncher getLauncher() {
        if (launcher == null) {
            throw new IllegalStateException("Launcher must not be null");
        }

        return launcher;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        getLauncher().launch(computer, listener);
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        getLauncher().afterDisconnect(computer, listener);
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        getLauncher().beforeDisconnect(computer, listener);
    }
}
