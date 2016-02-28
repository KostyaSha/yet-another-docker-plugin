package com.github.kostyasha.yad.other;

import hudson.remoting.Launcher;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;

public final class SlaveVersion extends MasterToSlaveCallable<String, IOException> {
    public String call() throws IOException {
        return Launcher.VERSION;
    }
}
