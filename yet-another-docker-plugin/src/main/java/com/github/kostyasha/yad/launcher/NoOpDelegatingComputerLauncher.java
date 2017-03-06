package com.github.kostyasha.yad.launcher;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Delegating ComputerLauncher without launch.
 *
 * @author Kanstantsin Shautsou
 */
public class NoOpDelegatingComputerLauncher extends DelegatingComputerLauncher {
    public NoOpDelegatingComputerLauncher(ComputerLauncher core) {
        super(core);
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        // noop
    }

    @Extension
    public static final class DescriptorImpl extends DelegatingComputerLauncher.DescriptorImpl {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "No Launching Delegating Computer Launcher";
        }
    }
}
