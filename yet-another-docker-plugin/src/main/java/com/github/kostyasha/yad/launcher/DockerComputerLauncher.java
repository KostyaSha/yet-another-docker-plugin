package com.github.kostyasha.yad.launcher;


import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.google.common.annotations.Beta;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.BooleanUtils.isFalse;


/**
 * Crappy wrapper... On one hand we need store UI configuration,
 * on other have valid configured launcher that different for host/port/etc for any slave.
 * <p>
 * like {@link DelegatingComputerLauncher}
 */
@Beta
public abstract class DockerComputerLauncher extends DelegatingComputerLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(DockerComputerLauncher.class);

    protected DockerComputerLauncher(ComputerLauncher launcher) {
        super(launcher);
    }

    /**
     * Called after container was created. DockerSlave is not created atm.
     */
    public void afterContainerCreate(DockerClient client, String containerId) throws IOException {
    }

    /**
     * Return valid configured launcher that will be used for launching slave
     */
    public abstract DockerComputerLauncher getPreparedLauncher(String cloudId,
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
     *
     * @return false - slave is not ready.
     */
    public boolean waitUp(String cloudId, DockerSlaveTemplate dockerSlaveTemplate,
                          InspectContainerResponse containerInspect) {
        if (isFalse(containerInspect.getState().getRunning())) {
            LOG.error("Container {} is not running!", containerInspect.getId());
            return false;
        }

        return true;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }
}
