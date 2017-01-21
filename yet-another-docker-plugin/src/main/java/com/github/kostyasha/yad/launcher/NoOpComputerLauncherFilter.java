package com.github.kostyasha.yad.launcher;

import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerLauncherFilter;

/**
 * @author Kanstantsin Shautsou
 */
public class NoOpComputerLauncherFilter extends ComputerLauncherFilter {
    public NoOpComputerLauncherFilter(ComputerLauncher core) {
        super(core);
    }

}
