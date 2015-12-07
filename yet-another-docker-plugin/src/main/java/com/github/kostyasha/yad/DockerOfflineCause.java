package com.github.kostyasha.yad;

import hudson.slaves.OfflineCause;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerOfflineCause extends OfflineCause {
    @Override
    public String toString() {
        return "Shutting down Docker";
    }
}
