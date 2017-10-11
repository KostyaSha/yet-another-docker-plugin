package com.github.kostyasha.yad;

import com.github.kostyasha.yad.launcher.DockerComputerIOLauncher;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;

import java.io.IOException;

@Extension
public class DockerComputerListener extends ComputerListener {

    @Override
    public void onLaunchFailure(Computer comp, TaskListener taskListener) throws IOException, InterruptedException {
        if (comp instanceof DockerComputer) {
            DockerComputer dockerComputer = (DockerComputer) comp;
            if (dockerComputer.getLauncher() instanceof DockerComputerIOLauncher) {
                taskListener.error("Failed to launch");
            }
        }
    }
}
